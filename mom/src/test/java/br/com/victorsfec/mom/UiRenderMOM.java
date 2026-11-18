package br.com.victorsfec.mom;

import br.com.victorsfec.mom.broker.MomBroker;
import br.com.victorsfec.mom.client.*;
import br.com.victorsfec.mom.dashboard.DashboardFrameMOM;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Renderiza os componentes da aplicação em imagens, sem capturar o desktop. */
public final class UiRenderMOM {
    /** Exporta para PNG o conteúdo Swing já preparado para exibição. */
    private static void render(JFrame frame, Path target) throws Exception {
        try {
            frame.addNotify(); frame.validate();
            // Pinta o conteúdo Swing diretamente: JFrame invisível não pinta sua área nativa.
            var content = frame.getContentPane();
            BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            content.printAll(graphics); graphics.dispose();
            ImageIO.write(image, "png", target.toFile());
        } finally { frame.dispose(); }
    }
    /** Gera as imagens do cliente e do dashboard com os totais do arquivo de exemplo. */
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output);
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        String job = UUID.randomUUID().toString();
        try (MomBroker broker = new MomBroker("tcp://127.0.0.1:0");
             WorkflowMOM workflow = new WorkflowMOM(broker.url(), job, Path.of("examples/entrada.txt"), StandardCharsets.UTF_8,
                     List.of("java", "paralelo", "distribuída"), 3)) {
            workflow.execute();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    render(new ClientFrameMOM(), output.resolve("cliente.png"));
                    render(new DashboardFrameMOM(job, workflow.model()), output.resolve("dashboard.png"));
                } catch (Exception error) { throw new RuntimeException(error); }
            });
        }
        System.out.println("Interfaces renderizadas em " + output.toAbsolutePath());
    }
}
