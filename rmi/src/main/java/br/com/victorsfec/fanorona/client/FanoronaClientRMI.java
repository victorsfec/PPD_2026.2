package br.com.victorsfec.fanorona.client;
import br.com.victorsfec.fanorona.shared.*;
import javax.swing.*;
import java.rmi.registry.LocateRegistry;
import java.util.concurrent.*;

/** Operações e consulta de eventos RMI fora da EDT; a ordem das ações é preservada. */
public final class FanoronaClientRMI {
    private final ScheduledExecutorService network = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed;
    private FanoronaServiceRMI service;
    private String token;
    private GameFrameRMI frame;
    void connect(String host, int port, String name) {
        frame = new GameFrameRMI(this); frame.setVisible(true);
        network.execute(() -> {
            try {
                service = (FanoronaServiceRMI) LocateRegistry.getRegistry(host, port).lookup(FanoronaServiceRMI.NAME);
                token = service.connect(name);
                if (closed) { service.disconnect(token); return; }
                network.scheduleWithFixedDelay(() -> {
                    if (closed) return;
                    try { for (String event : service.poll(token)) SwingUtilities.invokeLater(() -> frame.receive(event)); }
                    catch (Exception e) { failed(e.getMessage()); }
                }, 0, 200, TimeUnit.MILLISECONDS);
            } catch (Exception e) { failed(e.getMessage()); }
        });
    }
    void send(String line) {
        if (closed) return;
        try { network.execute(() -> { try { invoke(service, token, line); } catch (Exception e) { failed(e.getMessage()); } }); }
        catch (RejectedExecutionException ignored) { }
    }
    /** Adaptador local da interface existente para métodos remotos tipados. */
    public static void invoke(FanoronaServiceRMI service, String token, String line) throws java.rmi.RemoteException {
        String[] p = ProtocolRMI.parts(line);
        switch (p[0]) {
            case "MOVE" -> service.move(token, Long.parseLong(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4]);
            case "END_TURN" -> service.endTurn(token, Long.parseLong(p[1]));
            case "CHAT" -> service.chat(token, ProtocolRMI.untext(p[1]));
            case "FORFEIT" -> service.forfeit(token);
            case "DRAW" -> service.draw(token);
            case "RESTART" -> service.restart(token, Long.parseLong(p[1]));
            case "RESTART_REPLY" -> service.replyRestart(token, Long.parseLong(p[1]), p[2].equals("ACCEPT"));
            default -> throw new IllegalArgumentException("Operação desconhecida.");
        }
    }
    private void failed(String reason) { if (closed) return; close(); SwingUtilities.invokeLater(() -> frame.disconnected(reason)); }
    void close() {
        if (closed) return; closed = true;
        // Também deixa a conexão em andamento terminar e liberar seu token.
        try { network.execute(() -> { try { if (service != null && token != null) service.disconnect(token); } catch (Exception ignored) { } }); }
        catch (RejectedExecutionException ignored) { }
        network.shutdown();
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JTextField name = new JTextField("Jogador"), host = new JTextField("localhost"), port = new JTextField(Integer.toString(ProtocolRMI.PORT));
            Object[] fields = {"Nome (até 30 caracteres)", name, "IP do servidor", host, "Porta RMI", port};
            while (JOptionPane.showConfirmDialog(null, fields, "Fanorona RMI | Conectar", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                try {
                    int number = Integer.parseInt(port.getText().strip());
                    if (number < 1 || number > 65535 || host.getText().isBlank()) throw new IllegalArgumentException("IP ou porta inválidos.");
                    String valid = ProtocolRMI.userText(ProtocolRMI.text(name.getText()), 30);
                    new FanoronaClientRMI().connect(host.getText().strip(), number, valid); return;
                } catch (IllegalArgumentException e) { JOptionPane.showMessageDialog(null, e.getMessage()); }
            }
        });
    }
}
