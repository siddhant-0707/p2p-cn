package Handler;

import Logging.Helper;
import Msgs.Constants;
import Msgs.Handshake;
import Metadata.MsgMetadata;
import Msgs.Msg;
import Queue.MsgQueue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

public class MsgHandler implements Runnable {
    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private Handshake handshakeMessage;
    public String peerId;
    public String connectedPeerId;
    private final int connectionMode;

    public MsgHandler(String peerId, int connectionMode, String address, int port) throws IOException {
        this.peerId = peerId;
        this.connectionMode = connectionMode;
        this.socket = new Socket(address, port);
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    public MsgHandler(String peerId, int connectionMode, Socket socket) throws IOException {
        this.socket = socket;
        this.connectionMode = connectionMode;
        this.peerId = peerId;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    public boolean sendHandshake() throws IOException {
        Handshake handshake = new Handshake(Constants.HANDSHAKE_HEADER, this.peerId);
        outputStream.write(handshake.serialize());
        return true;
    }

    public void receiveHandshake() throws Exception {
        byte[] handshakeBuffer = new byte[Constants.HANDSHAKE_MESSAGE_LENGTH];
        while (inputStream.read(handshakeBuffer) > 0) {
            handshakeMessage = Handshake.deserialize(handshakeBuffer);
            if (handshakeMessage.getHeader().equals(Constants.HANDSHAKE_HEADER)) {
                connectedPeerId = handshakeMessage.getPeerID();
                Helper.writeLog(peerId + " connected to " + connectedPeerId);
                Helper.writeLog(peerId + " received handshake from " + connectedPeerId);
                break;
            }
        }
    }

    public void managePassiveConnection() throws Exception {
        receiveHandshake();
        if (sendHandshake()) {
            Helper.writeLog(peerId + " sent handshake successfully.");
        } else {
            Helper.writeLog(peerId + " handshake failed.");
            System.exit(-1);
        }
    }

    public void processIncomingMessages() throws IOException {
        byte[] messageLengthBuffer = new byte[Constants.MESSAGE_LENGTH];
        byte[] messageTypeBuffer = new byte[Constants.MESSAGE_TYPE];
        byte[] initialBuffer = new byte[Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE];
        MsgMetadata metadata = new MsgMetadata();
        while (inputStream.read(initialBuffer) != -1) {
            System.arraycopy(initialBuffer, 0, messageLengthBuffer, 0, Constants.MESSAGE_LENGTH);
            System.arraycopy(initialBuffer, Constants.MESSAGE_LENGTH, messageTypeBuffer, 0, Constants.MESSAGE_TYPE);
            Msg receivedMsg = new Msg();
            receivedMsg.setMessageLength(messageLengthBuffer);
            receivedMsg.setMessageType(messageTypeBuffer);
            
            metadata.setMessage(receivedMsg);
            metadata.setSenderId(connectedPeerId);
            MsgQueue.addMessage(metadata);
        }
    }

    @Override
    public void run() {
        try {
            if (connectionMode == Constants.ACTIVE_CONNECTION) {
                if (sendHandshake()) {
                    Helper.writeLog(peerId + " handshake sent.");
                    receiveHandshake();
                } else {
                    Helper.writeLog(peerId + " handshake failed.");
                    System.exit(-1);
                }
            } else {
                managePassiveConnection();
            }
            processIncomingMessages();
        } catch (Exception e) {
            System.out.println(Arrays.toString(e.getStackTrace()));
        }
    }
}
