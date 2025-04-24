package Handler;

import Configs.SysConfig;
import Msgs.BitField;
import Msgs.Constants;
import Msgs.FilePiece;
import Msgs.Msg;
import Metadata.MsgMetadata;
import Metadata.PeerMetadata;
import Queue.MsgQueue;
import Process.Peer;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Set;

import static Logging.Helper.writeLog;

/**
 * Consumes messages from {@link Queue.MessageQueue} and takes the correct
 * protocol actions for this peer.
 */
public class MsgProcessingHandler implements Runnable {

    private final String selfId;

    public MsgProcessingHandler(String peerId) {
        this.selfId = peerId;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {

            while (MsgQueue.hasNext()) {

                /* dequeue next message */
                MsgMetadata meta = MsgQueue.getMessageFromQueue();
                Msg   msg        = meta.getMessage();
                String remoteId  = meta.getSenderId();
                String type      = msg.getType();

                PeerMetadata remote = Peer.remotePeerDetails.get(remoteId);
                int state = remote.getPeerState();          // numeric FSM state

                /* fast-path HAVE messages that trigger interest check          */
                if (Constants.HAVE.equals(type) && state != 14) {
                    processHaveOrInterestingPieces(msg, "", remoteId);
                    continue;
                }

                /* finite-state dispatch */
                switch (state) {
                    case 2:
                        processBitfieldHandshake(type, remoteId);
                        break;
                    case 3:
                        processInterestedOrNot(type, remoteId);
                        break;
                    case 4:
                        processFileRequest(msg, type, remoteId);
                        break;
                    case 8:
                        acknowledgeBitfield(msg, type, remoteId);
                        break;
                    case 9:
                        processChokeUnchoke(type, remoteId);
                        break;
                    case 11:
                        processReceivedPiece(msg, type, remoteId);
                        break;
                    case 14:
                        processHaveOrUnchoke(msg, type, remoteId);
                        break;
                    case 15:
                        handlePeerFinished(remoteId);
                        break;
                    default:
                        // optionally handle unknown states
                        break;
                }
                
            }
        }
    }

    /* ---------- state-specific helpers ---------- */

    private void processBitfieldHandshake(String type, String remoteId) {
        if (Constants.BITFIELD.equals(type)) {
            writeLog(selfId + " received BITFIELD from " + remoteId);
            sendBitfield(Peer.peerToSocketMap.get(remoteId), remoteId);
            Peer.remotePeerDetails.get(remoteId).setPeerState(3);
        }
    }

    private void processInterestedOrNot(String type, String remoteId) {
        if (Constants.INTERESTED.equals(type)) {
            handleInterested(remoteId);
        } else if (Constants.NOT_INTERESTED.equals(type)) {
            handleNotInterested(remoteId);
        }
    }

    /* file request from remote peer */
    private void processFileRequest(Msg msg, String type, String remoteId) {
        if (!Constants.REQUEST.equals(type)) return;

        sendPiece(Peer.peerToSocketMap.get(remoteId), msg, remoteId);
        broadcastDownloadCompleteIfNeeded();
        if (isNeitherPreferredNorOptimistic(remoteId)) {
            chokePeer(remoteId);
        }
    }

    /* acknowledge the remote’s BITFIELD (after we sent ours) */
    private void acknowledgeBitfield(Msg msg, String type, String remoteId) {
        if (Constants.BITFIELD.equals(type)) {
            if (isInteresting(msg, remoteId)) handleInterested(remoteId);
            else                              handleNotInterested(remoteId);
        }
    }

    private void processChokeUnchoke(String type, String remoteId) {
        if (Constants.CHOKE.equals(type)) {
            writeLog(selfId + " is CHOKED by " + remoteId);
            Peer.remotePeerDetails.get(remoteId).setChoked(true);
            Peer.remotePeerDetails.get(remoteId).setPeerState(14);
        } else if (Constants.UNCHOKE.equals(type)) {
            writeLog(selfId + " is UNCHOKED by " + remoteId);
            requestFirstMissingPiece(remoteId);
        }
    }

