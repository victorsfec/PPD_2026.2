package br.com.victorsfec.fanorona;

import br.com.victorsfec.fanorona.game.Board;
import br.com.victorsfec.fanorona.server.FanoronaServer;
import br.com.victorsfec.fanorona.shared.Protocol;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Testes sem bibliotecas externas: falhas lançam AssertionError e interrompem o build. */
public final class AppTest {
    private static int checks;
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
    }
    private static void rejects(Runnable action, String name) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(name);
    }
    private static Board position(int... values) {
        int[] cells = new int[45];
        for (int i = 0; i < values.length; i += 2) cells[values[i]] = values[i + 1];
        return new Board(cells, 1);
    }
    private static void rules() {
        Board start = new Board();
        check(start.count(1) == 22 && start.count(2) == 22 && start.encode().charAt(22) == '0', "22 peças e centro vazio");
        check(start.turn() == 1, "P1 inicia");
        check(Board.connected(0, 10) && !Board.connected(1, 11) && !Board.connected(8, 9), "linhas e bordas");
        check(start.legalMoves().stream().noneMatch(m -> m.mode().equals("P")), "captura inicial obrigatória");
        String before = start.encode();
        rejects(() -> start.move(2, start.legalMoves().get(0)), "fora de turno");
        rejects(() -> start.move(1, new Board.Move(-1, 80, "A")), "coordenadas inválidas");
        check(before.equals(start.encode()), "rejeição atômica");
        Board approach = position(18,1,20,2,21,2,23,2);
        check(approach.move(1,new Board.Move(18,19,"A")) == 2 && approach.count(2) == 1, "aproximação para no vazio");
        Board withdrawal = position(20,1,19,2,18,2,44,2);
        check(withdrawal.move(1,new Board.Move(20,21,"W")) == 2, "afastamento captura linha");
        Board both = position(20,1,19,2,22,2,44,2);
        check(both.legalMoves().containsAll(List.of(new Board.Move(20,21,"A"), new Board.Move(20,21,"W"))), "escolha dos modos");
        both.move(1,new Board.Move(20,21,"A"));
        check(both.encode().charAt(19) == '2' && both.encode().charAt(22) == '0', "não captura os dois lados");
        Board sequence = position(20,1,22,2,12,2,44,2);
        sequence.move(1,new Board.Move(20,21,"A"));
        check(sequence.chain() == 21 && sequence.turn() == 1, "continuidade com a mesma peça");
        check(sequence.legalMoves().stream().allMatch(m -> m.from() == 21 && m.to() != 20), "não revisita origem");
        rejects(() -> sequence.move(1,new Board.Move(21,22,"P")), "sequência não permite movimento simples");
        sequence.move(1,new Board.Move(21,30,"W"));
        check(sequence.count(2) == 1, "segunda captura");
        Board optional = position(20,1,22,2,12,2,44,2);
        optional.move(1,new Board.Move(20,21,"A")); optional.endTurn(1);
        check(optional.turn() == 2 && optional.chain() == -1, "fim opcional da sequência");
        rejects(() -> new Board().endTurn(1), "não permite passar turno");
        Board direction = position(20,1,19,2,23,2,12,2,44,2);
        direction.move(1,new Board.Move(20,21,"W"));
        check(direction.legalMoves().stream().noneMatch(m -> m.to() == 22), "não repete direção consecutiva");
        Board simple = position(0,1,44,2);
        simple.move(1,new Board.Move(0,1,"P")); check(simple.turn() == 2, "movimento simples troca turno");
        Board win = position(18,1,20,2);
        win.move(1,new Board.Move(18,19,"A")); check(win.winner() == 1, "vitória por eliminação");
        rejects(() -> win.move(1,new Board.Move(19,18,"P")), "partida encerrada");
        int[] blocked = new int[45]; Arrays.fill(blocked, 1); blocked[0] = 2; blocked[44] = 0;
        Board immobilized = new Board(blocked, 1);
        immobilized.move(1, new Board.Move(43,44,"P"));
        check(immobilized.winner() == 1 && immobilized.count(2) == 1, "vitória por imobilização");
        Board owned = position(20,1,22,2,23,1,24,2,44,2);
        check(owned.move(1, new Board.Move(20,21,"A")) == 1, "captura para na peça aliada");
        rejects(() -> new Board().move(1, new Board.Move(0,22,"A")), "não move peça adversária");
        Board invalidMode = new Board();
        Board.Move valid = invalidMode.legalMoves().get(0);
        rejects(() -> invalidMode.move(1, new Board.Move(valid.from(),valid.to(),"X")), "modo desconhecido");
        // Percorre partidas legais com sementes fixas e verifica conservação das peças.
        for (int seed = 0; seed < 12; seed++) {
            Board game = new Board(); Random random = new Random(seed);
            for (int step = 0; step < 300 && game.winner() == 0; step++) {
                List<Board.Move> options = game.legalMoves();
                if (options.isEmpty()) throw new AssertionError("Partida ativa sem movimentos");
                int player = game.turn(), own = game.count(player), enemy = game.count(3-player);
                int captured = game.move(player, options.get(random.nextInt(options.size())));
                if (game.count(player) != own || game.count(3-player) != enemy-captured)
                    throw new AssertionError("Contagem inconsistente em partida gerada");
            }
        }
        check(true, "conservação em 12 partidas com sementes fixas");
        check(Protocol.untext(Protocol.text("Olá | : ação")).equals("Olá | : ação"), "UTF-8 e delimitadores");
    }
    /** Cliente de teste real, com timeout para que falhas não deixem testes pendurados. */
    private static final class Peer implements AutoCloseable {
        final Socket socket; final BufferedReader in; final PrintWriter out;
        Peer(int port, String name) throws IOException {
            socket = new Socket("127.0.0.1", port); socket.setSoTimeout(3000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            send("HELLO|" + Protocol.text(name));
        }
        void send(String s) { out.println(s); }
        String until(String prefix) throws IOException {
            for (int i = 0; i < 30; i++) {
                String line = in.readLine();
                if (line == null) throw new EOFException(prefix);
                if (line.startsWith(prefix + "|")) return line;
            }
            throw new AssertionError("Resposta não recebida: " + prefix);
        }
        public void close() throws IOException { socket.close(); }
    }
    private static void network() throws Exception {
        try (FanoronaServer server = new FanoronaServer(0)) {
            Thread listener = new Thread(() -> { try { server.run(); } catch (IOException e) { throw new UncheckedIOException(e); } }); listener.start();
            // Um socket sem HELLO não pode impedir o pareamento de outros clientes.
            try (Socket silent = new Socket("127.0.0.1", server.port()); Peer a = new Peer(silent.getPort(), "José | A")) {
                a.until("INFO");
                try (Peer b = new Peer(server.port(), "B")) {
                    check(a.until("WELCOME").startsWith("WELCOME|1|"), "primeiro cliente é P1"); b.until("WELCOME");
                    String initial = a.until("STATE"); check(initial.equals(b.until("STATE")), "snapshots iniciais iguais");
                    b.send("MOVE|0|20|22|A"); check(b.until("ERROR").contains("|"), "rede rejeita turno incorreto");
                    a.send("MOVE|0|x|22|A"); a.until("ERROR");
                    a.send("CHAT|" + Protocol.text("Olá | rede: funcionando"));
                    check(a.until("CHAT").equals(b.until("CHAT")), "chat bidirecional e caracteres especiais");
                    String[] state = Protocol.parts(initial);
                    String[] move = state[5].split(";")[0].split(",");
                    a.send("MOVE|0|" + move[0] + "|" + move[1] + "|" + move[2]);
                    check(a.until("STATE").equals(b.until("STATE")), "jogada sincroniza os tabuleiros");
                    a.send("END_TURN|0"); a.until("ERROR");
                    a.send("FORFEIT"); check(a.until("OVER").equals(b.until("OVER")), "desistência gera mesmo resultado");
                    b.send("CHAT|" + Protocol.text("Fim!")); check(a.until("CHAT").equals(b.until("CHAT")), "chat após resultado");
                }
            }
            try (Peer c = new Peer(server.port(), "C")) {
                c.until("INFO");
                try (Peer d = new Peer(server.port(), "D")) {
                    c.until("STATE"); d.until("STATE");
                }
                check(c.until("OVER").startsWith("OVER|1|"), "EOF concede vitória");
                c.until("PEER_LEFT"); c.send("RESTART|0");
                check(Protocol.untext(Protocol.parts(c.until("ERROR"))[1]).contains("desconectou"), "reinício exige os dois jogadores conectados");
            }
            try (Peer e = new Peer(server.port(), "E")) {
                e.until("INFO");
                try (Peer f = new Peer(server.port(), "F")) {
                    e.until("STATE"); f.until("STATE"); e.send("DRAW"); f.until("INFO");
                    // A leitura acima pode consumir o INFO de espera; sincronizar pela oferta no próprio emissor.
                    e.until("INFO"); f.send("DRAW");
                    check(e.until("OVER").startsWith("OVER|0|"), "empate por acordo"); f.until("OVER");
                }
            }
            try (Peer g = new Peer(server.port(), "G")) {
                g.until("INFO");
                try (Peer h = new Peer(server.port(), "H")) {
                    g.until("STATE"); h.until("STATE");
                    try (Peer i = new Peer(server.port(), "I")) {
                        i.until("INFO");
                        try (Peer j = new Peer(server.port(), "J")) {
                            i.until("STATE"); j.until("STATE");
                            g.send("CHAT|" + Protocol.text("Somente GH")); g.until("CHAT"); h.until("CHAT");
                            i.socket.setSoTimeout(200);
                            try { i.in.readLine(); throw new AssertionError("Vazamento entre sessões"); }
                            catch (SocketTimeoutException expected) { checks++; }
                            i.socket.setSoTimeout(3000);
                            i.send("CHAT|" + Protocol.text("Somente IJ")); check(i.until("CHAT").equals(j.until("CHAT")), "segunda sessão continua ativa");
                            g.send("CHAT|@@@"); g.until("ERROR");
                            g.send("CHAT|" + Protocol.text("x".repeat(501))); g.until("ERROR");
                            g.send("CHAT|" + Protocol.text("Ainda conectado")); check(g.until("CHAT").equals(h.until("CHAT")), "validação de texto preserva conexão");
                        }
                    }
                }
            }
            // Os dois adversários podem preparar a demonstração, mesmo fora de sua vez.
            for (int player = 1; player <= 2; player++) {
                try (Peer first = new Peer(server.port(), "Demo P1")) {
                    first.until("INFO");
                    try (Peer second = new Peer(server.port(), "Demo P2")) {
                        first.until("STATE"); second.until("STATE");
                        finalDemo(first, second, player == 1 ? first : second, player == 1 ? second : first, player);
                    }
                }
            }
            server.close(); listener.join(3000); check(!listener.isAlive(), "servidor encerra accept");
        }
    }
    // Os clientes são emprestados: o método network fecha ambos com try-with-resources.
    private static void finalDemo(Peer first, Peer second, Peer requester, Peer opponent, int player) throws IOException {
        requester.send("CHAT|" + Protocol.text("/final explicado"));
        check(first.until("CHAT").equals(second.until("CHAT")), "texto que menciona /final continua chat");
        requester.send("CHAT|" + Protocol.text(" /FINAL "));
        String prepared = first.until("STATE");
        check(prepared.equals(second.until("STATE")), "demonstração sincronizada para P" + player);
        String[] state = Protocol.parts(prepared);
        check(state[1].equals("1") && state[2].equals(Integer.toString(player)) && state[3].equals("-1"), "revisão, vez e sequência da demonstração");
        check(state[4].charAt(20) == '0' + player && state[4].charAt(22) == '0' + 3 - player
            && state[4].chars().filter(c -> c != '0').count() == 2 && state[5].equals("20,21,A"), "posição permite captura final C3-D3");
        requester.send("MOVE|0|20|21|A"); requester.until("ERROR");
        requester.send("MOVE|1|20|21|A");
        check(first.until("STATE").equals(second.until("STATE")), "captura final sincronizada");
        String result = first.until("OVER");
        check(result.equals(second.until("OVER")) && result.startsWith("OVER|" + player + "|"), "vitória de quem solicitou /final");
        requester.send("CHAT|" + Protocol.text("/final"));
        check(Protocol.untext(Protocol.parts(requester.until("ERROR"))[1]).startsWith("Partida encerrada"), "comando não reabre partida encerrada");
        agreeRestart(first, second, requester, opponent, 2);
        check(first.until("RESTARTED").equals(second.until("RESTARTED")), "reinício confirmado para a dupla");
        String reset = first.until("STATE");
        check(reset.equals(second.until("STATE")), "reinício sincroniza os mesmos clientes");
        String[] resetState = Protocol.parts(reset);
        check(resetState[1].equals("3") && resetState[2].equals("1") && resetState[3].equals("-1")
            && resetState[4].equals(new Board().encode()), "tabuleiro inicial, P1 e nova revisão após vitória");
        requester.send("CHAT|" + Protocol.text("Mesmos jogadores"));
        check(first.until("CHAT").equals(second.until("CHAT")), "chat continua após reiniciar");
        requester.send("RESTART|2"); requester.until("ERROR");
        agreeRestart(first, second, requester, opponent, 3);
        first.until("RESTARTED"); second.until("RESTARTED");
        check(first.until("STATE").equals(second.until("STATE")), "reinício também funciona durante partida ativa");
        requester.send("FORFEIT");
        String resetResult = first.until("OVER");
        check(resetResult.equals(second.until("OVER")) && resetResult.endsWith("|0|0|0|0|0|0"), "estatísticas zeradas pelo reinício");
        agreeRestart(first, second, requester, opponent, 4);
        first.until("RESTARTED"); second.until("RESTARTED");
        first.until("STATE"); second.until("STATE");
        requester.send("RESTART|5");
        String cancelledOffer = Protocol.parts(first.until("RESTART_OFFER"))[1]; second.until("RESTART_OFFER");
        requester.send("CHAT|" + Protocol.text("/final"));
        check(first.until("RESTART_CANCELLED").equals(second.until("RESTART_CANCELLED")), "mudança de tabuleiro cancela oferta");
        first.until("STATE"); second.until("STATE");
        opponent.send("RESTART_REPLY|" + cancelledOffer + "|ACCEPT");
        check(Protocol.untext(Protocol.parts(opponent.until("ERROR"))[1]).contains("expirada"), "aceite após mudança não reinicia");
    }
    private static void agreeRestart(Peer first, Peer second, Peer requester, Peer opponent, long revision) throws IOException {
        requester.send("RESTART|" + revision);
        String offer = first.until("RESTART_OFFER");
        check(offer.equals(second.until("RESTART_OFFER")), "solicitação chega aos dois jogadores");
        String offerId = Protocol.parts(offer)[1];
        // Uma solicitação isolada não publica tabuleiro nem inicia outra partida.
        first.socket.setSoTimeout(200);
        try { first.in.readLine(); throw new AssertionError("Reinício sem consentimento"); }
        catch (SocketTimeoutException expected) { checks++; }
        finally { first.socket.setSoTimeout(3000); }
        requester.send("RESTART_REPLY|" + offerId + "|ACCEPT");
        check(Protocol.untext(Protocol.parts(requester.until("ERROR"))[1]).contains("adversário"), "solicitante não aceita a própria oferta");
        opponent.send("RESTART_REPLY|" + offerId + "|DECLINE");
        check(first.until("RESTART_CANCELLED").equals(second.until("RESTART_CANCELLED")), "recusa comunicada à dupla");
        opponent.send("RESTART_REPLY|" + offerId + "|ACCEPT");
        opponent.until("ERROR");
        requester.send("RESTART|" + revision);
        String next = first.until("RESTART_OFFER"); second.until("RESTART_OFFER");
        check(!Protocol.parts(next)[1].equals(offerId), "nova oferta possui ID diferente mesmo sem jogada");
        opponent.send("RESTART_REPLY|" + offerId + "|ACCEPT"); opponent.until("ERROR");
        opponent.send("RESTART_REPLY|" + Protocol.parts(next)[1] + "|ACCEPT");
    }
    public static void main(String[] args) throws Exception {
        rules(); network(); System.out.println("SUCESSO: " + checks + " verificações de regras e TCP.");
    }
}
