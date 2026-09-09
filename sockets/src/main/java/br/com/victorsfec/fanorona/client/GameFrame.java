package br.com.victorsfec.fanorona.client;

import br.com.victorsfec.fanorona.game.Board;
import br.com.victorsfec.fanorona.shared.Protocol;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/** Visualização de snapshots do servidor. Cliques só enviam intenções de movimento. */
final class GameFrame extends JFrame {
    private final FanoronaClient client;
    private final JLabel status = new JLabel("Conectando..."), score = new JLabel("22 peças por jogador");
    private final JTextArea chat = new JTextArea();
    private final JTextField message = new JTextField();
    private final JButton end = new JButton("Encerrar sequência"), forfeit = new JButton("Desistir"), draw = new JButton("Empate"), send = new JButton("Enviar");
    private final BoardPanel boardPanel = new BoardPanel();
    private final JButton restart = new JButton("Reiniciar partida");
    private final JButton declineRestart = new JButton("Recusar reinício");
    private int restartRequester;
    private long restartOfferId;
    private final String[] names = {"P1", "P2"};
    private String cells = "0".repeat(45);
    private List<Board.Move> legal = new ArrayList<>();
    private int id, turn, chain = -1, selected = -1;
    private long revision;
    private boolean ended, pending;
    private boolean paired;

