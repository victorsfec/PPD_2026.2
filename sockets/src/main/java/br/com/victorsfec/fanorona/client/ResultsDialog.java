package br.com.victorsfec.fanorona.client;

import javax.swing.*;
import br.com.victorsfec.fanorona.shared.Protocol;

/** Resultado confirmado pelo servidor e estatísticas da partida. */
final class ResultsDialog {
    private ResultsDialog() { }
    static void show(JFrame owner, String[] p, String result) {
        JOptionPane.showMessageDialog(owner, result + "\n" + Protocol.untext(p[2])
            + "\n\n                         P1 / P2"
            + "\nMovimentos:       " + p[3] + " / " + p[4]
            + "\nPeças capturadas: " + p[5] + " / " + p[6]
            + "\nComandos inválidos: " + p[7] + " / " + p[8], "Resultado da partida", JOptionPane.INFORMATION_MESSAGE);
    }
}
