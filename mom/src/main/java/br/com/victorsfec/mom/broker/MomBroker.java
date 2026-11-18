package br.com.victorsfec.mom.broker;

import org.apache.activemq.broker.BrokerService;

/** Broker ActiveMQ real, acessível por TCP também em processos separados. */
public final class MomBroker implements AutoCloseable {
    private final BrokerService broker = new BrokerService();
    /** Configura os limites de recursos e inicia o broker no endereço TCP informado. */
    public MomBroker(String bind) throws Exception {
        broker.setBrokerName("mom-" + java.util.UUID.randomUUID());
        // Uma reinicialização descarta o estado das execuções desta demonstração.
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.setSchedulerSupport(false);
        // Fila e tópico compartilham o orçamento de memória do broker.
        broker.getSystemUsage().getMemoryUsage().setLimit(64L * 1024 * 1024);
        broker.getSystemUsage().getTempUsage().setLimit(512L * 1024 * 1024);
        broker.getSystemUsage().setSendFailIfNoSpaceAfterTimeout(10000);
        try {
            broker.addConnector(bind);
            broker.start();
            broker.waitUntilStarted();
        } catch (Exception error) { close(); throw error; }
    }
    /** Retorna o endereço efetivo, inclusive quando o sistema escolhe a porta. */
    public String url() throws Exception { return broker.getTransportConnectors().getFirst().getPublishableConnectString(); }

    /** Para o broker e aguarda a liberação de seus recursos. */
    @Override public void close() throws Exception { broker.stop(); broker.waitUntilStopped(); }

    /** Executa o broker em uma JVM independente até o encerramento pelo operador. */
    public static void main(String[] args) throws Exception {
        String bind = args.length == 0 ? "tcp://127.0.0.1:61616" : args[0];
        MomBroker broker = new MomBroker(bind);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { broker.close(); } catch (Exception e) { System.err.println(e.getMessage()); }
        }));
        System.out.println("Broker MOM ativo em " + broker.url() + ". Ctrl+C encerra.");
        new java.util.concurrent.CountDownLatch(1).await();
    }
}
