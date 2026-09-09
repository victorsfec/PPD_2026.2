package br.com.victorsfec.fanorona.game;

import java.util.*;

/** Motor determinístico, independente da rede e do Swing. A sessão controla seu acesso. */
public final class Board {
    public static final int ROWS = 5, COLS = 9;
    // Índice = linha * 9 + coluna; valores: 0 vazio, 1 P1 e 2 P2.
    private final int[] cells;
    private int turn = 1, winner, chain = -1, lastDr, lastDc;
    // Guarda as posições já visitadas na sequência de capturas do turno.
    private final Set<Integer> visited = new HashSet<>();

    /** Uma alternativa completa: origem, destino e modo A, W ou P. */
    public record Move(int from, int to, String mode) {
        public String wire() { return from + "," + to + "," + mode; }
    }

    public Board() {
        cells = new int[45];
        Arrays.fill(cells, 0, 18, 2);
        Arrays.fill(cells, 27, 45, 1);
        int[] middle = {1,2,1,2,0,1,2,1,2};
        System.arraycopy(middle, 0, cells, 18, 9);
    }

    /** Cria posições para os testes e para o comando de demonstração /final. */
    public Board(int[] position, int player) {
        if (position.length != 45 || player < 1 || player > 2)
            throw new IllegalArgumentException("Posição inválida");
        for (int v : position) if (v < 0 || v > 2) throw new IllegalArgumentException("Peça inválida");
        cells = position.clone(); turn = player;
    }

    public int turn() { return turn; }
    public int winner() { return winner; }
    public int chain() { return chain; }
    public int count(int player) { return (int) Arrays.stream(cells).filter(v -> v == player).count(); }
    public String encode() {
        StringBuilder s = new StringBuilder(45);
        for (int v : cells) s.append(v);
        return s.toString();
    }

    /** Diagonais existem apenas nas interseções de paridade par. */
    public static boolean connected(int a, int b) {
        if (a < 0 || b < 0 || a >= 45 || b >= 45 || a == b) return false;
        int dr = Math.abs(a / 9 - b / 9), dc = Math.abs(a % 9 - b % 9);
        return dr <= 1 && dc <= 1 && (dr == 0 || dc == 0 || (a / 9 + a % 9) % 2 == 0);
    }

    /** Captura somente a linha inimiga contígua, parando em vazio, aliado ou borda. */
    private List<Integer> victims(int a, int b, String mode) {
        int dr = b / 9 - a / 9, dc = b % 9 - a % 9;
        int r = b / 9 + dr, c = b % 9 + dc;
        // A procura à frente do destino; W inverte a direção e procura atrás da origem.
        if (mode.equals("W")) { dr = -dr; dc = -dc; r = a / 9 + dr; c = a % 9 + dc; }
        List<Integer> result = new ArrayList<>();
        if (mode.equals("P")) return result;
        while (r >= 0 && r < 5 && c >= 0 && c < 9 && cells[r * 9 + c] == 3 - turn) {
            result.add(r * 9 + c); r += dr; c += dc;
        }
        return result;
    }

    /** A captura inicial é obrigatória; numa sequência só a mesma peça pode agir. */
    public List<Move> legalMoves() {
        List<Move> captures = new ArrayList<>(), quiet = new ArrayList<>();
        if (winner != 0) return captures;
        for (int a = 0; a < 45; a++) {
            if (cells[a] != turn || (chain >= 0 && chain != a)) continue;
            for (int b = 0; b < 45; b++) {
                if (cells[b] != 0 || !connected(a, b) || visited.contains(b)) continue;
                int dr = b / 9 - a / 9, dc = b % 9 - a % 9;
                if (chain >= 0 && dr == lastDr && dc == lastDc) continue;
                for (String mode : new String[]{"A", "W"})
                    if (!victims(a, b, mode).isEmpty()) captures.add(new Move(a, b, mode));
                if (chain < 0) quiet.add(new Move(a, b, "P"));
            }
        }
        // Movimento simples (P) só é permitido sem captura disponível e fora de sequência.
        return captures.isEmpty() && chain < 0 ? quiet : captures;
    }

    /** Valida antes de alterar a matriz; uma rejeição preserva integralmente o estado. */
    public int move(int player, Move move) {
        requireTurn(player);
        if (!legalMoves().contains(move)) throw new IllegalArgumentException("Jogada inválida. Se houver captura, ela é obrigatória.");
        List<Integer> removed = victims(move.from, move.to, move.mode);
        cells[move.to] = cells[move.from]; cells[move.from] = 0;
        for (int v : removed) cells[v] = 0;
        if (count(3 - turn) == 0) { winner = turn; chain = -1; return removed.size(); }
        if (!removed.isEmpty()) {
            visited.add(move.from); visited.add(move.to); chain = move.to;
            lastDr = move.to / 9 - move.from / 9; lastDc = move.to % 9 - move.from % 9;
            // Se houver outra captura, mantém a vez para o jogador continuar ou encerrar.
            if (!legalMoves().isEmpty()) return removed.size();
        }
        nextTurn();
        return removed.size();
    }

    /** O enunciado permite encerrar a sequência depois de pelo menos uma captura. */
    public void endTurn(int player) {
        requireTurn(player);
        if (chain < 0) throw new IllegalArgumentException("Não há sequência para encerrar.");
        nextTurn();
    }
    private void requireTurn(int player) {
        if (winner != 0) throw new IllegalArgumentException("Partida encerrada.");
        if (player != turn) throw new IllegalArgumentException("Aguarde sua vez.");
    }
    private void nextTurn() {
        int previous = turn;
        turn = 3 - turn; chain = -1; visited.clear(); lastDr = lastDc = 0;
        // Convenção explícita do projeto: jogador imobilizado perde.
        if (legalMoves().isEmpty()) winner = previous;
    }
}
