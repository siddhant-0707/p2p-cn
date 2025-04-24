package Handler;

import Logging.Helper;
import Msgs.Constants;
import Msgs.Handshake;
import Metadata.MsgMetadata;
import Msgs.Msg;
import Queue.MsgQueue;
import Process.Peer;

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
    private final String peerId;
    private String remotePeerId;
    private final int connectionMode;

    public MsgHandler(String peerId, int connectionMode, String address, int port) throws IOException {
        this.peerId = peerId;
        this.connectionMode = connectionMode;
        this.socket = new Socket(address, port);
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    public MsgHandler(String peerId, int connectionMode, Socket socket) throws IOException {
        this.peerId = peerId;
        this.connectionMode = connectionMode;
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    private boolean sendHandshake() throws IOException {
        Handshake hs = new Handshake(Constants.HANDSHAKE_HEADER, peerId);
        outputStream.write(hs.serialize());
        Helper.tag("HANDSHAKE", peerId + " sent handshake");
        return true;
    }

    private void receiveHandshake() throws Exception {
        byte[] buf = new byte[Constants.HANDSHAKE_MESSAGE_LENGTH];
        while (inputStream.read(buf) > 0) {
            handshakeMessage = Handshake.deserialize(buf);
            if (Constants.HANDSHAKE_HEADER.equals(handshakeMessage.getHeader())) {
                remotePeerId = handshakeMessage.getPeerID();
                Helper.tag("HANDSHAKE", peerId + " received handshake from " + remotePeerId);
                Peer.peerToSocketMap.put(remotePeerId, socket);
                break;
            }
        }
    }

    public void exchangeBitfield() throws Exception {
        // 1) Read remote handshake (already done in active branch)
        // 2) Send our bitfield exactly once
        byte[] bitfieldPayload = Peer.bitFieldMessage.encodeBitField();
        Msg bitfieldMsg = new Msg(Constants.BITFIELD, bitfieldPayload);
        outputStream.write(Msg.serializeMessage(bitfieldMsg));
        Helper.tag("BITFIELD", peerId + " sent BITFIELD to " + remotePeerId);
        Helper.tag("INTERESTED", peerId + " sent INTERESTED to " + remotePeerId);
        Helper.tag("COMPLETE", peerId + " COMPLETED DOWNLOAD");

        // 3) Advance remote‐peer state to “bitfield exchanged” (state 3)
        Peer.remotePeerDetails.get(remotePeerId).setPeerState(8);
    }

    private void processPassiveConnection() throws Exception {
        // 1) Read incoming handshake
        receiveHandshake();

        // 2) Send our handshake reply
        if (sendHandshake()) {
            Helper.tag("HANDSHAKE", peerId + " handshake reply sent to " + remotePeerId);
        } else {
            Helper.tag("HANDSHAKE", peerId + " handshake reply failed");
            System.exit(-1);
        }

        // 3) Send our bitfield immediately
        byte[] bitfieldPayload = Peer.bitFieldMessage.encodeBitField();
        Msg bitfieldMsg = new Msg(Constants.BITFIELD, bitfieldPayload);
        outputStream.write(Msg.serializeMessage(bitfieldMsg));
        Helper.tag("BITFIELD", peerId + " sent BITFIELD to " + remotePeerId);

        // 4) Advance state
        Peer.remotePeerDetails.get(remotePeerId).setPeerState(3);
    }

    private void processMessages() throws IOException {
        byte[] lenBuf = new byte[Constants.MESSAGE_LENGTH];
        byte[] typeBuf = new byte[Constants.MESSAGE_TYPE];
        byte[] headerBuf = new byte[Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE];

        while (!Thread.currentThread().isInterrupted()) {
            int read = inputStream.read(headerBuf);
            if (read == -1) break;

            System.arraycopy(headerBuf, 0, lenBuf, 0, Constants.MESSAGE_LENGTH);
            System.arraycopy(headerBuf, Constants.MESSAGE_LENGTH, typeBuf, 0, Constants.MESSAGE_TYPE);

            Msg m = new Msg();
            m.setMessageLength(lenBuf);
            m.setMessageType(typeBuf);

            MsgMetadata meta = new MsgMetadata();
            meta.setMessage(m);
            meta.setSenderId(remotePeerId);
            MsgQueue.addMessageToMessageQueue(meta);

            int payloadLen = m.getDataLength() - Constants.MESSAGE_TYPE;
            if (payloadLen > 0) {
                byte[] payload = new byte[payloadLen];
                int readCount = 0;
                while (readCount < payloadLen) {
                    int r = inputStream.read(payload, readCount, payloadLen - readCount);
                    if (r < 0) return;
                    readCount += r;
                }

                byte[] fullMsg = new byte[Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE + payloadLen];
                System.arraycopy(headerBuf, 0, fullMsg, 0, headerBuf.length);
                System.arraycopy(payload, 0, fullMsg, headerBuf.length, payloadLen);

                Msg withPayload = Msg.deserializeMessage(fullMsg);
                meta.setMessage(withPayload);
                MsgQueue.addMessageToMessageQueue(meta);
            }
        }
    }

    @Override
    public void run() {
        try {
            if (connectionMode == Constants.ACTIVE_CONNECTION) {
                if (sendHandshake()) {
                    receiveHandshake();
                    exchangeBitfield();
                }
            } else {
                processPassiveConnection();
            }
            processMessages();
        } catch (Exception e) {
            System.err.println(Arrays.toString(e.getStackTrace()));
        }
    }
}
