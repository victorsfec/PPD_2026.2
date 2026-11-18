package br.com.victorsfec.mom.client;

import br.com.victorsfec.mom.broker.MomBroker;
import br.com.victorsfec.mom.shared.ProtocolMOM;
import javax.swing.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;

/** Ponto de entrada do cliente gráfico e do modo console. */
public final class MomClient {
    private MomClient() { }
    /** Sem argumentos, abre o Swing. No console, executa o mesmo fluxo JMS. */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.invokeLater(() -> new ClientFrameMOM().setVisible(true));
            return;
        }
        if (Arrays.asList(args).contains("--help")) {
            System.out.println("Sem argumentos: interface Swing.\nConsole: --console --file ARQUIVO --words 'java,paralelo' [--workers 3] [--local] [--broker tcp://localhost:61616] [--charset UTF-8] [--job UUID]");
            return;
        }
        // As opções com valor descrevem a execução. As flags ativam o modo escolhido.
        Map<String, String> options = new HashMap<>();
        Set<String> flags = new HashSet<>();
        Set<String> valued = Set.of("--file", "--words", "--workers", "--broker", "--charset", "--job");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--console") || args[i].equals("--local")) flags.add(args[i]);
            else if (valued.contains(args[i]) && i + 1 < args.length) options.put(args[i], args[++i]);
            else throw new IllegalArgumentException("Argumento desconhecido ou sem valor: " + args[i]);
        }
        if (!flags.contains("--console") || !options.containsKey("--file") || !options.containsKey("--words"))
            throw new IllegalArgumentException("Consulte --help para executar no console.");
        String job = ProtocolMOM.jobId(options.getOrDefault("--job", UUID.randomUUID().toString()));
        String url = options.getOrDefault("--broker", ProtocolMOM.URL);
        if (flags.contains("--local") && !url.equals(ProtocolMOM.URL))
            throw new IllegalArgumentException("--local utiliza tcp://localhost:61616. Remova --broker ou --local.");
        // O console fecha o workflow e o broker local mesmo se a contagem falhar.
        try (MomBroker _ = flags.contains("--local") ? new MomBroker("tcp://127.0.0.1:61616") : null;
             WorkflowMOM workflow = new WorkflowMOM(url, job, Path.of(options.get("--file")),
                     Charset.forName(options.getOrDefault("--charset", "UTF-8")), ProtocolMOM.keywords(options.get("--words")),
                     Integer.parseInt(options.getOrDefault("--workers", "3")))) {
            Runtime.getRuntime().addShutdownHook(new Thread(workflow::close));
            System.out.println("Execução: " + job);
            var result = workflow.execute();
            for (int i = 0; i < result.words().size(); i++) System.out.println(result.words().get(i) + " = " + result.counts().get(i));
            System.out.println("Linhas: " + result.processed() + "/" + result.expected());
            System.out.println("Workers: " + result.workers());
        }
    }
}
