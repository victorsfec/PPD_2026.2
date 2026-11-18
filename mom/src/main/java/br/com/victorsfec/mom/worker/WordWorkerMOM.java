package br.com.victorsfec.mom.worker;

import br.com.victorsfec.mom.shared.ProtocolMOM;
import jakarta.jms.*;
import java.util.function.BooleanSupplier;

/** Consumidores competem na fila; consumo e publicação pertencem à mesma transação. */
public final class WordWorkerMOM {
    private WordWorkerMOM() { }
    /** Retira linhas da fila da execução, conta as palavras e publica os resultados no tópico. */
    public static void consume(Connection connection, String job, String name, BooleanSupplier stopped) throws Exception {
        try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
             MessageConsumer consumer = session.createConsumer(session.createQueue(ProtocolMOM.queue(job)));
             MessageProducer publisher = session.createProducer(session.createTopic(ProtocolMOM.TOPIC))) {
            publisher.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            connection.start();
            while (!stopped.getAsBoolean() && !Thread.currentThread().isInterrupted()) {
                // O tempo de espera permite verificar periodicamente o pedido de parada.
                Message input = consumer.receive(500);
                if (input == null) continue;
                try {
                    if (!(input instanceof TextMessage text) || !job.equals(input.getStringProperty("jobId")))
                        throw new JMSException("Mensagem de linha inválida.");
                    var words = ProtocolMOM.getKeywords(input);
                    long[] counts = WordCounterMOM.count(text.getText(), words);
                    Message output = session.createMessage();
                    ProtocolMOM.identify(output, job, "RESULT");
                    ProtocolMOM.putKeywords(output, words);
                    output.setLongProperty("line", input.getLongProperty("line"));
                    output.setStringProperty("worker", name);
                    // countN corresponde a wordN, inclusive quando a palavra não aparece.
                    for (int i = 0; i < counts.length; i++) output.setLongProperty("count" + i, counts[i]);
                    publisher.send(output);
                    // O commit confirma a retirada da linha e a publicação do resultado juntos.
                    session.commit();
                // O rollback permite ao broker disponibilizar a linha novamente.
                } catch (Exception error) { session.rollback(); throw error; }
            }
        }
    }
    /** Executa um worker independente, conectado por TCP à fila do UUID informado. */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Uso: worker.jar URL_BROKER UUID [NOME]");
        String job = ProtocolMOM.jobId(args[1]);
        try (Connection connection = ProtocolMOM.connect(args[0])) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { connection.close(); } catch (JMSException ignored) { }
            }));
            System.out.println("Worker ativo para " + job + ". Ctrl+C encerra.");
            consume(connection, job, args.length > 2 ? args[2] : "externo", () -> false);
        }
    }
}
