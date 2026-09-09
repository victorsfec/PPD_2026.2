package br.com.victorsfec.fanorona.server;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/** Teste separado que exige ambiente gráfico; opera os botões reais e conexões TCP. */
public final class ServerFrameTest {
    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
    private static <T extends Component> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T match = find(nested, type);
                if (match != null) return match;
            }
        }
        return null;
    }
    private static boolean text(Container parent, String expected) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel label && label.getText().contains(expected)) return true;
            if (child instanceof Container nested && text(nested, expected)) return true;
        }
        return false;
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + 8_000_000_000L;
        while (!edt(condition)) {
            if (System.nanoTime() > deadline) throw new AssertionError("Tempo excedido esperando a interface.");
            Thread.sleep(25);
        }
    }
    private static void click(ServerFrame frame) throws Exception {
        edt(() -> { find(frame, JButton.class).doClick(); return null; });
    }
    private static final class TestClient implements Closeable {
        private final Socket socket;
        private final BufferedReader reader;
        private final OutputStream writer;

        TestClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = socket.getOutputStream();
            } catch (IOException | RuntimeException | Error e) {
                try { socket.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
                throw e;
            }
        }

        @Override public void close() throws IOException {
            try { reader.close(); }
            finally {
                try { writer.close(); }
                finally { socket.close(); }
            }
        }
    }
    private static TestClient connect(int port) throws IOException {
        TestClient client = new TestClient(port);
        try {
            client.socket.setSoTimeout(5000);
            client.writer.write("HELLO|VGVzdGU=\n".getBytes(StandardCharsets.UTF_8));
            String response = client.reader.readLine();
            if (response == null || !response.startsWith("INFO|")) throw new AssertionError("Servidor não respondeu ao cliente.");
            return client;
        } catch (IOException | RuntimeException | Error e) {
            try { client.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    private static void disconnected(TestClient client) throws IOException {
        try {
            if (client.reader.read() != -1) throw new AssertionError("Cliente continuou conectado.");
        } catch (SocketException expected) { /* Windows também pode sinalizar reset. */ }
    }
    private static void snapshot(ServerFrame frame, Path path) throws Exception {
        edt(() -> {
            frame.addNotify(); frame.validate();
            Container content = frame.getContentPane();
            BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            content.printAll(graphics); graphics.dispose();
            ImageIO.write(image, "png", path.toFile());
            return null;
        });
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "target/server-ui" : args[0]);
        Files.createDirectories(output);
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) { port = reservation.getLocalPort(); }
        ServerFrame frame = edt(() -> new ServerFrame(port));
        try {
            snapshot(frame, output.resolve("stopped.png"));
            edt(() -> { find(frame, JTextField.class).setText("65536"); return null; });
            click(frame);
            await(() -> text(frame, "Porta inválida"));
            try (ServerSocket occupied = new ServerSocket(port)) {
                edt(() -> { find(frame, JTextField.class).setText(Integer.toString(occupied.getLocalPort())); return null; });
                click(frame);
                await(() -> text(frame, "Não foi possível"));
            }
            click(frame);
            await(() -> text(frame, "Servidor online"));
            snapshot(frame, output.resolve("running.png"));
            try (TestClient client = connect(port)) {
                click(frame);
                await(() -> text(frame, "Servidor parado"));
                disconnected(client);
            }
            // Reinício na mesma porta, seguido do fechamento da janela com cliente ativo.
            click(frame);
            await(() -> text(frame, "Servidor online"));
            try (TestClient client = connect(port)) {
                edt(() -> { frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); return null; });
                await(() -> !frame.isDisplayable());
                disconnected(client);
            }
            try (ServerSocket reused = new ServerSocket(port)) {
                if (!reused.isBound()) throw new AssertionError("Porta não foi liberada.");
            }
            System.out.println("SUCESSO: interface validada — porta inválida, porta ocupada, iniciar, parar, desconectar, reiniciar e fechar/liberar porta.");
        } finally {
            edt(() -> { frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); return null; });
        }
    }
}