    private void processReceivedPiece(Msg msg, String type, String remoteId) {
        if (!Constants.PIECE.equals(type)) return;

        byte[] payload = msg.getPayload();
        updateDownloadRate(payload.length, remoteId);

        FilePiece piece = FilePiece.fromPayload(payload);
        Peer.bitFieldMessage.updateBitField(remoteId, piece);

        requestFirstMissingPiece(remoteId);
        Peer.updateOtherPeerMetadata();        // refresh status flags

        // notify others with HAVE
        for (String id : Peer.remotePeerDetails.keySet()) {
            if (!id.equals(Peer.peerID) && isPeerStillInterested(id)) {
                sendHave(Peer.peerToSocketMap.get(id));
                Peer.remotePeerDetails.get(id).setPeerState(3);
            }
        }

        broadcastDownloadCompleteIfNeeded();
    }

    private void processHaveOrInterestingPieces(Msg msg, String type, String remoteId) {
        writeLog(selfId + " got HAVE from " + remoteId);
        if (isInteresting(msg, remoteId)) handleInterested(remoteId);
        else                              handleNotInterested(remoteId);
    }

    private void processHaveOrUnchoke(Msg msg, String type, String remoteId) {
        if (Constants.HAVE.equals(type))       processHaveOrInterestingPieces(msg, type, remoteId);
        else if (Constants.UNCHOKE.equals(type)) requestFirstMissingPiece(remoteId);
    }

    private void handlePeerFinished(String remoteId) {
        writeLog(remoteId + " finished downloading.");
        int previous = Peer.remotePeerDetails.get(remoteId).getPreviousState();
        Peer.remotePeerDetails.get(remoteId).setPeerState(previous);
    }

    /* ---------- interest / choke logic ---------- */

    private void handleInterested(String remoteId) {
        writeLog(selfId + " got INTERESTED from " + remoteId);
        PeerMetadata remote = Peer.remotePeerDetails.get(remoteId);
        remote.setInterested(true);
        remote.setHandshaked(true);

        if (isNeitherPreferredNorOptimistic(remoteId)) {
            chokePeer(remoteId);
        } else {
            unchokePeer(remoteId);
        }
    }

    private void handleNotInterested(String remoteId) {
        writeLog(selfId + " got NOT_INTERESTED from " + remoteId);
        PeerMetadata remote = Peer.remotePeerDetails.get(remoteId);
        remote.setInterested(false);
        remote.setHandshaked(true);
        remote.setPeerState(5);
    }

    private boolean isNeitherPreferredNorOptimistic(String id) {
        return !Peer.preferredNeighbours.containsKey(id) &&
               !Peer.optimisticUnchoked.containsKey(id);
    }

    /* ---------- piece selection ---------- */

    private void requestFirstMissingPiece(String remoteId) {
        int idx = Peer.bitFieldMessage.findFirstMissingPiece(
                Peer.remotePeerDetails.get(remoteId).getBitField());
        if (idx == -1) {
            Peer.remotePeerDetails.get(remoteId).setPeerState(13);
            return;
        }

        sendRequest(Peer.peerToSocketMap.get(remoteId), idx, remoteId);
        Peer.remotePeerDetails.get(remoteId).setPeerState(11);
        Peer.remotePeerDetails.get(remoteId).setStartTime(new Date());
    }

    /* ---------- messaging primitives ---------- */

    private void chokePeer(String remoteId) {
        sendChoke(Peer.peerToSocketMap.get(remoteId), remoteId);
        Peer.remotePeerDetails.get(remoteId).setChoked(true);
        Peer.remotePeerDetails.get(remoteId).setPeerState(6);
    }

    private void unchokePeer(String remoteId) {
        sendUnchoke(Peer.peerToSocketMap.get(remoteId), remoteId);
        Peer.remotePeerDetails.get(remoteId).setChoked(false);
        Peer.remotePeerDetails.get(remoteId).setPeerState(4);
    }

    /* ---------- helper predicates ---------- */

