package br.com.victorsfec.mom.client;

import br.com.victorsfec.mom.dashboard.*;
import br.com.victorsfec.mom.reader.LineReaderMOM;
import br.com.victorsfec.mom.shared.ProtocolMOM;
import br.com.victorsfec.mom.worker.WordWorkerMOM;
import jakarta.jms.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Orquestra a execução; todos os dados entre os estágios passam pelo broker JMS. */
public final class WorkflowMOM implements AutoCloseable {
    private final String url, job;
    private final Path file;
    private final Charset charset;
    private final List<String> words;
    private final int workerCount;
    private final ResultModelMOM model;
    // Cada leitor e cada worker local executa em sua própria thread virtual.
    private final ExecutorService tasks = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("mom-", 0).factory());
    private final List<Connection> connections = new ArrayList<>();
    private Connection subscriberConnection;
    private volatile boolean closed;
    private boolean started;

    /** Valida o arquivo e prepara o estado de uma única execução. */
    public WorkflowMOM(String url, String job, Path file, Charset charset, List<String> words, int workerCount) {
        if (workerCount < 0 || workerCount > 32) throw new IllegalArgumentException("Use de 0 a 32 workers.");
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) throw new IllegalArgumentException("Arquivo inexistente ou sem permissão de leitura.");
        this.url = url; this.job = ProtocolMOM.jobId(job); this.file = file;
        this.charset = charset; this.words = List.copyOf(words); this.workerCount = workerCount;
        model = new ResultModelMOM(job, words);
    }
    /** Expõe o modelo que a interface usa para consultar os totais. */
    public ResultModelMOM model() { return model; }

    /** Registra cada conexão para fechá-la ao encerrar a execução. */
    private Connection connect() throws JMSException {
        Connection connection = ProtocolMOM.connect(url);
        synchronized (connections) {
            if (closed) { connection.close(); throw new JMSException("Execução encerrada."); }
            connections.add(connection);
        }
        connection.setExceptionListener(model::fail);
        return connection;
    }
    /** Agenda uma tarefa JMS e encaminha falhas ao modelo de resultados. */
    private void submit(CheckedTask task) {
        tasks.submit(() -> { try { task.run(); } catch (Exception error) { if (!closed) model.fail(error); } });
    }
    @FunctionalInterface private interface CheckedTask { void run() throws Exception; }

    /** Aguarda a contagem fora da EDT. Com zero workers locais, usa processos externos. */
    public ResultModelMOM.Snapshot execute() throws Exception {
        try { return executeInternal(); }
        catch (Exception error) { model.fail(error); throw error; }
    }
    /** Prepara os participantes e aguarda o sinal de conclusão do modelo. */
    private ResultModelMOM.Snapshot executeInternal() throws Exception {
        synchronized (this) {
            if (started || closed) throw new java.lang.IllegalStateException("Workflow já iniciado ou encerrado.");
            started = true;
        }
        // O tópico tem assinatura não durável. O dashboard assina antes da primeira publicação.
        subscriberConnection = connect();
        TopicSubscriberMOM.subscribe(subscriberConnection, job, model);
        for (int i = 1; i <= workerCount; i++) {
            String name = "worker-" + i;
            Connection connection = connect();
            submit(() -> WordWorkerMOM.consume(connection, job, name, () -> closed));
        }
        // Exatamente duas execuções de leitura; cada uma possui conexão e sessão próprias.
        for (int parity = 0; parity <= 1; parity++) {
            int selection = parity;
            Connection connection = connect();
            submit(() -> LineReaderMOM.produce(connection, file, charset, job, selection, words, () -> closed));
        }
        // Esperas curtas permitem detectar inatividade sem tratar um único segundo como falha.
        while (true) {
            try { return model.completion().get(1, TimeUnit.SECONDS); }
            catch (TimeoutException waiting) {
                if (model.idleNanos() > TimeUnit.SECONDS.toNanos(120))
                    model.fail(new TimeoutException("120 segundos sem eventos. Verifique o broker e os workers."));
            } catch (ExecutionException failure) {
                if (failure.getCause() instanceof Exception error) throw error;
                throw failure;
            }
        }
    }
    /** Transfere a conexão do assinante ao dashboard após uma contagem bem-sucedida. */
    Connection takeSubscriberConnection() {
        synchronized (connections) {
            if (closed || !connections.remove(subscriberConnection)) return null;
            Connection connection = subscriberConnection;
            subscriberConnection = null;
            return connection;
        }
    }

    /** Encerra as tarefas e conexões uma única vez, inclusive no cancelamento. */
    @Override public void close() {
        List<Connection> active;
        synchronized (connections) {
            if (closed) return;
            closed = true;
            active = List.copyOf(connections);
        }
        model.fail(new CancellationException("Execução cancelada."));
        tasks.shutdownNow();
        for (Connection connection : active) {
            try { connection.close(); } catch (JMSException ignored) { }
        }
        try { tasks.awaitTermination(15, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
