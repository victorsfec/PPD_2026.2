package br.com.victorsfec.mom.dashboard;

import br.com.victorsfec.mom.shared.ProtocolMOM;
import jakarta.jms.Connection;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;

/** A EDT consulta snapshots; callbacks JMS nunca alteram componentes Swing. */
public final class DashboardFrameMOM extends JFrame {
    private final ResultModelMOM model;
    // A tabela é de leitura e pode ordenar a coluna de ocorrências como valores numéricos.
    private final DefaultTableModel rows = new DefaultTableModel(new Object[]{"Palavra-chave", "Ocorrências"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Class<?> getColumnClass(int column) { return column == 1 ? Long.class : String.class; }
    };
    private final JLabel status = new JLabel("Aguardando mensagens do broker...");
    private final JLabel progress = new JLabel("0 linhas processadas");
    private final JLabel workers = new JLabel("Workers: aguardando resultados");
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final Timer refresh;
    private final long start = System.nanoTime();

    /** Monta a tabela, a barra de progresso e o estado da execução. */
    public DashboardFrameMOM(String job, ResultModelMOM model) {
        super("MOM | Dashboard de palavras");
        this.model = model;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(12, 16));
        content.setBorder(new EmptyBorder(22, 24, 22, 24));
        JPanel heading = new JPanel(new GridLayout(3, 1, 0, 6));
        JLabel title = new JLabel("Contagem de palavras em tempo real");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 21f));
        heading.add(title);
        heading.add(new JLabel("Execução: " + job));
        heading.add(status);
        content.add(heading, BorderLayout.NORTH);
        JTable table = new JTable(rows);
        table.setRowHeight(31);
        table.setFont(table.getFont().deriveFont(15f));
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);
        for (String word : model.snapshot().words()) rows.addRow(new Object[]{word, 0L});
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel footer = new JPanel(new GridLayout(3, 1, 0, 8));
        footer.add(progress); footer.add(bar); footer.add(workers);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(780, 550);
        setMinimumSize(new Dimension(660, 420));
        setLocationByPlatform(true);
        // O Timer Swing concentra as atualizações da tela na EDT a cada 150 ms.
        refresh = new Timer(150, event -> update());
        refresh.start();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { refresh.stop(); }
        });
        update();
    }
    /** Atualiza a visualização a partir de uma única leitura consistente do modelo. */
    private void update() {
        var snapshot = model.snapshot();
        for (int i = 0; i < snapshot.counts().size(); i++) rows.setValueAt(snapshot.counts().get(i), i, 1);
        String total = snapshot.expected() < 0 ? "total em leitura" : "de " + snapshot.expected();
        progress.setText(snapshot.processed() + " linhas processadas (" + total + ") | "
                + snapshot.readers() + "/2 leitores concluídos | "
                + String.format(java.util.Locale.ROOT, "%.1f s", (System.nanoTime() - start) / 1e9));
        // O total fica disponível depois que os dois leitores anunciam o fim da leitura.
        bar.setIndeterminate(snapshot.expected() < 0 && snapshot.error() == null);
        if (snapshot.expected() >= 0) bar.setValue(snapshot.expected() == 0 ? 1000
                : (int) (1000.0 * snapshot.processed() / snapshot.expected()));
        workers.setText("Linhas por worker: " + (snapshot.workers().isEmpty() ? "nenhuma" : snapshot.workers()));
        if (snapshot.error() != null) {
            status.setText("Interrompido: " + snapshot.error());
            status.setForeground(new Color(170, 30, 30));
            refresh.stop();
        } else if (snapshot.complete()) {
            status.setText("Concluído. Todas as linhas publicadas foram contabilizadas.");
            status.setForeground(new Color(20, 115, 65));
            refresh.stop();
        } else status.setText("Recebendo resultados pelo tópico " + ProtocolMOM.TOPIC);
    }
    /** Mantém a assinatura ativa enquanto a janela estiver aberta. Chamado na EDT. */
    public void keepSubscriptionUntilClosed(Connection connection) {
        Runnable close = () -> Thread.ofVirtual().start(() -> {
            try { connection.close(); } catch (Exception ignored) { }
        });
        if (!isDisplayable()) {
            close.run();
            return;
        }
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { close.run(); }
        });
    }

    /** Abre um dashboard independente que deve assinar antes da publicação dos resultados. */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Uso: dashboard.jar URL_BROKER UUID 'palavra1,palavra2'");
        String job = ProtocolMOM.jobId(args[1]);
        ResultModelMOM model = new ResultModelMOM(job, ProtocolMOM.keywords(args[2]));
        Connection connection = ProtocolMOM.connect(args[0]);
        try { TopicSubscriberMOM.subscribe(connection, job, model); }
        catch (Exception error) { connection.close(); throw error; }
        SwingUtilities.invokeLater(() -> {
            DashboardFrameMOM frame = new DashboardFrameMOM(job, model);
            frame.setVisible(true);
            frame.keepSubscriptionUntilClosed(connection);
        });
    }
}