    private boolean isPeerStillInterested(String id) {
        PeerMetadata pm = Peer.remotePeerDetails.get(id);
        return !pm.hasCompletedFile() && !pm.isChoked() && pm.isInterested();
    }

    private boolean isInteresting(Msg msg, String remoteId) {
        BitField remoteBF = BitField.decodeBitField(msg.getPayload());
        Peer.remotePeerDetails.get(remoteId).setBitField(remoteBF);
        int idx = Peer.bitFieldMessage.findFirstMissingPiece(remoteBF);
        if (idx != -1 && Constants.HAVE.equals(msg.getType())) {
            writeLog(selfId + " remote " + remoteId + " has piece " + idx);
        }
        return idx != -1;
    }

    /* ---------- bandwidth measurement ---------- */

    private void updateDownloadRate(long payloadBytes, String remoteId) {
        PeerMetadata pm = Peer.remotePeerDetails.get(remoteId);
        pm.setEndTime(new Date());

        long elapsed = pm.getEndTime().getTime() - pm.getStartTime().getTime();
        if (elapsed == 0) elapsed = 1;

        double rate = ((double) (payloadBytes + Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE) / elapsed) * 1000;
        pm.setDataRate(rate);
    }

    /* ---------- send-helpers (build & socket) ---------- */

    private void sendBitfield(Socket sock, String remoteId) {
        writeLog(selfId + " → BITFIELD → " + remoteId);
        sendControlMsg(sock, new Msg(Constants.BITFIELD, Peer.bitFieldMessage.encodeBitField()));
    }

    private void sendHave(Socket sock) {
        sendControlMsg(sock, new Msg(Constants.HAVE, Peer.bitFieldMessage.encodeBitField()));
    }

    private void sendChoke(Socket sock, String remoteId) {
        writeLog(selfId + " → CHOKE → " + remoteId);
        sendControlMsg(sock, new Msg(Constants.CHOKE));
    }

    private void sendUnchoke(Socket sock, String remoteId) {
        writeLog(selfId + " → UNCHOKE → " + remoteId);
        sendControlMsg(sock, new Msg(Constants.UNCHOKE));
    }

    private void sendRequest(Socket sock, int pieceIdx, String remoteId) {
        writeLog(selfId + " REQUEST piece " + pieceIdx + " → " + remoteId);
        sendControlMsg(sock, new Msg(Constants.REQUEST, ByteBuffer.allocate(4).putInt(pieceIdx).array()));
    }

    private void sendPiece(Socket sock, Msg request, String remoteId) {
        int idx = ByteBuffer.wrap(request.getPayload()).getInt();
        writeLog(selfId + " sending PIECE " + idx + " → " + remoteId);

        byte[] buf = new byte[SysConfig.pieceSize];
        int read;
        File file = new File(Peer.peerFolder, SysConfig.fileName);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek((long) idx * SysConfig.pieceSize);
            read = raf.read(buf, 0, SysConfig.pieceSize);
        } catch (IOException e) { return; }

        byte[] payload = new byte[read + Constants.PIECE_INDEX_LENGTH];
        System.arraycopy(request.getPayload(), 0, payload, 0, Constants.PIECE_INDEX_LENGTH);
        System.arraycopy(buf, 0, payload, Constants.PIECE_INDEX_LENGTH, read);

        sendControlMsg(sock, new Msg(Constants.PIECE, payload));
    }

    private void broadcastDownloadCompleteIfNeeded() {
        if (Peer.isFirstPeer || !Peer.bitFieldMessage.isDownloadComplete()) return;

        for (String id : Peer.remotePeerDetails.keySet()) {
            if (id.equals(Peer.peerID)) continue;
            Socket s = Peer.peerToSocketMap.get(id);
            if (s != null) sendControlMsg(s, new Msg(Constants.MESSAGE_DOWNLOADED));
        }
    }

    private void sendControlMsg(Socket socket, Msg msg) {
        if (socket == null) return;
        try (OutputStream out = socket.getOutputStream()) {
            out.write(Msg.serializeMessage(msg));
        } catch (Exception ignored) {}
    }
}
