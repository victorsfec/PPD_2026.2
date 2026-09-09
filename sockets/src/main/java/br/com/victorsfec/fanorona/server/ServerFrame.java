package br.com.victorsfec.fanorona.server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Controles na EDT; abertura, escuta e encerramento dos sockets em segundo plano. */
final class ServerFrame extends JFrame {
    private final JTextField portField;
    private final JLabel status = new JLabel("Servidor parado.", SwingConstants.CENTER);
    private final JLabel detail = new JLabel("Escolha a porta e clique em Iniciar Servidor.", SwingConstants.CENTER);
    private final JButton toggle = new JButton("Iniciar Servidor");
    private SwingWorker<Void, Integer> worker;
    private volatile FanoronaServer server;
    private volatile boolean stopRequested;
    private boolean closing;

    ServerFrame(int port) {
        super("Fanorona | Servidor TCP");
        portField = new JTextField(Integer.toString(port), 7);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(12, 18));
        content.setBorder(new EmptyBorder(22, 24, 22, 24));
        JPanel configuration = new JPanel(new FlowLayout(FlowLayout.CENTER));
        configuration.add(new JLabel("Porta TCP:"));
        configuration.add(portField);
        content.add(configuration, BorderLayout.NORTH);
        JPanel messages = new JPanel(new GridLayout(2, 1, 0, 10));
        status.setFont(status.getFont().deriveFont(Font.BOLD, 18f));
        messages.add(status);
        messages.add(detail);
        content.add(messages, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        actions.add(toggle);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(640, 250);
        setMinimumSize(new Dimension(640, 250));
        setLocationRelativeTo(null);
        toggle.addActionListener(event -> { if (worker == null) startServer(); else stopServer(); });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                closing = true;
                if (worker == null) dispose(); else stopServer();
            }
        });
    }

    private void startServer() {
        final int port;
        try {
            port = Integer.parseInt(portField.getText().strip());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            status.setText("Porta inválida.");
            detail.setText("Informe um número entre 1 e 65535.");
            return;
        }
        stopRequested = false;
        portField.setEnabled(false);
        toggle.setText("Parar Servidor");
        status.setText("Iniciando servidor...");
        detail.setText("Abrindo a porta TCP " + port + ".");
        // SwingWorker executa a rede em segundo plano e devolve as atualizações à EDT.
        worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws IOException {
                try (FanoronaServer active = new FanoronaServer(port)) {
                    server = active;
                    // Uma solicitação de parada pode chegar enquanto o socket está sendo criado.
                    if (!stopRequested) {
                        publish(active.port());
                        active.run();
                    }
                } finally { server = null; }
                return null;
            }
            @Override protected void process(List<Integer> ports) {
                if (!stopRequested) {
                    status.setText("Servidor online na porta " + ports.getLast() + ".");
                    detail.setText("Aguardando jogadores. Parar encerra todas as conexões.");
                }
            }
            // Só libera um novo início depois que a execução anterior terminou.
            @Override protected void done() {
                String error = null;
                try { get(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); error = "Operação interrompida."; }
                catch (ExecutionException e) { error = e.getCause().getMessage(); }
                worker = null;
                toggle.setText("Iniciar Servidor");
                toggle.setEnabled(true);
                portField.setEnabled(true);
                status.setText(error == null ? "Servidor parado." : "Não foi possível manter o servidor ativo.");
                detail.setText(error == null ? "Você pode iniciar uma nova sessão do servidor." : "Erro: " + error);
                if (closing) dispose();
            }
        };
        worker.execute();
    }

    private void stopServer() {
        stopRequested = true;
        toggle.setEnabled(false);
        status.setText("Parando servidor...");
        detail.setText("Encerrando as conexões dos jogadores.");
        FanoronaServer active = server;
        if (active != null) {
            // Encerrar conexões também fica fora da EDT para não travar a janela.
            new Thread(() -> {
                try { active.close(); }
                catch (IOException e) { System.err.println("Falha ao fechar servidor: " + e.getMessage()); }
            }, "encerramento-servidor").start();
        }
    }
}
