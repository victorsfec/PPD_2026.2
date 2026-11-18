package br.com.victorsfec.mom.dashboard;

import br.com.victorsfec.mom.shared.ProtocolMOM;
import jakarta.jms.*;

/** Recebe eventos do tópico e entrega ao modelo apenas os da execução assinada. */
public final class TopicSubscriberMOM {
    private TopicSubscriberMOM() { }
    /** Registra o seletor e o listener antes de iniciar a entrega JMS. */
    public static void subscribe(Connection connection, String job, ResultModelMOM model) throws JMSException {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer subscriber = session.createConsumer(session.createTopic(ProtocolMOM.TOPIC), ProtocolMOM.selector(job));
        // O callback roda em uma thread JMS e atualiza o modelo, sem operar a interface Swing.
        subscriber.setMessageListener(message -> {
            try { model.receive(message); } catch (Exception error) { model.fail(error); }
        });
        connection.setExceptionListener(model::fail);
        connection.start();
        // Sessão e consumer permanecem abertos até a conexão ser fechada pelo proprietário.
    }
}
