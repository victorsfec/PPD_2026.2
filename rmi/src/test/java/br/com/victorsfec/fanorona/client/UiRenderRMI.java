package br.com.victorsfec.fanorona.client;

import br.com.victorsfec.fanorona.game.BoardRMI;
import br.com.victorsfec.fanorona.shared.ProtocolRMI;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.stream.Collectors;

/** Renderização da própria interface Swing para revisão visual, sem partida simulada na rede. */
public final class UiRenderRMI {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FanoronaClientRMI client = new FanoronaClientRMI();
            GameFrameRMI frame = new GameFrameRMI(client);
            try {
                int viewer = args.length > 4 ? Integer.parseInt(args[4]) : 1;
                frame.receive("WELCOME|" + viewer + "|" + ProtocolRMI.text("Jogador 1") + "|" + ProtocolRMI.text("Jogador 2"));
                BoardRMI board = new BoardRMI();
                if (args.length > 5) board = new BoardRMI(board.encode().chars().map(c -> c - '0').toArray(), Integer.parseInt(args[5]));
                frame.receive("STATE|0|" + board.turn() + "|-1|" + board.encode() + "|" + board.legalMoves().stream().map(BoardRMI.Move::wire).collect(Collectors.joining(";")));
                frame.receive("CHAT|2|" + ProtocolRMI.text("Olá! Vamos começar a partida."));
                // Argumento opcional: ID do solicitante para revisar os controles de consentimento.
                if (args.length > 1 && !args[1].equals("0")) frame.receive("RESTART_OFFER|1|" + args[1]);
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
