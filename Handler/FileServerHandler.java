package Handler;

import Process.Peer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Accepts incoming TCP connections on the peer’s server socket
 * and spawns a {@link MsgHandler} (passive-mode = 0) for each one.
 */
public class FileServerHandler implements Runnable {

    private final ServerSocket serverSocket;
    private final String       selfId;

    public FileServerHandler(ServerSocket serverSocket, String peerId) {
        this.serverSocket = serverSocket;
        this.selfId       = peerId;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                /* block until another peer connects */
                Socket client = serverSocket.accept();

                /* hand the socket to a new message-handler thread */
                Thread t = new Thread(new MsgHandler(selfId, /* PASSIVE = */ 0, client));
                t.start();

                /* remember the socket so the rest of the peer logic can use it */
                Peer.peerToSocketMap.putIfAbsent(client.getInetAddress().getHostAddress(), client);

            } catch (IOException e) {
                // socket closed → exit loop
                break;
            }
        }
    }
}
