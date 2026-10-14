package br.com.victorsfec.fanorona.server;
import br.com.victorsfec.fanorona.shared.*;
import java.io.IOException;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;

/** Lobby RMI. Registro e serviço compartilham uma porta fixa para uso em rede. */
public final class FanoronaServerRMI implements FanoronaServiceRMI, AutoCloseable {
    private final Registry registry;
    private final int port;
    private final Map<String, ClientHandlerRMI> clients = new HashMap<>();
    private final Deque<ClientHandlerRMI> waiting = new ArrayDeque<>();
    private final ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed;
    public FanoronaServerRMI(int port) throws RemoteException {
        this.port = port;
        registry = LocateRegistry.createRegistry(port);
        try { registry.rebind(NAME, UnicastRemoteObject.exportObject(this, port)); }
        catch (RemoteException e) {
            UnicastRemoteObject.unexportObject(registry, true);
            try { UnicastRemoteObject.unexportObject(this, true); } catch (NoSuchObjectException ignored) { }
            maintenance.shutdownNow(); throw e;
        }
        maintenance.scheduleAtFixedRate(() -> {
            List<ClientHandlerRMI> expired;
            synchronized (this) { expired = clients.values().stream().filter(c -> System.nanoTime()-c.lastSeen > TimeUnit.SECONDS.toNanos(15)).toList(); }
            expired.forEach(this::expire);
        }, 1, 1, TimeUnit.SECONDS);
    }
    public int port() { return port; }
    public synchronized void run() throws IOException {
        while (!closed) try { wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
    }
    @Override public synchronized String connect(String name) throws RemoteException {
        if (closed || clients.size() >= 100) throw new RemoteException("Servidor indisponível ou lotado.");
        String valid = ProtocolRMI.userText(ProtocolRMI.text(name == null ? "" : name), 30);
        String token = UUID.randomUUID().toString();
        ClientHandlerRMI c = new ClientHandlerRMI(this, valid);
        clients.put(token, c); waiting.add(c);
        c.send("INFO|" + ProtocolRMI.text("Aguardando oponente. O primeiro conectado será P1 e iniciará."));
        if (waiting.size() >= 2) new GameSessionRMI(waiting.remove(), waiting.remove()).start();
        return token;
    }
    private synchronized ClientHandlerRMI client(String token) throws RemoteException {
        ClientHandlerRMI c = clients.get(token);
        if (closed || c == null) throw new RemoteException("Sessão encerrada ou expirada.");
        c.lastSeen = System.nanoTime(); return c;
    }
    @Override public List<String> poll(String token) throws RemoteException { return client(token).drain(); }
    private void action(String token, String command) throws RemoteException {
        ClientHandlerRMI c = client(token);
        GameSessionRMI session = c.session;
        if (session == null) c.send("ERROR|" + ProtocolRMI.text("Aguarde outro jogador.")); else session.process(command, c);
    }
    @Override public void move(String t, long r, int f, int d, String m) throws RemoteException {
        if (!Set.of("A", "W", "P").contains(m == null ? "" : m)) { client(t).send("ERROR|" + ProtocolRMI.text("Modo inválido.")); return; }
        action(t, "MOVE|" + r + "|" + f + "|" + d + "|" + m);
    }
    @Override public void endTurn(String t, long r) throws RemoteException { action(t, "END_TURN|" + r); }
    @Override public void chat(String t, String s) throws RemoteException { action(t, "CHAT|" + ProtocolRMI.text(s == null ? "" : s)); }
    @Override public void forfeit(String t) throws RemoteException { action(t, "FORFEIT"); }
    @Override public void draw(String t) throws RemoteException { action(t, "DRAW"); }
    @Override public void restart(String t, long r) throws RemoteException { action(t, "RESTART|" + r); }
    @Override public void replyRestart(String t, long o, boolean a) throws RemoteException { action(t, "RESTART_REPLY|" + o + "|" + (a ? "ACCEPT" : "DECLINE")); }
    @Override public void disconnect(String token) {
        ClientHandlerRMI c;
        synchronized (this) { c = clients.remove(token); if (c != null) waiting.remove(c); }
        if (c != null && c.session != null) c.session.disconnect(c);
    }
    void expire(ClientHandlerRMI c) {
        // Adia desconexões para não adquirir outro monitor durante uma publicação.
        try { maintenance.execute(() -> {
            String token;
            synchronized (this) { token = clients.entrySet().stream().filter(e -> e.getValue() == c).map(Map.Entry::getKey).findFirst().orElse(null); }
            if (token != null) disconnect(token);
        }); } catch (RejectedExecutionException ignored) { }
    }
    @Override public void close() throws IOException {
        synchronized (this) { if (closed) return; closed = true; clients.clear(); waiting.clear(); notifyAll(); }
        maintenance.shutdownNow();
        try { registry.unbind(NAME); } catch (NotBoundException | RemoteException ignored) { }
        UnicastRemoteObject.unexportObject(this, true); UnicastRemoteObject.unexportObject(registry, true);
    }
    public static void main(String[] args) throws Exception {
        boolean console = GraphicsEnvironment.isHeadless(); int port = ProtocolRMI.PORT;
        for (String arg : args) { if (arg.equals("--console")) console = true; else port = Integer.parseInt(arg); }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Porta deve estar entre 1 e 65535");
        if (!console) { final int selected = port; SwingUtilities.invokeLater(() -> new ServerFrameRMI(selected).setVisible(true)); return; }
        try (FanoronaServerRMI server = new FanoronaServerRMI(port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.close(); } catch (IOException ignored) { } }));
            System.out.println("Fanorona RMI na porta " + port + ". Ctrl+C para encerrar."); server.run();
        }
    }
}
