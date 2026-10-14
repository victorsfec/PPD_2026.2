package br.com.victorsfec.fanorona.shared;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/** Interface pública de operações remotas; o token identifica a sessão do jogador. */
public interface FanoronaServiceRMI extends Remote {
    String NAME = "Fanorona";
    String connect(String name) throws RemoteException;
    List<String> poll(String token) throws RemoteException;
    void move(String token, long revision, int from, int to, String mode) throws RemoteException;
    void endTurn(String token, long revision) throws RemoteException;
    void chat(String token, String text) throws RemoteException;
    void forfeit(String token) throws RemoteException;
    void draw(String token) throws RemoteException;
    void restart(String token, long revision) throws RemoteException;
    void replyRestart(String token, long offer, boolean accept) throws RemoteException;
    void disconnect(String token) throws RemoteException;
}
