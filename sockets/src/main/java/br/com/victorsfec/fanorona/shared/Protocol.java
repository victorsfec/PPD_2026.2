package br.com.victorsfec.fanorona.shared;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Uma mensagem UTF-8 por linha. Textos em Base64 não interferem no separador '|'. */
public final class Protocol {
    public static final int PORT = 12345, MAX_LINE = 8192;
    private Protocol() { }
    // Base64 evita que nomes e chat sejam confundidos com separadores; não é criptografia.
    public static String text(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    public static String untext(String s) { return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8); }
    public static String[] parts(String s) { return s.split("\\|", -1); }
    // TCP transmite um fluxo: o fim de linha delimita cada mensagem neste protocolo.
    public static String readLine(Reader reader) throws IOException {
        StringBuilder s = new StringBuilder(); int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') return s.toString();
            if (c != '\r') s.append((char)c);
            // O limite impede que uma mensagem sem fim de linha cresça indefinidamente.
            if (s.length() > MAX_LINE) throw new IOException("Mensagem excede o limite");
        }
        return s.length() == 0 ? null : s.toString();
    }
    public static String userText(String encoded, int max) {
        String s = untext(encoded).strip();
        if (s.isEmpty() || s.length() > max || s.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Texto vazio, muito longo ou com caracteres de controle.");
        return s;
    }
}