    GameFrame(FanoronaClient client) {
        super("Fanorona | Projeto 1 | PPD 2026.2"); this.client = client;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 640)); setSize(1160, 740); setLocationRelativeTo(null);
        JPanel main = new JPanel(new BorderLayout(18, 14));
        main.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); setContentPane(main);
        JPanel header = new JPanel(new GridLayout(0, 1, 0, 6));
        JLabel title = new JLabel("FANORONA"); title.setFont(new Font("SansSerif", Font.BOLD, 27));
        status.setFont(new Font("SansSerif", Font.BOLD, 15));
        header.add(title); header.add(status); header.add(score); main.add(header, BorderLayout.NORTH);
        main.add(boardPanel, BorderLayout.CENTER);
        JPanel side = new JPanel(new BorderLayout(5, 8)); side.setPreferredSize(new Dimension(285, 420));
        side.add(new JLabel("Chat da partida"), BorderLayout.NORTH);
        chat.setEditable(false); chat.setLineWrap(true); chat.setWrapStyleWord(true); chat.setFont(new Font("SansSerif", Font.PLAIN, 14));
        side.add(new JScrollPane(chat), BorderLayout.CENTER);
        JPanel composer = new JPanel(new BorderLayout(4, 4)); composer.add(message); composer.add(send, BorderLayout.EAST);
        side.add(composer, BorderLayout.SOUTH); main.add(side, BorderLayout.EAST);
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(new JLabel("Selecione sua peça e um destino verde. P1: branca • P2: preta."), BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(end); actions.add(draw); actions.add(forfeit); actions.add(restart); actions.add(declineRestart);
        JButton help = new JButton("Regras"); actions.add(help); footer.add(actions, BorderLayout.SOUTH); main.add(footer, BorderLayout.SOUTH);
        send.addActionListener(e -> sendChat()); message.addActionListener(e -> sendChat());
        end.addActionListener(e -> { pending = true; client.send("END_TURN|" + revision); buttons(); });
        forfeit.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Confirmar desistência?", "Desistir", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) client.send("FORFEIT");
        });
        draw.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Propor ou aceitar empate?", "Empate", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) client.send("DRAW");
        });
        restart.addActionListener(e -> {
            long offer = restartOfferId;
            boolean accepting = restartRequester != 0 && restartRequester != id;
            if (JOptionPane.showConfirmDialog(this, accepting
                    ? "Aceitar o reinício solicitado pelo adversário e zerar as estatísticas?"
                    : "Solicitar o reinício? A partida só reiniciará se o adversário aceitar.",
                    "Reiniciar partida", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION && paired && !pending) {
                // Uma resposta mantém o ID visto antes da confirmação para evitar aceite de outra oferta.
                pending = true; client.send(accepting ? "RESTART_REPLY|" + offer + "|ACCEPT" : "RESTART|" + revision); buttons();
            }
        });
        declineRestart.addActionListener(e -> {
            pending = true; client.send("RESTART_REPLY|" + restartOfferId + "|DECLINE"); buttons();
        });
        help.addActionListener(e -> JOptionPane.showMessageDialog(this,
            "P1 (brancas, primeiro conectado) inicia.\nMova pelas linhas para uma interseção vizinha vazia.\nCaptura inicial obrigatória quando disponível.\nAproximação: elimina a linha inimiga à frente do destino.\nAfastamento: elimina a linha inimiga atrás da origem.\nEscolha um único modo quando ambos forem possíveis.\nNa sequência: mesma peça, sem revisitar ponto nem repetir direção.\nVocê pode encerrar a sequência após uma captura.\nVence quem elimina ou imobiliza o adversário.\nO chat permanece disponível após o resultado."));
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) {
            if (ended || id == 0 || JOptionPane.showConfirmDialog(GameFrame.this, "Sair concede vitória ao adversário. Continuar?", "Sair", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                client.close(); dispose();
            }
        }});
        buttons();
    }
    private void sendChat() {
        try { client.send("CHAT|" + Protocol.text(Protocol.userText(Protocol.text(message.getText()), 500))); message.setText(""); }
        catch (IllegalArgumentException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }
    void disconnected(String reason) {
        paired = false;
        ended = true; pending = false; status.setText("Conexão encerrada"); append(reason);
        send.setEnabled(false); message.setEnabled(false); buttons();
    }
    void receive(String line) {
        try {
            String[] p = Protocol.parts(line);
            switch (p[0]) {
                case "WELCOME":
                    paired = true;
                    id = Integer.parseInt(p[1]); names[0] = Protocol.untext(p[2]); names[1] = Protocol.untext(p[3]);
                    setTitle("Fanorona | Você: P" + id + " - " + names[id - 1]); break;
                // A tela substitui sua cópia do tabuleiro pelo estado confirmado no servidor.
                case "STATE":
                    revision = Long.parseLong(p[1]); turn = Integer.parseInt(p[2]); chain = Integer.parseInt(p[3]); cells = p[4];
                    legal = new ArrayList<>();
                    if (!p[5].isEmpty()) for (String m : p[5].split(";")) {
                        String[] v = m.split(","); legal.add(new Board.Move(Integer.parseInt(v[0]), Integer.parseInt(v[1]), v[2]));
                    }
                    selected = chain; pending = false;
                    status.setText((turn == id ? "Sua vez" : "Vez do adversário") + " | P" + turn + (chain >= 0 ? " | Continue capturando ou encerre a sequência" : ""));
                    score.setText("P1 " + names[0] + ": " + cells.chars().filter(c -> c == '1').count() + " peças     |     P2 " + names[1] + ": " + cells.chars().filter(c -> c == '2').count() + " peças");
                    break;
                case "RESTART_OFFER":
                    restartOfferId = Long.parseLong(p[1]); restartRequester = Integer.parseInt(p[2]); pending = false;
                    append(restartRequester == id ? "Reinício solicitado. Aguardando o adversário."
                        : "O adversário solicitou reinício. Clique em Aceitar reinício ou Recusar reinício.");
                    break;
                case "RESTART_CANCELLED":
                    restartRequester = 0; pending = false; append(Protocol.untext(p[2])); break;
                // Libera uma nova partida sem recriar a conexão nem apagar o chat.
                case "RESTARTED":
                    restartRequester = 0;
                    revision = Long.parseLong(p[1]); ended = false; pending = true; selected = -1;
                    for (Window dialog : getOwnedWindows()) if (dialog instanceof JDialog) dialog.dispose();
                    break;
                case "PEER_LEFT":
                    paired = false; append("O adversário desconectou. Para jogar novamente, conecte uma nova dupla."); break;
                case "CHAT": append("P" + p[1] + " " + names[Integer.parseInt(p[1]) - 1] + ": " + Protocol.untext(p[2])); break;
                case "ERROR": pending = false; append("Aviso: " + Protocol.untext(p[1])); break;
                case "INFO": append(Protocol.untext(p[1])); break;
                case "OVER":
                    ended = true; pending = false;
                    int winner = Integer.parseInt(p[1]);
                    String result = winner == 0 ? "Empate" : "P" + winner + " - " + names[winner - 1] + " venceu!";
                    status.setText(result); append(result + " - " + Protocol.untext(p[2])); buttons(); boardPanel.repaint();
                    ResultsDialog.show(this, p, result); break;
                default: append("Mensagem desconhecida do servidor.");
            }
            buttons(); boardPanel.repaint();
        } catch (RuntimeException e) { client.close(); disconnected("Protocolo inválido recebido."); }
    }
    private void append(String text) { chat.append(text + "\n"); chat.setCaretPosition(chat.getDocument().getLength()); }
    private void buttons() {
        restart.setText(restartRequester == 0 ? "Reiniciar partida" : restartRequester == id ? "Aguardando adversário" : "Aceitar reinício");
        restart.setEnabled(paired && !pending && restartRequester != id);
        declineRestart.setVisible(paired && restartRequester != 0 && restartRequester != id);
        declineRestart.setEnabled(!pending);
        end.setEnabled(!ended && !pending && id == turn && chain >= 0);
        forfeit.setEnabled(!ended && id > 0); draw.setEnabled(!ended && id > 0);
    }
    private void click(int cell) {
        if (ended || pending || turn != id || id == 0) return;
        if (cells.charAt(cell) - '0' == id) { selected = cell; boardPanel.repaint(); return; }
        // As opções de destino vêm da lista de movimentos enviada pelo servidor.
        List<Board.Move> options = legal.stream().filter(m -> m.from() == selected && m.to() == cell).toList();
        if (options.isEmpty()) return;
        Board.Move chosen = options.get(0);
        if (options.size() == 2) {
            int choice = JOptionPane.showOptionDialog(this, "Qual linha de peças deseja capturar?", "Modo de captura", JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, new String[]{"Aproximação", "Afastamento"}, "Aproximação");
            if (choice < 0) return;
            String mode = choice == 0 ? "A" : "W";
            chosen = options.stream().filter(m -> m.mode().equals(mode)).findFirst().orElseThrow();
        }
        // Envia a intenção e bloqueia novos cliques até receber a resposta do servidor.
        client.send("MOVE|" + revision + "|" + chosen.from() + "|" + chosen.to() + "|" + chosen.mode()); pending = true; buttons();
    }
    /** Desenha exatamente as conexões aceitas pelo motor, evitando diagonais fictícias. */
    private final class BoardPanel extends JPanel {
        private int step, left, top;
        BoardPanel() {
            setBackground(new Color(239, 225, 198));
            addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) {
                if (step <= 0) return;
                int c = Math.round((e.getX() - left) / (float)step), r = Math.round((e.getY() - top) / (float)step);
                if (r >= 0 && r < 5 && c >= 0 && c < 9 && Math.abs(e.getX() - (left + c * step)) < step / 3 && Math.abs(e.getY() - (top + r * step)) < step / 3) click(r * 9 + c);
            }});
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Reserva também os raios das peças nas bordas: eles crescem junto com o passo.
            int labelMargin = g.getFontMetrics().getHeight() + 16;
            step = Math.max(1, (int)Math.min((getWidth() - 2.0 * labelMargin) / 8.5,
                (getHeight() - 2.0 * labelMargin) / 4.5));
            int radius = Math.max(9, step / 4);
            left = (getWidth() - step * 8) / 2; top = (getHeight() - step * 4) / 2;
            g.setColor(new Color(125, 102, 70)); g.setStroke(new BasicStroke(1.5f));
            for (int a = 0; a < 45; a++) for (int b = a + 1; b < 45; b++) if (Board.connected(a, b))
                g.drawLine(left + a % 9 * step, top + a / 9 * step, left + b % 9 * step, top + b / 9 * step);
            for (int a = 0; a < 45; a++) {
                int x = left + a % 9 * step, y = top + a / 9 * step;
                g.setColor(new Color(100, 80, 55)); g.fillOval(x - 3, y - 3, 6, 6);
                if (cells.charAt(a) != '0') {
                    g.setColor(cells.charAt(a) == '1' ? Color.WHITE : new Color(36, 42, 49));
                    g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
                    g.setColor(Color.DARK_GRAY); g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
                    g.setColor(cells.charAt(a) == '1' ? Color.BLACK : Color.WHITE); g.drawString(cells.substring(a, a + 1), x - 4, y + 5);
                }
                if (a == selected && turn == id && !ended) { g.setColor(new Color(20, 110, 205)); g.setStroke(new BasicStroke(3)); g.drawOval(x - radius - 4, y - radius - 4, radius * 2 + 8, radius * 2 + 8); }
                final int destination = a;
                if (!ended && turn == id && legal.stream().anyMatch(m -> m.from() == selected && m.to() == destination)) {
                    g.setColor(new Color(22, 125, 76)); g.fillOval(x - 9, y - 9, 18, 18);
                }
            }
            g.setColor(Color.DARK_GRAY);
            // Letras e números ficam fora das peças, inclusive com a janela maximizada.
            for (int c = 0; c < 9; c++) g.drawString(String.valueOf((char)('A' + c)), left + c * step - 4, top - radius - 12);
            for (int r = 0; r < 5; r++) g.drawString(String.valueOf(r + 1), left - radius - 14, top + r * step + 4);
            g.dispose();
        }
    }
}
