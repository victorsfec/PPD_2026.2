package br.com.victorsfec.fanorona.client;

import br.com.victorsfec.fanorona.game.Board;
import br.com.victorsfec.fanorona.shared.Protocol;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.stream.Collectors;

/** Renderização da própria interface Swing para revisão visual, sem partida simulada na rede. */
public final class UiRender {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FanoronaClient client = new FanoronaClient();
            GameFrame frame = new GameFrame(client);
            try {
                frame.receive("WELCOME|1|" + Protocol.text("Jogador 1") + "|" + Protocol.text("Jogador 2"));
                Board board = new Board();
                frame.receive("STATE|0|1|-1|" + board.encode() + "|" + board.legalMoves().stream().map(Board.Move::wire).collect(Collectors.joining(";")));
                frame.receive("CHAT|2|" + Protocol.text("Olá! Vamos começar a partida."));
                // Argumento opcional: ID do solicitante para revisar os controles de consentimento.
                if (args.length > 1) frame.receive("RESTART_OFFER|1|" + args[1]);
                // Largura e altura opcionais permitem conferir o tabuleiro em telas maiores.
                if (args.length > 3) frame.setSize(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                frame.addNotify(); frame.validate();
                BufferedImage image = new BufferedImage(frame.getContentPane().getWidth(), frame.getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics(); frame.getContentPane().printAll(g); g.dispose();
                ImageIO.write(image, "png", new File(args[0]));
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { client.close(); frame.dispose(); }
        });
    }
}
