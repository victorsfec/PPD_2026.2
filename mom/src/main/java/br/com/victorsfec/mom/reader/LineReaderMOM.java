package br.com.victorsfec.mom.reader;

import br.com.victorsfec.mom.shared.ProtocolMOM;
import jakarta.jms.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Cada execução percorre o arquivo em streaming e publica somente sua paridade. */
public final class LineReaderMOM {
    private LineReaderMOM() { }
    /** Percorre o arquivo linha a linha e envia à fila as linhas da paridade indicada. */
    public static void produce(Connection connection, Path file, Charset charset, String job,
                               int parity, List<String> words, BooleanSupplier stopped) throws Exception {
        if (parity != 0 && parity != 1) throw new IllegalArgumentException("Paridade inválida.");
        try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
             MessageProducer lines = session.createProducer(session.createQueue(ProtocolMOM.queue(job)));
             MessageProducer events = session.createProducer(session.createTopic(ProtocolMOM.TOPIC));
             var reader = Files.newBufferedReader(file, charset)) {
            lines.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            events.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            long number = 0, sent = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (stopped.getAsBoolean() || Thread.currentThread().isInterrupted())
                    throw new InterruptedException("Leitura cancelada.");
                // Numera a partir de 1. A paridade 0 seleciona as pares e a 1, as ímpares.
                number++;
                if (number % 2 != parity) continue;
                TextMessage message = session.createTextMessage(line);
                ProtocolMOM.identify(message, job, "LINE");
                message.setLongProperty("line", number);
                // O worker recebe no próprio evento a lista de palavras que deve procurar.
                ProtocolMOM.putKeywords(message, words);
                lines.send(message);
                sent++;
                // O commit a cada 100 linhas libera cada lote para consumo durante a leitura.
                if (sent % 100 == 0) session.commit();
            }
            // O EOF informa quantas linhas este leitor publicou, inclusive zero.
            Message done = session.createMessage();
            ProtocolMOM.identify(done, job, "READER_DONE");
            done.setIntProperty("parity", parity);
            done.setLongProperty("lines", sent);
            events.send(done);
            session.commit();
        }
    }
}
