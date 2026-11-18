package br.com.victorsfec.mom.dashboard;

import br.com.victorsfec.mom.shared.ProtocolMOM;
import jakarta.jms.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Agregação independente de Swing; o dashboard só soma eventos recebidos do tópico. */
public final class ResultModelMOM {
    /** Cópia do estado para exibição. Total esperado igual a -1 indica leitura em andamento. */
    public record Snapshot(List<String> words, List<Long> counts, long processed, long expected,
                           int readers, Map<String, Long> workers, boolean complete, String error) { }
    private final String job;
    private final List<String> words;
    private final long[] counts;
    private final Long[] readerLines = new Long[2];
    // Um bit por linha; blocos esparsos evitam guardar o texto ou objetos por mensagem.
    private final Map<Long, BitSet> seen = new HashMap<>();
    private final Map<String, Long> workers = new TreeMap<>();
    private long processed, lastActivity = System.nanoTime();
    private String error;
    private boolean complete;
    private final CompletableFuture<Snapshot> completion = new CompletableFuture<>();

    /** Inicia os totais em zero e fixa a ordem das palavras desta execução. */
    public ResultModelMOM(String job, List<String> words) {
        this.job = ProtocolMOM.jobId(job);
        this.words = List.copyOf(words);
        counts = new long[words.size()];
    }
    /** Permite ao workflow aguardar o resultado final ou a falha da execução. */
    public CompletableFuture<Snapshot> completion() { return completion; }

    /** Mede o tempo desde o último evento útil recebido da execução. */
    public synchronized long idleNanos() { return System.nanoTime() - lastActivity; }

    /** Registra a primeira falha e libera quem estiver aguardando a contagem. */
    public synchronized void fail(Throwable cause) {
        if (complete || error != null) return;
        error = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        completion.completeExceptionally(cause);
    }
    /** Valida e agrega um resultado ou registra o término de um leitor. */
    public synchronized void receive(Message event) throws JMSException {
        if (!job.equals(event.getStringProperty("jobId")) || error != null || complete) return;
        switch (event.getStringProperty("kind")) {
            case "RESULT" -> {
                if (!words.equals(ProtocolMOM.getKeywords(event))) throw new JMSException("Palavras incompatíveis no resultado.");
                long line = event.getLongProperty("line");
                if (line < 1) throw new JMSException("Número de linha inválido.");
                long[] incoming = new long[counts.length];
                for (int i = 0; i < counts.length; i++) {
                    incoming[i] = event.getLongProperty("count" + i);
                    if (incoming[i] < 0) throw new JMSException("Contagem negativa.");
                }
                // O número da linha identifica a contribuição, mesmo se ela chegar fora de ordem.
                BitSet block = seen.computeIfAbsent(line >>> 16, unused -> new BitSet());
                int bit = (int) (line & 65535);
                if (block.get(bit)) return;
                block.set(bit);
                for (int i = 0; i < counts.length; i++) counts[i] = Math.addExact(counts[i], incoming[i]);
                processed++;
                workers.compute(event.getStringProperty("worker"),
                        (_, previous) -> previous == null ? 1L : previous + 1L);
            }
            case "READER_DONE" -> {
                int parity = event.getIntProperty("parity");
                long lines = event.getLongProperty("lines");
                if (parity < 0 || parity > 1 || lines < 0) throw new JMSException("Final de leitura inválido.");
                if (readerLines[parity] != null && readerLines[parity] != lines)
                    throw new JMSException("Totais de leitura incompatíveis.");
                readerLines[parity] = lines;
            }
            default -> throw new JMSException("Evento desconhecido.");
        }
        lastActivity = System.nanoTime();
        // EOF encerra a produção. O trabalho só termina quando todos os resultados chegam.
        if (readerLines[0] != null && readerLines[1] != null) {
            long total = Math.addExact(readerLines[0], readerLines[1]);
            if (processed > total) throw new JMSException("Recebidos mais resultados que linhas publicadas.");
            if (processed == total) { complete = true; completion.complete(snapshot()); }
        }
    }
    /** Copia os totais sob o mesmo bloqueio usado pelo listener JMS. */
    public synchronized Snapshot snapshot() {
        List<Long> values = new ArrayList<>();
        for (long count : counts) values.add(count);
        int readers = (readerLines[0] == null ? 0 : 1) + (readerLines[1] == null ? 0 : 1);
        long expected = readers == 2 ? readerLines[0] + readerLines[1] : -1;
        return new Snapshot(words, List.copyOf(values), processed, expected, readers,
                Collections.unmodifiableMap(new TreeMap<>(workers)), complete, error);
    }
}
