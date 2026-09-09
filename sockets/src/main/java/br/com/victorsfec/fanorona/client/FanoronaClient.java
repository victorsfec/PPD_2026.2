package br.com.victorsfec.fanorona.client;

import br.com.victorsfec.fanorona.shared.Protocol;
import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Transporte assíncrono. Nenhuma operação de rede bloqueante roda na EDT. */
public final class FanoronaClient {
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private volatile Socket socket;
    private volatile boolean closed;
    private BufferedWriter out;
    private GameFrame frame;
    void connect(String host, int port, String name) {
        frame = new GameFrame(this); frame.setVisible(true);
        // A conexão ocorre fora da thread gráfica para a janela continuar respondendo.
        sender.execute(() -> {
            try {
                Socket connection = new Socket(); socket = connection;
                if (closed) { connection.close(); return; }
                connection.connect(new InetSocketAddress(host, port), 5000);
                connection.setTcpNoDelay(true);
                out = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
                // HELLO identifica o jogador antes de qualquer comando de jogo.
                write("HELLO|" + Protocol.text(name));
                new Thread(() -> listen(connection), "recepcao-servidor").start();
            } catch (IOException e) { failed(e.getMessage()); }
        });
    }
    private void listen(Socket connection) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String message;
            while ((message = Protocol.readLine(in)) != null) {
                final String received = message;
                    // A EDT é a thread do Swing: somente ela deve atualizar a interface.
                    SwingUtilities.invokeLater(() -> frame.receive(received));
            }
            if (!closed) failed("O servidor encerrou a conexão.");
        } catch (IOException e) { if (!closed) failed(e.getMessage()); }
    }
    // O executor de uma única thread mantém a ordem de envio dos comandos.
    void send(String line) {
        if (closed) return;
        try { sender.execute(() -> { try { write(line); } catch (IOException e) { failed(e.getMessage()); } }); }
        catch (RejectedExecutionException ignored) { }
    }
    private void write(String line) throws IOException {
        if (out == null) throw new IOException("Conexão ainda não estabelecida.");
        out.write(line); out.newLine(); out.flush();
    }
    private void failed(String reason) {
        close(); SwingUtilities.invokeLater(() -> frame.disconnected(reason));
    }
    void close() {
        closed = true;
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        sender.shutdownNow();
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JTextField name = new JTextField("Jogador"), host = new JTextField("localhost"), port = new JTextField("12345");
            Object[] fields = {"Nome (até 30 caracteres)", name, "IP do servidor", host, "Porta TCP", port};
            while (JOptionPane.showConfirmDialog(null, fields, "Fanorona | Conectar", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                try {
                    int number = Integer.parseInt(port.getText().strip());
                    if (number < 1 || number > 65535 || host.getText().isBlank()) throw new IllegalArgumentException("IP ou porta inválidos.");
                    String validName = Protocol.userText(Protocol.text(name.getText()), 30);
                    new FanoronaClient().connect(host.getText().strip(), number, validName); return;
                } catch (IllegalArgumentException e) { JOptionPane.showMessageDialog(null, e.getMessage()); }
            }
        });
    }
}
