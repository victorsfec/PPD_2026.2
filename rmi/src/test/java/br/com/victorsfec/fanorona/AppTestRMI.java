package br.com.victorsfec.fanorona;

import br.com.victorsfec.fanorona.game.BoardRMI;
import br.com.victorsfec.fanorona.server.FanoronaServerRMI;
import br.com.victorsfec.fanorona.shared.ProtocolRMI;
import java.io.*;

import java.util.*;

/** Testes sem bibliotecas externas: falhas lançam AssertionError e interrompem o build. */
public final class AppTestRMI {
    private static int checks;
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
    }
    private static void rejects(Runnable action, String name) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(name);
    }
    private static BoardRMI position(int... values) {
        int[] cells = new int[45];
        for (int i = 0; i < values.length; i += 2) cells[values[i]] = values[i + 1];
        return new BoardRMI(cells, 1);
    }
    private static void rules() {
        BoardRMI start = new BoardRMI();
        check(start.count(1) == 22 && start.count(2) == 22 && start.encode().charAt(22) == '0', "22 peças e centro vazio");
        check(start.turn() == 1, "P1 inicia");
        check(BoardRMI.connected(0, 10) && !BoardRMI.connected(1, 11) && !BoardRMI.connected(8, 9), "linhas e bordas");
        check(start.legalMoves().stream().noneMatch(m -> m.mode().equals("P")), "captura inicial obrigatória");
        String before = start.encode();
        rejects(() -> start.move(2, start.legalMoves().get(0)), "fora de turno");
        rejects(() -> start.move(1, new BoardRMI.Move(-1, 80, "A")), "coordenadas inválidas");
        check(before.equals(start.encode()), "rejeição atômica");
        BoardRMI approach = position(18,1,20,2,21,2,23,2);
        check(approach.move(1,new BoardRMI.Move(18,19,"A")) == 2 && approach.count(2) == 1, "aproximação para no vazio");
        BoardRMI withdrawal = position(20,1,19,2,18,2,44,2);
        check(withdrawal.move(1,new BoardRMI.Move(20,21,"W")) == 2, "afastamento captura linha");
        BoardRMI both = position(20,1,19,2,22,2,44,2);
        check(both.legalMoves().containsAll(List.of(new BoardRMI.Move(20,21,"A"), new BoardRMI.Move(20,21,"W"))), "escolha dos modos");
        both.move(1,new BoardRMI.Move(20,21,"A"));
        check(both.encode().charAt(19) == '2' && both.encode().charAt(22) == '0', "não captura os dois lados");
        BoardRMI sequence = position(20,1,22,2,12,2,44,2);
        sequence.move(1,new BoardRMI.Move(20,21,"A"));
        check(sequence.chain() == 21 && sequence.turn() == 1, "continuidade com a mesma peça");
        check(sequence.legalMoves().stream().allMatch(m -> m.from() == 21 && m.to() != 20), "não revisita origem");
        rejects(() -> sequence.move(1,new BoardRMI.Move(21,22,"P")), "sequência não permite movimento simples");
        sequence.move(1,new BoardRMI.Move(21,30,"W"));
        check(sequence.count(2) == 1, "segunda captura");
        BoardRMI optional = position(20,1,22,2,12,2,44,2);
        optional.move(1,new BoardRMI.Move(20,21,"A")); optional.endTurn(1);
        check(optional.turn() == 2 && optional.chain() == -1, "fim opcional da sequência");
        rejects(() -> new BoardRMI().endTurn(1), "não permite passar turno");
        BoardRMI direction = position(20,1,19,2,23,2,12,2,44,2);
        direction.move(1,new BoardRMI.Move(20,21,"W"));
        check(direction.legalMoves().stream().noneMatch(m -> m.to() == 22), "não repete direção consecutiva");
        BoardRMI simple = position(0,1,44,2);
        simple.move(1,new BoardRMI.Move(0,1,"P")); check(simple.turn() == 2, "movimento simples troca turno");
        BoardRMI win = position(18,1,20,2);
        win.move(1,new BoardRMI.Move(18,19,"A")); check(win.winner() == 1, "vitória por eliminação");
        rejects(() -> win.move(1,new BoardRMI.Move(19,18,"P")), "partida encerrada");
        int[] blocked = new int[45]; Arrays.fill(blocked, 1); blocked[0] = 2; blocked[44] = 0;
        BoardRMI immobilized = new BoardRMI(blocked, 1);
        immobilized.move(1, new BoardRMI.Move(43,44,"P"));
        check(immobilized.winner() == 1 && immobilized.count(2) == 1, "vitória por imobilização");
        BoardRMI owned = position(20,1,22,2,23,1,24,2,44,2);
        check(owned.move(1, new BoardRMI.Move(20,21,"A")) == 1, "captura para na peça aliada");
        rejects(() -> new BoardRMI().move(1, new BoardRMI.Move(0,22,"A")), "não move peça adversária");
        BoardRMI invalidMode = new BoardRMI();
        BoardRMI.Move valid = invalidMode.legalMoves().get(0);
        rejects(() -> invalidMode.move(1, new BoardRMI.Move(valid.from(),valid.to(),"X")), "modo desconhecido");
        // Percorre partidas legais com sementes fixas e verifica conservação das peças.
        for (int seed = 0; seed < 12; seed++) {
            BoardRMI game = new BoardRMI(); Random random = new Random(seed);
            for (int step = 0; step < 300 && game.winner() == 0; step++) {
                List<BoardRMI.Move> options = game.legalMoves();
                if (options.isEmpty()) throw new AssertionError("Partida ativa sem movimentos");
                int player = game.turn(), own = game.count(player), enemy = game.count(3-player);
                int captured = game.move(player, options.get(random.nextInt(options.size())));
                if (game.count(player) != own || game.count(3-player) != enemy-captured)
                    throw new AssertionError("Contagem inconsistente em partida gerada");
            }
        }
        check(true, "conservação em 12 partidas com sementes fixas");
        check(ProtocolRMI.untext(ProtocolRMI.text("Olá | : ação")).equals("Olá | : ação"), "UTF-8 e delimitadores");
    }
    private static final class Peer implements AutoCloseable {
        final br.com.victorsfec.fanorona.shared.FanoronaServiceRMI service;
        final String token;
        final Deque<String> events = new ArrayDeque<>();
        Peer(int port, String name) throws Exception {
            service = (br.com.victorsfec.fanorona.shared.FanoronaServiceRMI) java.rmi.registry.LocateRegistry.getRegistry("127.0.0.1", port).lookup("Fanorona");
            token = service.connect(name);
        }
        void send(String s) throws IOException { br.com.victorsfec.fanorona.client.FanoronaClientRMI.invoke(service, token, s); }
        String until(String prefix) throws IOException {
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < deadline) {
                events.addAll(service.poll(token));
                while (!events.isEmpty()) { String line = events.remove(); if (line.startsWith(prefix + "|")) return line; }
                try { Thread.sleep(10); } catch (InterruptedException e) { throw new IOException(e); }
            }
            throw new AssertionError("Resposta não recebida: " + prefix);
        }
        public void close() throws IOException { service.disconnect(token); }
    }
    private static void network() throws Exception {
        int port = 23456;
        try (FanoronaServerRMI server = new FanoronaServerRMI(port)) {
            for (int player = 1; player <= 2; player++) {
                try (Peer a = new Peer(port, "José | A"); Peer b = new Peer(port, "B")) {
                    check(a.until("WELCOME").startsWith("WELCOME|1|"), "primeiro cliente é P1");
                    check(b.until("WELCOME").startsWith("WELCOME|2|"), "segundo cliente é P2");
                    String initial = a.until("STATE"); check(initial.equals(b.until("STATE")), "estados iguais");
                    b.send("MOVE|0|20|22|A"); b.until("ERROR");
                    String[] move = ProtocolRMI.parts(initial)[5].split(";")[0].split(",");
                    a.send("MOVE|0|"+move[0]+"|"+move[1]+"|"+move[2]);
                    check(a.until("STATE").equals(b.until("STATE")), "jogada RMI sincronizada");
                    a.send("END_TURN|0"); a.until("ERROR");
                    a.send("FORFEIT"); a.until("OVER"); b.until("OVER");
                    a.send("RESTART|1"); String offer = ProtocolRMI.parts(a.until("RESTART_OFFER"))[1]; b.until("RESTART_OFFER");
                    b.send("RESTART_REPLY|"+offer+"|ACCEPT"); a.until("STATE"); b.until("STATE");
                }
                try (Peer a = new Peer(port, "Demo A"); Peer b = new Peer(port, "Demo B")) {
                    a.until("STATE"); b.until("STATE");
                    finalDemo(a, b, player == 1 ? a : b, player == 1 ? b : a, player);
                }
            }
            try (Peer a = new Peer(port, "C")) {
                a.until("INFO");
                try (Peer b = new Peer(port, "D")) { a.until("STATE"); b.until("STATE"); }
                check(a.until("OVER").startsWith("OVER|1|"), "desconexão concede vitória"); a.until("PEER_LEFT");
                a.send("RESTART|0"); a.until("ERROR");
            }
            try (Peer a = new Peer(port, "E"); Peer b = new Peer(port, "F")) {
                a.until("STATE"); b.until("STATE");
                a.send("DRAW"); a.until("INFO"); b.send("DRAW");
                check(a.until("OVER").startsWith("OVER|0|"), "empate por acordo"); b.until("OVER");
                try (Peer c = new Peer(port, "G"); Peer d = new Peer(port, "H")) {
                    c.until("STATE"); d.until("STATE");
                    a.send("CHAT|"+ProtocolRMI.text("Olá | RMI")); check(a.until("CHAT").equals(b.until("CHAT")), "chat após resultado");
                    check(c.service.poll(c.token).isEmpty(), "partidas isoladas");
                    c.send("CHAT|"+ProtocolRMI.text("x".repeat(501))); c.until("ERROR");
                    c.send("CHAT|"+ProtocolRMI.text("ok")); check(c.until("CHAT").equals(d.until("CHAT")), "validação preserva sessão");
                    try { c.service.poll("token-inválido"); throw new AssertionError("Token inválido aceito"); }
                    catch (java.rmi.RemoteException expected) { checks++; }
                }
            }
            try (Peer survivor = new Peer(port, "Ativo");
                 Peer abandoned = new Peer(port, "Encerrado abruptamente")) {
                survivor.until("STATE"); abandoned.until("STATE");
                // Apenas o sobrevivente continua consultando: simula processo encerrado sem disconnect.
                boolean detected = false;
                long deadline = System.nanoTime() + 19_000_000_000L;
                while (System.nanoTime() < deadline && !detected) {
                    for (String event : survivor.service.poll(survivor.token))
                        if (event.startsWith("OVER|1|")) detected = true;
                    Thread.sleep(100);
                }
                check(detected, "expiração detecta encerramento abrupto e concede vitória");
                try { abandoned.service.poll(abandoned.token); throw new AssertionError("Sessão expirada aceita"); }
                catch (java.rmi.RemoteException expected) { checks++; }
            }
            server.close();
        }
        try (FanoronaServerRMI restarted = new FanoronaServerRMI(port); Peer a = new Peer(restarted.port(), "Reaberto")) { a.until("INFO"); checks++; }
    }
    // Os clientes são emprestados: o método network fecha ambos com try-with-resources.
    private static void finalDemo(Peer first, Peer second, Peer requester, Peer opponent, int player) throws IOException {
        requester.send("CHAT|" + ProtocolRMI.text("/final explicado"));
        check(first.until("CHAT").equals(second.until("CHAT")), "texto que menciona /final continua chat");
        requester.send("CHAT|" + ProtocolRMI.text(" /FINAL "));
        String prepared = first.until("STATE");
        check(prepared.equals(second.until("STATE")), "demonstração sincronizada para P" + player);
        String[] state = ProtocolRMI.parts(prepared);
        check(state[1].equals("1") && state[2].equals(Integer.toString(player)) && state[3].equals("-1"), "revisão, vez e sequência da demonstração");
        check(state[4].charAt(20) == '0' + player && state[4].charAt(22) == '0' + 3 - player
            && state[4].chars().filter(c -> c != '0').count() == 2 && state[5].equals("20,21,A"), "posição permite captura final C3-D3");
        requester.send("MOVE|0|20|21|A"); requester.until("ERROR");
        requester.send("MOVE|1|20|21|A");
        check(first.until("STATE").equals(second.until("STATE")), "captura final sincronizada");
        String result = first.until("OVER");
        check(result.equals(second.until("OVER")) && result.startsWith("OVER|" + player + "|"), "vitória de quem solicitou /final");
        requester.send("CHAT|" + ProtocolRMI.text("/final"));
        check(ProtocolRMI.untext(ProtocolRMI.parts(requester.until("ERROR"))[1]).startsWith("Partida encerrada"), "comando não reabre partida encerrada");
        agreeRestart(first, second, requester, opponent, 2);
        check(first.until("RESTARTED").equals(second.until("RESTARTED")), "reinício confirmado para a dupla");
        String reset = first.until("STATE");
        check(reset.equals(second.until("STATE")), "reinício sincroniza os mesmos clientes");
        String[] resetState = ProtocolRMI.parts(reset);
        check(resetState[1].equals("3") && resetState[2].equals("1") && resetState[3].equals("-1")
            && resetState[4].equals(new BoardRMI().encode()), "tabuleiro inicial, P1 e nova revisão após vitória");
        requester.send("CHAT|" + ProtocolRMI.text("Mesmos jogadores"));
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
        String cancelledOffer = ProtocolRMI.parts(first.until("RESTART_OFFER"))[1]; second.until("RESTART_OFFER");
        requester.send("CHAT|" + ProtocolRMI.text("/final"));
        check(first.until("RESTART_CANCELLED").equals(second.until("RESTART_CANCELLED")), "mudança de tabuleiro cancela oferta");
        first.until("STATE"); second.until("STATE");
        opponent.send("RESTART_REPLY|" + cancelledOffer + "|ACCEPT");
        check(ProtocolRMI.untext(ProtocolRMI.parts(opponent.until("ERROR"))[1]).contains("expirada"), "aceite após mudança não reinicia");
    }
    private static void agreeRestart(Peer first, Peer second, Peer requester, Peer opponent, long revision) throws IOException {
        requester.send("RESTART|" + revision);
        String offer = first.until("RESTART_OFFER");
        check(offer.equals(second.until("RESTART_OFFER")), "solicitação chega aos dois jogadores");
        String offerId = ProtocolRMI.parts(offer)[1];
        // Uma solicitação isolada não publica tabuleiro nem inicia outra partida.
        check(first.service.poll(first.token).isEmpty(), "reinício aguarda consentimento");
        requester.send("RESTART_REPLY|" + offerId + "|ACCEPT");
        check(ProtocolRMI.untext(ProtocolRMI.parts(requester.until("ERROR"))[1]).contains("adversário"), "solicitante não aceita a própria oferta");
        opponent.send("RESTART_REPLY|" + offerId + "|DECLINE");
        check(first.until("RESTART_CANCELLED").equals(second.until("RESTART_CANCELLED")), "recusa comunicada à dupla");
        opponent.send("RESTART_REPLY|" + offerId + "|ACCEPT");
        opponent.until("ERROR");
        requester.send("RESTART|" + revision);
        String next = first.until("RESTART_OFFER"); second.until("RESTART_OFFER");
        check(!ProtocolRMI.parts(next)[1].equals(offerId), "nova oferta possui ID diferente mesmo sem jogada");
        opponent.send("RESTART_REPLY|" + offerId + "|ACCEPT"); opponent.until("ERROR");
        opponent.send("RESTART_REPLY|" + ProtocolRMI.parts(next)[1] + "|ACCEPT");
    }
    public static void main(String[] args) throws Exception {
        rules(); network(); System.out.println("SUCESSO: " + checks + " verificações de regras e RMI.");
    }
}
