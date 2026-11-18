package br.com.victorsfec.mom.shared;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Contrato JMS compartilhado por produtores, workers e subscribers. */
public final class ProtocolMOM {
    public static final String URL = "tcp://localhost:61616";
    // Todos os jobs usam este tópico. O seletor distingue as execuções pelo UUID.
    public static final String TOPIC = "mom.palavras";
    // Letras, marcas Unicode, dígitos e sublinhado formam um token pesquisável.
    public static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}_]+");
    private ProtocolMOM() { }

    /** Padroniza caixa e composição Unicode, preservando acentos. */
    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
    /** Valida as palavras digitadas e remove repetições mantendo a ordem de entrada. */
    public static List<String> keywords(String input) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String part : input.split("[,;\\r\\n]+")) {
            String word = normalize(part.strip());
            if (word.isEmpty()) continue;
            if (!WORD.matcher(word).matches()) throw new IllegalArgumentException("Informe palavras isoladas: " + part);
            result.add(word);
        }
        if (result.isEmpty() || result.size() > 100) throw new IllegalArgumentException("Informe de 1 a 100 palavras-chave.");
        return List.copyOf(result);
    }
    /** Converte o identificador para a forma canônica de UUID. */
    public static String jobId(String value) { return UUID.fromString(value).toString(); }

    /** Define uma fila de linhas exclusiva para a execução. */
    public static String queue(String job) { return "mom.linhas." + jobId(job); }

    /** Filtra os eventos do tópico para entregar ao subscriber só os do seu UUID. */
    public static String selector(String job) { return "jobId = '" + jobId(job) + "'"; }

    /** Cria a conexão JMS/TCP e configura os limites de espera do cliente. */
    public static Connection connect(String url) throws JMSException {
        if (!url.startsWith("tcp://")) throw new IllegalArgumentException("Use uma URL tcp://host:porta.");
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
        factory.setConnectResponseTimeout(5000);
        factory.setSendTimeout(10000);
        // Uma linha antecipada por consumidor reduz a concentração de trabalho.
        factory.getPrefetchPolicy().setQueuePrefetch(1);
        return factory.createConnection();
    }
    /** Registra a execução e o tipo do evento nas propriedades JMS. */
    public static void identify(Message message, String job, String kind) throws JMSException {
        message.setStringProperty("jobId", jobId(job));
        message.setStringProperty("kind", kind);
    }
    /** Grava as palavras em propriedades indexadas, mantendo sua ordem. */
    public static void putKeywords(Message message, List<String> words) throws JMSException {
        message.setIntProperty("keywordCount", words.size());
        for (int i = 0; i < words.size(); i++) message.setStringProperty("word" + i, words.get(i));
    }
    /** Reconstrói a lista ordenada de palavras a partir das propriedades JMS. */
    public static List<String> getKeywords(Message message) throws JMSException {
        int count = message.getIntProperty("keywordCount");
        if (count < 1 || count > 100) throw new JMSException("Lista de palavras inválida.");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(message.getStringProperty("word" + i));
        return List.copyOf(result);
    }
}
