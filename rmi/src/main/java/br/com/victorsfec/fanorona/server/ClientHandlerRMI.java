package br.com.victorsfec.fanorona.server;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/** Identidade e caixa de eventos RMI. Não abre sockets nem threads de transporte. */
final class ClientHandlerRMI {
    private final FanoronaServerRMI server;
    private final ArrayBlockingQueue<String> outgoing = new ArrayBlockingQueue<>(256);
    volatile GameSessionRMI session;
    final String playerName;
    volatile long lastSeen = System.nanoTime();
    ClientHandlerRMI(FanoronaServerRMI server, String name) { this.server = server; playerName = name; }
    void send(String message) { if (!outgoing.offer(message)) server.expire(this); }
    List<String> drain() { List<String> events = new ArrayList<>(); outgoing.drainTo(events); return events; }
}
