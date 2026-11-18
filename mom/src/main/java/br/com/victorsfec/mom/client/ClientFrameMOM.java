package br.com.victorsfec.mom.client;

import br.com.victorsfec.mom.broker.MomBroker;
import br.com.victorsfec.mom.dashboard.DashboardFrameMOM;
import br.com.victorsfec.mom.shared.ProtocolMOM;
import jakarta.jms.Connection;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/** Formulário Swing que coleta a entrada e delega a execução ao workflow. */
public final class ClientFrameMOM extends JFrame {
    private final JTextField file = new JTextField(34);
    private final JTextArea words = new JTextArea("java, paralelo, distribuída", 3, 28);
    private final JTextField url = new JTextField(ProtocolMOM.URL);
    private final JTextField job = new JTextField(UUID.randomUUID().toString());
    private final JComboBox<String> charset = new JComboBox<>(new String[]{"UTF-8", "ISO-8859-1", "windows-1252"});
    private final JSpinner workerCount = new JSpinner(new SpinnerNumberModel(3, 0, 32, 1));
    private final JCheckBox local = new JCheckBox("Iniciar broker local (127.0.0.1:61616)", true);
    private final JButton start = new JButton("Iniciar contagem"), cancel = new JButton("Cancelar");
    private final JLabel status = new JLabel("Selecione o arquivo e as palavras-chave.");
    private final JButton choose = new JButton("Selecionar...");
    private WorkflowMOM workflow;
    private boolean closing;

    /** Monta o formulário e associa os botões às ações do cliente. */
    public ClientFrameMOM() {
        super("Projeto MOM | Contagem paralela de palavras");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(16, 18));
        content.setBorder(new EmptyBorder(24, 26, 24, 26));
        JLabel title = new JLabel("Workflow de contagem paralela");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        content.add(title, BorderLayout.NORTH);
        JPanel form = new JPanel(new GridBagLayout());
        JPanel fileRow = new JPanel(new BorderLayout(8, 0)); fileRow.add(file); fileRow.add(choose, BorderLayout.EAST);
        words.setLineWrap(true); words.setWrapStyleWord(true);
        addRow(form, 0, "Arquivo de entrada", fileRow);
        addRow(form, 1, "Palavras (vírgula, ; ou linha)", new JScrollPane(words));
        addRow(form, 2, "Codificação do arquivo", charset);
        addRow(form, 3, "Workers locais (0 = externos)", workerCount);
        addRow(form, 4, "Broker JMS", url);
        addRow(form, 5, "ID da execução (UUID)", job);
        addRow(form, 6, "", local);
        content.add(form, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(0, 12));
        footer.add(status, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel); buttons.add(start); footer.add(buttons, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(900, 510);
        setMinimumSize(new Dimension(840, 480));
        setLocationRelativeTo(null);
        cancel.setEnabled(false);
        url.setEnabled(false);
        choose.addActionListener(event -> {
            JFileChooser dialog = new JFileChooser();
            if (dialog.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) file.setText(dialog.getSelectedFile().getAbsolutePath());
        });
        local.addActionListener(event -> url.setEnabled(!local.isSelected()));
        start.addActionListener(event -> start());
        cancel.addActionListener(event -> stop());
        addWindowListener(new WindowAdapter() {
            // Se houver trabalho ativo, o fechamento da janela aguarda seu encerramento.
            @Override public void windowClosing(WindowEvent event) {
                closing = true;
                if (workflow == null) dispose(); else stop();
            }
        });
    }
    /** Alinha cada rótulo ao campo correspondente no formulário. */
    private static void addRow(JPanel panel, int row, String text, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row; c.insets = new Insets(5, 0, 5, 12); c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(text), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, c);
    }
    /** Bloqueia os parâmetros e habilita o cancelamento durante a contagem. */
    private void controls(boolean running) {
        for (JComponent component : new JComponent[]{file, words, charset, workerCount, job, local, choose, start}) component.setEnabled(!running);
        url.setEnabled(!running && !local.isSelected()); cancel.setEnabled(running);
    }
    /** Valida os dados, abre o dashboard e inicia a contagem em segundo plano. */
    private void start() {
        final boolean startLocal = local.isSelected();
        final String selectedJob;
        try {
            selectedJob = ProtocolMOM.jobId(job.getText().strip());
            workflow = new WorkflowMOM(startLocal ? ProtocolMOM.URL : url.getText().strip(), selectedJob,
                    Path.of(file.getText().strip()), Charset.forName((String) charset.getSelectedItem()),
                    ProtocolMOM.keywords(words.getText()), (Integer) workerCount.getValue());
        } catch (Exception error) { JOptionPane.showMessageDialog(this, error.getMessage(), "Confira os dados", JOptionPane.WARNING_MESSAGE); return; }
        WorkflowMOM active = workflow;
        DashboardFrameMOM dashboard = new DashboardFrameMOM(selectedJob, active.model());
        dashboard.setVisible(true);
        controls(true); status.setText("Iniciando execução " + selectedJob);
        // A EDT é a thread do Swing. Operações de rede e disco executam fora dela.
        new SwingWorker<Connection, Void>() {
            /** Abre o broker local quando solicitado e aguarda o resultado do workflow. */
            @Override protected Connection doInBackground() throws Exception {
                try (MomBroker _ = startLocal ? new MomBroker("tcp://127.0.0.1:61616") : null; active) {
                    active.execute();
                    // O broker externo continua disponível para a janela após a contagem.
                    return startLocal ? null : active.takeSubscriberConnection();
                } catch (Exception error) { active.model().fail(error); throw error; }
                finally { active.close(); }
            }
            /** Atualiza o formulário na EDT e prepara um novo UUID para outra contagem. */
            @Override protected void done() {
                try {
                    Connection subscription = get();
                    if (subscription != null) dashboard.keepSubscriptionUntilClosed(subscription);
                    status.setText("Contagem concluída. Confira os totais no dashboard.");
                }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); status.setText("Interrompido."); }
                catch (ExecutionException error) { status.setText("Execução interrompida: " + error.getCause().getMessage()); }
                workflow = null; controls(false); job.setText(UUID.randomUUID().toString());
                if (closing) { dashboard.dispose(); dispose(); }
            }
        }.execute();
    }
    /** Solicita o encerramento em uma thread separada para manter a janela responsiva. */
    private void stop() {
        WorkflowMOM active = workflow;
        if (active == null) return;
        cancel.setEnabled(false); status.setText("Encerrando conexões e tarefas...");
        Thread.ofVirtual().start(active::close);
    }
}
