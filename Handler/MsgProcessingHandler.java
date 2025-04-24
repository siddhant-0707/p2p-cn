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
 * Consumes messages from {@link Queue.MsgQueue} and takes the correct
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
                MsgMetadata meta     = MsgQueue.getMessageFromQueue();
                Msg         msg      = meta.getMessage();
                String      remoteId = meta.getSenderId();
                String      type     = msg.getType();

                PeerMetadata remote = Peer.remotePeerDetails.get(remoteId);
                int state = remote.getPeerState();

                // fast‐path HAVEs to trigger interest check
                if (Constants.HAVE.equals(type) && state != 14) {
                    processHaveOrInterestingPieces(msg, "", remoteId);
                    continue;
                }

                // FSM dispatch
                switch (state) {
                    case 2:
                        processBitfieldHandshake(msg, remoteId);
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
                        break;
                }
            }
        }
    }

    /**
     * State 2: We just received their BITFIELD; store it, reply our BITFIELD,
     * then immediately decide and send INTERESTED or NOT_INTERESTED.
     */
    private void processBitfieldHandshake(Msg msg, String remoteId) {
        // decode & store their bitfield
        BitField theirs = BitField.decodeBitField(msg.getPayload());
        Peer.remotePeerDetails.get(remoteId).setBitField(theirs);

        writeLog(selfId + " received BITFIELD from " + remoteId);
        sendBitfield(Peer.peerToSocketMap.get(remoteId), remoteId);
        Peer.remotePeerDetails.get(remoteId).setPeerState(3);

        // decide interest
        if (Peer.bitFieldMessage.findFirstMissingPiece(theirs) >= 0) {
            writeLog(selfId + " sent INTERESTED to " + remoteId);
            sendInterested( Peer.peerToSocketMap.get(remoteId), remoteId);
            Peer.remotePeerDetails.get(remoteId).setPeerState(9);
        } else {
            writeLog(selfId + " sent NOT_INTERESTED to " + remoteId);
            sendNotInterested( Peer.peerToSocketMap.get(remoteId), remoteId);
            Peer.remotePeerDetails.get(remoteId).setPeerState(13);
        }
    }

    private void processInterestedOrNot(String type, String remoteId) {
        if (Constants.INTERESTED.equals(type)) {
            handleInterested(remoteId);
        } else if (Constants.NOT_INTERESTED.equals(type)) {
            handleNotInterested(remoteId);
        }
    }

    private void processFileRequest(Msg msg, String type, String remoteId) {
        if (!Constants.REQUEST.equals(type)) return;
        sendPiece(Peer.peerToSocketMap.get(remoteId), msg, remoteId);
        broadcastDownloadCompleteIfNeeded();
        if (isNeitherPreferredNorOptimistic(remoteId)) {
            chokePeer(remoteId);
        }
    }

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

    /* ---------- state-11 : received a PIECE message ---------- */
    private void processReceivedPiece(Msg msg, String type, String remoteId) {

        if (!Constants.PIECE.equals(type)) return;

        /* 1 . update throughput                                */
        byte[] payload = msg.getPayload();
        updateDownloadRate(payload.length, remoteId);

        /* 2 . write the piece to disk & update our bit-field   */
        FilePiece piece = FilePiece.fromPayload(payload);
        Peer.bitFieldMessage.updateBitField(remoteId, piece);

        /* 3 . ***** NEW:  piece-download log *****             */
        int haveNow = Peer.bitFieldMessage.countAvailablePieces();
        writeLog(String.format("Peer %s has downloaded the piece %d from %s. "
                + "Now the number of pieces it has is %d",
                selfId, piece.getPieceIndex(), remoteId, haveNow));

        /* 4 . request the next interesting piece (if any)      */
        requestFirstMissingPiece(remoteId);

        /* 5 . refresh flags coming from PeerInfo.cfg           */
        Peer.updateOtherPeerMetadata();

        /* 6 . broadcast HAVE to every still-interested peer    */
        for (String id : Peer.remotePeerDetails.keySet()) {
            if (!id.equals(Peer.peerID) && isPeerStillInterested(id)) {
                sendHave(Peer.peerToSocketMap.get(id));
                Peer.remotePeerDetails.get(id).setPeerState(3);
            }
        }

        /* 7 . ***** NEW:  complete-file log *****              */
        if (Peer.bitFieldMessage.isDownloadComplete()) {
            writeLog(String.format("Peer %s has downloaded the complete file",
                                selfId));
        }

        /* 8 . notify others with MESSAGE_DOWNLOADED if needed  */
        broadcastDownloadCompleteIfNeeded();
    }


    private void processHaveOrInterestingPieces(Msg msg, String ignored, String remoteId) {
        writeLog(selfId + " got HAVE from " + remoteId);
        if (isInteresting(msg, remoteId)) handleInterested(remoteId);
        else                              handleNotInterested(remoteId);
    }

    private void processHaveOrUnchoke(Msg msg, String type, String remoteId) {
        if (Constants.HAVE.equals(type)) {
            processHaveOrInterestingPieces(msg, type, remoteId);
        } else if (Constants.UNCHOKE.equals(type)) {
            requestFirstMissingPiece(remoteId);
        }
    }

    private void handlePeerFinished(String remoteId) {
        writeLog(remoteId + " finished downloading.");
        int prev = Peer.remotePeerDetails.get(remoteId).getPreviousState();
        Peer.remotePeerDetails.get(remoteId).setPeerState(prev);
    }

    /* ---------- interest / choke logic ---------- */

    private void handleInterested(String remoteId) {
        writeLog(selfId + " got INTERESTED from " + remoteId);
        PeerMetadata pm = Peer.remotePeerDetails.get(remoteId);
        pm.setInterested(true);
        pm.setHandshaked(true);
        if (isNeitherPreferredNorOptimistic(remoteId)) {
            chokePeer(remoteId);
        } else {
            unchokePeer(remoteId);
        }
    }

    private void handleNotInterested(String remoteId) {
        writeLog(selfId + " got NOT_INTERESTED from " + remoteId);
        PeerMetadata pm = Peer.remotePeerDetails.get(remoteId);
        pm.setInterested(false);
        pm.setHandshaked(true);
        pm.setPeerState(5);
    }

    private boolean isNeitherPreferredNorOptimistic(String id) {
        return !Peer.preferredNeighbours.containsKey(id)
            && !Peer.optimisticUnchoked.containsKey(id);
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

    /* ---------- helpers & messaging primitives ---------- */

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

    private boolean isPeerStillInterested(String id) {
        PeerMetadata pm = Peer.remotePeerDetails.get(id);
        return !pm.hasCompletedFile() && !pm.isChoked() && pm.isInterested();
    }

    private boolean isInteresting(Msg msg, String remoteId) {
        BitField bf = BitField.decodeBitField(msg.getPayload());
        Peer.remotePeerDetails.get(remoteId).setBitField(bf);
        int idx = Peer.bitFieldMessage.findFirstMissingPiece(bf);
        if (idx != -1 && Constants.HAVE.equals(msg.getType())) {
            writeLog(selfId + " remote " + remoteId + " has piece " + idx);
        }
        return idx != -1;
    }

    private void updateDownloadRate(long payloadBytes, String remoteId) {
        PeerMetadata pm = Peer.remotePeerDetails.get(remoteId);
        pm.setEndTime(new Date());
        long elapsed = pm.getEndTime().getTime() - pm.getStartTime().getTime();
        if (elapsed == 0) elapsed = 1;
        double rate = ((double)(payloadBytes + Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE) / elapsed) * 1000;
        pm.setDataRate(rate);
    }

    private void sendBitfield(Socket sock, String remoteId) {
        writeLog(selfId + " → BITFIELD → " + remoteId);
        sendControlMsg(sock, new Msg(Constants.BITFIELD,
                Peer.bitFieldMessage.encodeBitField()));
    }

    private void sendHave(Socket sock) {
        sendControlMsg(sock, new Msg(Constants.HAVE,
                Peer.bitFieldMessage.encodeBitField()));
    }

    private void sendChoke(Socket sock, String remoteId) {
        writeLog(selfId + " → CHOKE → " + remoteId);
        sendControlMsg(sock, new Msg(Constants.CHOKE));
    }

    private void sendUnchoke(Socket sock, String remoteId) {
        writeLog(selfId + " → UNCHOKE → " + remoteId);
        sendControlMsg(sock, new Msg(Constants.UNCHOKE));
    }

    private void sendInterested(Socket sock, String remoteId) {
        writeLog(selfId + " → INTERESTED → " + remoteId);
        sendControlMsg(sock, new Msg(Constants.INTERESTED));
    }

    private void sendNotInterested(Socket sock, String remoteId) {
        writeLog(selfId + " → NOT_INTERESTED → " + remoteId);
        sendControlMsg(sock, new Msg(Constants.NOT_INTERESTED));
    }

    private void sendRequest(Socket sock, int pieceIdx, String remoteId) {
        writeLog(selfId + " REQUEST piece " + pieceIdx + " → " + remoteId);
        sendControlMsg(sock, new Msg(Constants.REQUEST,
                ByteBuffer.allocate(4).putInt(pieceIdx).array()));
    }

    private void sendPiece(Socket sock, Msg req, String remoteId) {
        int idx = ByteBuffer.wrap(req.getPayload()).getInt();
        writeLog(selfId + " sending PIECE " + idx + " → " + remoteId);
        byte[] buf = new byte[SysConfig.pieceSize];
        int n;
        try (RandomAccessFile raf = new RandomAccessFile(
                new File(Peer.peerFolder, SysConfig.fileName), "r")) {
            raf.seek((long)idx * SysConfig.pieceSize);
            n = raf.read(buf, 0, SysConfig.pieceSize);
        } catch (IOException e) {
            return;
        }
        byte[] payload = new byte[n + Constants.PIECE_INDEX_LENGTH];
        System.arraycopy(req.getPayload(), 0, payload, 0, Constants.PIECE_INDEX_LENGTH);
        System.arraycopy(buf, 0, payload, Constants.PIECE_INDEX_LENGTH, n);
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
