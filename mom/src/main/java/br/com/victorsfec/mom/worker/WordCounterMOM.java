package br.com.victorsfec.mom.worker;

import br.com.victorsfec.mom.shared.ProtocolMOM;
import java.util.*;

/** Conta palavras inteiras, ignorando caixa e preservando os acentos. */
public final class WordCounterMOM {
    private WordCounterMOM() { }
    /** Conta os tokens de uma linha na mesma ordem das palavras pesquisadas. */
    public static long[] count(String line, List<String> words) {
        // O índice da palavra aponta diretamente para sua posição no vetor de contagens.
        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < words.size(); i++) positions.put(words.get(i), i);
        long[] counts = new long[words.size()];
        // A mesma normalização é aplicada à pesquisa e ao texto do arquivo.
        var matcher = ProtocolMOM.WORD.matcher(ProtocolMOM.normalize(line));
        while (matcher.find()) {
            Integer index = positions.get(matcher.group());
            if (index != null) counts[index]++;
        }
        return counts;
    }
}
