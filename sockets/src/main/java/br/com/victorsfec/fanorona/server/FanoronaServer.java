package br.com.victorsfec.fanorona.server;

import br.com.victorsfec.fanorona.shared.Protocol;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;

/** Aceita conexões sem aguardar o nome; um cliente lento não bloqueia o accept. */
public final class FanoronaServer implements AutoCloseable {
    private final ServerSocket listener;
    private final Set<ClientHandler> clients = new HashSet<>();
    private final Deque<ClientHandler> waiting = new ArrayDeque<>();
    private volatile boolean closed;
    // ServerSocket abre a porta TCP em que os clientes vão se conectar.
    public FanoronaServer(int port) throws IOException { listener = new ServerSocket(port); }
    public int port() { return listener.getLocalPort(); }
    public void run() throws IOException {
        while (!closed) {
            try {
                // accept espera uma conexão e devolve um Socket exclusivo para esse cliente.
                Socket socket = listener.accept();
                synchronized (this) {
                    if (closed || clients.size() >= 100) { socket.close(); continue; }
                    ClientHandler client = new ClientHandler(this, socket);
                    // Cada cliente recebe uma thread; o servidor volta a aceitar conexões.
                    clients.add(client); client.start();
                }
            } catch (SocketException e) { if (!closed) throw e; }
        }
    }
    synchronized void ready(ClientHandler client) {
        if (closed || !clients.contains(client)) return;
        waiting.add(client);
        client.send("INFO|" + Protocol.text("Aguardando oponente. O primeiro conectado será P1 e iniciará."));
        // Cada dupla sai da fila de espera e recebe uma partida independente.
        if (waiting.size() >= 2) {
            ClientHandler first = waiting.remove(), second = waiting.remove();
            GameSession session = new GameSession(first, second);
            session.start();
        }
    }
    void remove(ClientHandler client) {
        synchronized (this) { clients.remove(client); waiting.remove(client); }
        // Não segurar o monitor do lobby ao adquirir o monitor da partida.
        GameSession session = client.session;
        if (session != null) session.disconnect(client);
    }
    @Override public void close() throws IOException {
        List<ClientHandler> copy;
        synchronized (this) { closed = true; copy = new ArrayList<>(clients); waiting.clear(); }
        // Fechar a porta desbloqueia accept; depois, encerramos os sockets dos jogadores.
        try { listener.close(); }
        finally { for (ClientHandler c : copy) c.shutdown(); }
    }
    public static void main(String[] args) throws Exception {
        boolean console = GraphicsEnvironment.isHeadless();
        Integer requestedPort = null;
        for (String arg : args) {
            if (arg.equals("--console")) console = true;
            else if (requestedPort == null) requestedPort = Integer.parseInt(arg);
            else throw new IllegalArgumentException("Uso: java -jar servidor.jar [porta] [--console]");
        }
        int port = requestedPort == null ? Protocol.PORT : requestedPort;
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Porta deve estar entre 1 e 65535");
        if (!console) {
            SwingUtilities.invokeLater(() -> new ServerFrame(port).setVisible(true));
            return;
        }
        FanoronaServer server = new FanoronaServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.close(); } catch (IOException ignored) { } }));
        System.out.println("Fanorona TCP na porta " + server.port() + ". Ctrl+C para encerrar.");
        server.run();
    }
}
