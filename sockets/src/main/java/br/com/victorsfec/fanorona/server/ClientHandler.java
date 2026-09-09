package br.com.victorsfec.fanorona.server;

import br.com.victorsfec.fanorona.shared.Protocol;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Leitura por cliente e fila de saída limitada: rede lenta não prende o monitor da sessão. */
final class ClientHandler extends Thread {
    private final FanoronaServer server;
    private final Socket socket;
    private final BlockingQueue<String> outgoing = new ArrayBlockingQueue<>(256);
    volatile GameSession session;
    String playerName;
    private Thread writer;
    ClientHandler(FanoronaServer server, Socket socket) { this.server = server; this.socket = socket; }
    @Override public void run() {
        try {
            // O cliente tem 15 segundos para enviar sua identificação inicial (HELLO).
            socket.setTcpNoDelay(true); socket.setSoTimeout(15000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            // Uma thread de saída consome a fila sem bloquear as regras da partida.
            writer = new Thread(() -> {
                try {
                    while (!socket.isClosed()) { out.write(outgoing.take()); out.newLine(); out.flush(); }
                } catch (IOException | InterruptedException e) { shutdown(); }
            }, "saida-" + threadId());
            writer.start();
            String first = Protocol.readLine(in);
            if (first == null) return;
            String[] hello = Protocol.parts(first);
            if (hello.length != 2 || !hello[0].equals("HELLO")) return;
            playerName = Protocol.userText(hello[1], 30);
            // Após identificar o jogador, retiramos o timeout e entramos no pareamento.
            socket.setSoTimeout(0); server.ready(this);
            String line;
            // Cada linha recebida representa um comando do protocolo; null indica desconexão.
            while ((line = Protocol.readLine(in)) != null) {
                GameSession current = session;
                if (current == null) send("ERROR|" + Protocol.text("Aguarde outro jogador."));
                else current.process(line, this);
            }
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Conexão encerrada: " + e.getMessage());
        } finally { shutdown(); server.remove(this); }
    }
    // Se o cliente não consumir a fila limitada, fechamos a conexão para evitar acúmulo.
    void send(String message) { if (!outgoing.offer(message)) shutdown(); }
    void shutdown() {
        try { socket.close(); } catch (IOException ignored) { }
        if (writer != null) writer.interrupt();
    }
}
