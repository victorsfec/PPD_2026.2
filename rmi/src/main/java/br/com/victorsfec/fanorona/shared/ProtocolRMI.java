package br.com.victorsfec.fanorona.shared;


import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Formato local dos eventos retornados por RMI. Textos em Base64 não interferem no separador '|'. */
public final class ProtocolRMI {
    public static final int PORT = 12345;
    private ProtocolRMI() { }
    // Base64 evita que nomes e chat sejam confundidos com separadores; não é criptografia.
    public static String text(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    public static String untext(String s) { return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8); }
    public static String[] parts(String s) { return s.split("\\|", -1); }
    public static String userText(String encoded, int max) {
        String s = untext(encoded).strip();
        if (s.isEmpty() || s.length() > max || s.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Texto vazio, muito longo ou com caracteres de controle.");
        return s;
    }
}
