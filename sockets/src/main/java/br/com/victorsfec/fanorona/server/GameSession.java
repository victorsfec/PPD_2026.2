package br.com.victorsfec.fanorona.server;

import br.com.victorsfec.fanorona.game.Board;
import br.com.victorsfec.fanorona.shared.Protocol;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Autoridade da partida. O monitor serializa validação, alteração e publicação do estado. */
final class GameSession {
    private final ClientHandler[] players;
    private Board board = new Board();
    private final int[] moves = new int[2], invalid = new int[2], captures = new int[2];
    private long revision;
    private boolean ended;
    private boolean playerDisconnected;
    private int drawOffer;
    private int restartRequester;
    private long restartOfferId;
    GameSession(ClientHandler first, ClientHandler second) { players = new ClientHandler[]{first, second}; }
    synchronized void start() {
        for (int i = 0; i < 2; i++) {
            players[i].session = this;
            players[i].send("WELCOME|" + (i + 1) + "|" + Protocol.text(players[0].playerName) + "|" + Protocol.text(players[1].playerName));
        }
        publish();
    }
    // synchronized permite que apenas uma thread por vez altere esta partida.
    synchronized void process(String line, ClientHandler sender) {
        // A identidade vem da conexão, não de um número informado pelo cliente.
        int id = sender == players[0] ? 1 : 2;
        try {
            String[] p = Protocol.parts(line);
            if (p[0].equals("CHAT") && p.length == 2) {
                String text = Protocol.userText(p[1], 500);
                if (text.equalsIgnoreCase("/final")) {
                    if (ended) throw new IllegalArgumentException("Partida encerrada. Use Reiniciar partida antes de demonstrar novamente.");
                    prepareFinal(id);
                } else broadcast("CHAT|" + id + "|" + Protocol.text(text));
                return;
            }
            // Reiniciar é permitido também após o resultado, mantendo a mesma dupla.
            if (p[0].equals("RESTART")) {
                if (p.length != 2) throw new IllegalArgumentException("Formato RESTART inválido.");
                checkRevision(p[1]);
                if (playerDisconnected) throw new IllegalArgumentException("O adversário desconectou. Conecte uma nova dupla.");
                if (restartRequester != 0) throw new IllegalArgumentException("Já existe uma solicitação de reinício aguardando resposta.");
                restartRequester = id; restartOfferId++;
                broadcast("RESTART_OFFER|" + restartOfferId + "|" + id);
                return;
            }
            if (p[0].equals("RESTART_REPLY")) {
                if (p.length != 3 || (!p[2].equals("ACCEPT") && !p[2].equals("DECLINE")))
                    throw new IllegalArgumentException("Resposta de reinício inválida.");
                // Somente o adversário pode responder à solicitação ainda vigente.
                if (restartRequester == 0 || Long.parseLong(p[1]) != restartOfferId)
                    throw new IllegalArgumentException("Solicitação de reinício expirada.");
                if (id == restartRequester) throw new IllegalArgumentException("Aguarde o aceite do adversário.");
                if (p[2].equals("DECLINE")) { cancelRestart("O adversário recusou o reinício."); return; }
                restartRequester = 0;
                board = new Board(); ended = false; drawOffer = 0;
                Arrays.fill(moves, 0); Arrays.fill(invalid, 0); Arrays.fill(captures, 0);
                // Mantém a revisão crescente para rejeitar comandos da partida anterior.
                revision++;
                broadcast("RESTARTED|" + revision);
                broadcast("INFO|" + Protocol.text("Ambos aceitaram reiniciar. Estatísticas zeradas; P1 começa."));
                publish();
                return;
            }
            if (ended) throw new IllegalArgumentException("Partida encerrada.");
            switch (p[0]) {
                case "MOVE":
                    if (p.length != 5) throw new IllegalArgumentException("Formato MOVE inválido.");
                    checkRevision(p[1]);
                    captures[id - 1] += board.move(id, new Board.Move(Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4]));
                    moves[id - 1]++; changed(); break;
                case "END_TURN":
                    if (p.length != 2) throw new IllegalArgumentException("Formato END_TURN inválido.");
                    checkRevision(p[1]); board.endTurn(id); changed(); break;
                case "FORFEIT":
                    if (p.length != 1) throw new IllegalArgumentException("Formato inválido.");
                    finish(3 - id, "Desistência de P" + id); break;
                case "DRAW":
                    if (p.length != 1) throw new IllegalArgumentException("Formato inválido.");
                    if (drawOffer == 3 - id) finish(0, "Empate por acordo");
                    else { drawOffer = id; broadcast("INFO|" + Protocol.text("P" + id + " propôs empate. O adversário pode aceitar no botão Empate. Uma jogada cancela a oferta.")); }
                    break;
                default: throw new IllegalArgumentException("Comando desconhecido.");
            }
        } catch (IllegalArgumentException e) {
            invalid[id - 1]++; sender.send("ERROR|" + Protocol.text(e.getMessage()));
        }
    }
    // Comando de demonstração: quem escreveu /final captura a última peça em C3 -> D3.
    private void prepareFinal(int player) {
        int[] position = new int[45];
        position[20] = player;     // C3: peça de quem solicitou a demonstração.
        position[22] = 3 - player; // E3: última peça adversária, capturada por aproximação.
        board = new Board(position, player);
        Arrays.fill(moves, 0); Arrays.fill(invalid, 0); Arrays.fill(captures, 0);
        broadcast("INFO|" + Protocol.text("Demonstração /final solicitada por P" + player
            + ". Tabuleiro e estatísticas reiniciados. P" + player + ": mova C3 para D3 para vencer por aproximação."));
        // A revisão continua aumentando para invalidar cliques anteriores à preparação.
        changed();
    }
    // Rejeita cliques feitos sobre uma versão antiga do tabuleiro.
    private void checkRevision(String value) {
        if (Long.parseLong(value) != revision) throw new IllegalArgumentException("Estado desatualizado. Selecione a peça novamente.");
    }
    private void changed() {
        cancelRestart("O estado da partida mudou. Solicite o reinício novamente.");
        drawOffer = 0; revision++; publish();
        if (board.winner() != 0) finish(board.winner(), "Captura de todas as peças ou imobilização");
    }
    // Os dois jogadores recebem o mesmo estado completo e os movimentos permitidos.
    private void publish() {
        String legal = board.legalMoves().stream().map(Board.Move::wire).collect(Collectors.joining(";"));
        broadcast("STATE|" + revision + "|" + board.turn() + "|" + board.chain() + "|" + board.encode() + "|" + legal);
    }
    synchronized void disconnect(ClientHandler client) {
        playerDisconnected = true;
        cancelRestart("Um jogador desconectou.");
        if (!ended) finish(client == players[0] ? 2 : 1, "Desconexão do adversário");
        broadcast("PEER_LEFT|" + (client == players[0] ? 1 : 2));
    }
    private void finish(int winner, String reason) {
        if (ended) return;
        cancelRestart("A partida terminou. Uma nova solicitação pode ser enviada.");
        ended = true;
        broadcast("OVER|" + winner + "|" + Protocol.text(reason) + "|" + moves[0] + "|" + moves[1] + "|" + captures[0] + "|" + captures[1] + "|" + invalid[0] + "|" + invalid[1]);
    }
    private void cancelRestart(String reason) {
        if (restartRequester == 0) return;
        restartRequester = 0;
        broadcast("RESTART_CANCELLED|" + restartOfferId + "|" + Protocol.text(reason));
    }
    private void broadcast(String message) { for (ClientHandler p : players) p.send(message); }
}
