package br.com.victorsfec.mom;

import br.com.victorsfec.mom.broker.MomBroker;
import br.com.victorsfec.mom.client.WorkflowMOM;
import br.com.victorsfec.mom.dashboard.*;
import br.com.victorsfec.mom.shared.ProtocolMOM;
import br.com.victorsfec.mom.worker.WordCounterMOM;
import jakarta.jms.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Testes sem framework, no mesmo padrão dos demais módulos do repositório. */
public final class AppTestMOM {
    private static int assertions;
    private static void check(boolean condition, String reason) {
        assertions++;
        if (!condition) throw new AssertionError(reason);
    }
    private static void equal(Object expected, Object actual, String reason) {
        check(Objects.equals(expected, actual), reason + ": esperado " + expected + ", recebido " + actual);
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static Path input(Path folder, String text) throws Exception {
        return Files.writeString(folder.resolve(id() + ".txt"), text, StandardCharsets.UTF_8);
    }
    /** Executa uma contagem JMS completa e devolve o estado final para comparação. */
    private static ResultModelMOM.Snapshot run(String url, Path file, List<String> words, int workers) throws Exception {
        try (WorkflowMOM workflow = new WorkflowMOM(url, id(), file, StandardCharsets.UTF_8, words, workers);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ResultModelMOM.Snapshot> future = executor.submit(workflow::execute);
            try { return future.get(45, TimeUnit.SECONDS); }
            finally { workflow.close(); }
        }
    }
    /** Confere as regras de contagem fora do broker, incluindo Unicode e palavras inteiras. */
    private static void counter() {
        var words = ProtocolMOM.keywords("JAVA; java\nAÇÃO,ação; açaí; _id;42");
        equal(List.of("java", "ação", "açaí", "_id", "42"), words, "Normalização de palavras");
        equal(List.of(3L, 2L, 1L, 1L, 1L), Arrays.stream(WordCounterMOM.count(
                "Java JAVA java! JavaScript java2 AÇÃO ac\u0327a\u0303o acao AÇAÍ _id 42 142", words)).boxed().toList(), "Palavras inteiras Unicode");
        try { ProtocolMOM.keywords("duas palavras"); throw new AssertionError("Frase aceita"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { ProtocolMOM.keywords(" , ;"); throw new AssertionError("Lista vazia aceita"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { ProtocolMOM.jobId("x' OR 1=1"); throw new AssertionError("ID inválido aceito"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    /** Simula um resultado de worker para controlar a ordem dos eventos no teste. */
    private static Message result(Session session, String job, long line, long count) throws Exception {
        Message event = session.createMessage();
        ProtocolMOM.identify(event, job, "RESULT");
        ProtocolMOM.putKeywords(event, List.of("java"));
        event.setLongProperty("line", line); event.setLongProperty("count0", count);
        event.setStringProperty("worker", "teste"); return event;
    }
    /** Simula o fim de um leitor com a quantidade de linhas publicada. */
    private static Message done(Session session, String job, int parity, long count) throws Exception {
        Message event = session.createMessage(); ProtocolMOM.identify(event, job, "READER_DONE");
        event.setIntProperty("parity", parity); event.setLongProperty("lines", count); return event;
    }
    /** Exercita a deduplicação e a condição de conclusão com eventos fora de ordem. */
    private static void aggregator(String url) throws Exception {
        String job = id(); ResultModelMOM model = new ResultModelMOM(job, List.of("java"));
        try (Connection connection = ProtocolMOM.connect(url); Session session = connection.createSession()) {
            model.receive(done(session, job, 0, 1)); model.receive(done(session, job, 1, 2));
            model.receive(result(session, job, 3, 7)); model.receive(result(session, job, 3, 7));
            model.receive(result(session, id(), 1, 1000));
            equal(1L, model.snapshot().processed(), "Duplicata e job estrangeiro ignorados");
            check(!model.snapshot().complete(), "EOF não encerra antes dos resultados");
            model.receive(result(session, job, 2, 2)); model.receive(result(session, job, 1, 4));
            equal(List.of(13L), model.snapshot().counts(), "Resultados fora de ordem");
            check(model.snapshot().complete(), "Conclusão após os dois leitores e todos os resultados");
            model.fail(new Exception("tardio")); check(model.snapshot().error() == null, "Conclusão preservada");
        }
    }
    /** Cobre tamanhos e paridades diferentes, além de um arquivo com vários lotes. */
    private static void fixtures(String url, Path folder) throws Exception {
        var words = List.of("java", "ação", "ausente");
        var empty = run(url, input(folder, ""), words, 3);
        equal(0L, empty.processed(), "Arquivo vazio"); equal(List.of(0L, 0L, 0L), empty.counts(), "Zero ocorrências");
        var one = run(url, input(folder, "JAVA JavaScript ação"), words, 1);
        equal(1L, one.expected(), "Uma linha sem newline final"); equal(List.of(1L, 1L, 0L), one.counts(), "Uma linha");
        var odd = run(url, input(folder, "java java\n\nAÇÃO\njava!\nação java"), words, 3);
        equal(5L, odd.processed(), "Linhas vazias e quantidade ímpar"); equal(List.of(4L, 2L, 0L), odd.counts(), "Paridades cobrem todo o arquivo");
        var even = run(url, input(folder, "java\nAÇÃO\njava\nação\n"), words, 2);
        equal(4L, even.expected(), "Quantidade par e newline final"); equal(List.of(2L, 2L, 0L), even.counts(), "Paridades pares");
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 3001; i++) big.append(i % 3 == 0 ? "java JAVA ação\n" : "JavaScript ação ação\n");
        var parallel = run(url, input(folder, big.toString()), words, 3);
        equal(3001L, parallel.processed(), "Streaming com múltiplos lotes");
        equal(List.of(2002L, 5001L, 0L), parallel.counts(), "Totais conhecidos do arquivo maior");
        check(parallel.workers().size() > 1, "Mais de um worker processou linhas");
    }
    /** Confere que execuções simultâneas mantêm seus totais separados. */
    private static void isolation(String url, Path folder) throws Exception {
        try (WorkflowMOM a = new WorkflowMOM(url, id(), input(folder, "java java\njava"), StandardCharsets.UTF_8, List.of("java"), 2);
             WorkflowMOM b = new WorkflowMOM(url, id(), input(folder, "java\nx"), StandardCharsets.UTF_8, List.of("java"), 2);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(a::execute); var second = executor.submit(b::execute);
            try {
                equal(List.of(3L), first.get(20, TimeUnit.SECONDS).counts(), "Isolamento A");
                equal(List.of(1L), second.get(20, TimeUnit.SECONDS).counts(), "Isolamento B");
            } finally { a.close(); b.close(); }
        }
    }
    /** Confere workers em JVMs distintas e dois assinantes para os mesmos resultados. */
    private static void externalWorkersAndSubscribers(String url, Path folder) throws Exception {
        String job = id(); List<String> words = List.of("java");
        ResultModelMOM subscriberA = new ResultModelMOM(job, words), subscriberB = new ResultModelMOM(job, words);
        List<Process> processes = new ArrayList<>();
        String executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        // Funciona tanto em java -cp quanto no classloader isolado do exec-maven-plugin.
        String classpath = Arrays.stream(new Class<?>[]{AppTestMOM.class, WorkflowMOM.class,
                        org.apache.activemq.ActiveMQConnectionFactory.class, org.apache.logging.log4j.LogManager.class,
                        org.apache.logging.log4j.core.LoggerContext.class})
                .map(type -> {
                    try { return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(); }
                    catch (Exception error) { throw new RuntimeException(error); }
                }).distinct().collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        try (Connection a = ProtocolMOM.connect(url); Connection b = ProtocolMOM.connect(url);
             WorkflowMOM workflow = new WorkflowMOM(url, job, input(folder, "java java\nx\njava"), StandardCharsets.UTF_8, words, 0);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            TopicSubscriberMOM.subscribe(a, job, subscriberA); TopicSubscriberMOM.subscribe(b, job, subscriberB);
            for (int i = 0; i < 3; i++) processes.add(new ProcessBuilder(executable, "-cp", classpath,
                    "br.com.victorsfec.mom.worker.WordWorkerMOM", url, job, "processo-" + i)
                    .redirectErrorStream(true).redirectOutput(folder.resolve("worker-" + i + ".log").toFile()).start());
            try {
                var result = executor.submit(workflow::execute).get(30, TimeUnit.SECONDS);
                equal(List.of(3L), result.counts(), "Workers em JVMs distintas");
                equal(result.counts(), subscriberA.completion().get(10, TimeUnit.SECONDS).counts(), "Pub/sub A");
                equal(result.counts(), subscriberB.completion().get(10, TimeUnit.SECONDS).counts(), "Pub/sub B");
                check(result.workers().keySet().stream().allMatch(name -> name.startsWith("processo-")), "Nenhum worker local");
            } finally { workflow.close(); }
        } finally {
            for (Process process : processes) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
        }
    }
    /** Verifica que uma linha não confirmada continua disponível para processamento. */
    private static void rollback(String url, Path folder) throws Exception {
        String job = id(); ResultModelMOM model = new ResultModelMOM(job, List.of("java"));
        try (Connection connection = ProtocolMOM.connect(url);
             Session send = connection.createSession();
             MessageProducer queue = send.createProducer(send.createQueue(ProtocolMOM.queue(job)));
             MessageProducer topic = send.createProducer(send.createTopic(ProtocolMOM.TOPIC))) {
            TopicSubscriberMOM.subscribe(connection, job, model);
            TextMessage line = send.createTextMessage("java java"); ProtocolMOM.identify(line, job, "LINE");
            ProtocolMOM.putKeywords(line, List.of("java")); line.setLongProperty("line", 1); queue.send(line);
            try (Session transaction = connection.createSession(true, Session.SESSION_TRANSACTED);
                 MessageConsumer consumer = transaction.createConsumer(transaction.createQueue(ProtocolMOM.queue(job)))) {
                check(consumer.receive(5000) != null, "Recepção transacional"); transaction.rollback();
            }
            topic.send(done(send, job, 0, 0)); topic.send(done(send, job, 1, 1));
            try (Connection worker = ProtocolMOM.connect(url); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var task = executor.submit(() -> {
                    try { br.com.victorsfec.mom.worker.WordWorkerMOM.consume(worker, job, "rollback", () -> model.snapshot().complete()); }
                    catch (Exception error) { model.fail(error); }
                });
                try { equal(List.of(2L), model.completion().get(15, TimeUnit.SECONDS).counts(), "Linha devolvida após rollback"); }
                finally { worker.close(); task.cancel(true); }
            }
        }
    }
    /** Cobre falhas de leitura, cancelamento e queda do broker durante a execução. */
    private static void failures(String url, Path folder) throws Exception {
        Path malformed = folder.resolve("invalido.txt"); Files.write(malformed, new byte[]{(byte) 0xc3, 0x28});
        try { run(url, malformed, List.of("java"), 1); throw new AssertionError("UTF-8 inválido aceito"); }
        catch (ExecutionException expected) { assertions++; }
        try (WorkflowMOM workflow = new WorkflowMOM(url, id(), input(folder, "java"), StandardCharsets.UTF_8, List.of("java"), 0);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var task = executor.submit(workflow::execute);
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (workflow.model().snapshot().readers() < 2 && System.nanoTime() < deadline) Thread.sleep(20);
            check(!workflow.model().snapshot().complete(), "Sem worker o trabalho permanece pendente");
            workflow.close();
            try { task.get(5, TimeUnit.SECONDS); throw new AssertionError("Cancelamento não encerrou"); }
            catch (ExecutionException expected) { assertions++; }
            check(workflow.model().snapshot().error() != null, "Cancelamento visível no dashboard");
        }
        MomBroker broker = new MomBroker("tcp://127.0.0.1:0");
        try (broker; WorkflowMOM workflow = new WorkflowMOM(broker.url(), id(), input(folder, "java"), StandardCharsets.UTF_8, List.of("java"), 0);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var task = executor.submit(workflow::execute);
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (workflow.model().snapshot().readers() < 2 && System.nanoTime() < deadline) Thread.sleep(20);
            broker.close();
            try { task.get(10, TimeUnit.SECONDS); throw new AssertionError("Queda do broker ignorada"); }
            catch (ExecutionException expected) { assertions++; }
            finally { workflow.close(); }
        }
    }
    /** Executa as verificações de integração com um broker em uma porta livre. */
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("mom-test-");
        try (MomBroker broker = new MomBroker("tcp://127.0.0.1:0")) {
            counter(); aggregator(broker.url()); fixtures(broker.url(), folder); isolation(broker.url(), folder);
            externalWorkersAndSubscribers(broker.url(), folder); rollback(broker.url(), folder); failures(broker.url(), folder);
            System.out.println("OK: " + assertions + " verificações; JMS/TCP, processos externos, pub/sub, rollback e falhas.");
        } finally {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
