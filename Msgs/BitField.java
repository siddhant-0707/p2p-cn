package Msgs;

import Configs.SysConfig;
import Logging.Helper;
import Process.Peer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class BitField {
    private final FilePiece[] filePieces;
    private final int totalPieces;

    public BitField() {
        int fileSize  = SysConfig.fileSize;
        int pieceSize = SysConfig.pieceSize;
        this.totalPieces = (int) Math.ceil((double) fileSize / pieceSize);
        this.filePieces  = new FilePiece[this.totalPieces];
        Arrays.setAll(this.filePieces, i -> new FilePiece());
    }

    public void initializePieces(String peerId, boolean hasFile) {
        for (FilePiece piece : filePieces) {
            piece.setPieceAvailable(hasFile);
            piece.setFromPeer(peerId);
        }
    }

    public byte[] encodeBitField() {
        int numBytes = (totalPieces + 7) / 8;
        byte[] bits = new byte[numBytes];

        for (int i = 0; i < totalPieces; i++) {
            if (filePieces[i].isPieceAvailable()) {
                bits[i / 8] |= (1 << (7 - (i % 8)));
            }
        }
        return bits;
    }


    public static BitField decodeBitField(byte[] data) {
        BitField bf = new BitField();
    
        // 1) nothing to decode → return an “all-zero” bit-field
        if (data == null || data.length == 0) {
            return bf;
        }
    
        // 2) decode only as many bits as we really have input for
        int bitsToDecode = Math.min(bf.totalPieces, data.length * 8);
    
        for (int i = 0; i < bitsToDecode; i++) {
            boolean available = ((data[i / 8] & 0xFF) & (1 << (7 - (i % 8)))) != 0;
            bf.filePieces[i].setPieceAvailable(available);
        }
        return bf;
    }

    public int countAvailablePieces() {
        int count = 0;
        for (FilePiece piece : filePieces) {
            if (piece.isPieceAvailable()) count++;
        }
        return count;
    }

    public boolean isDownloadComplete() {
        for (FilePiece piece : filePieces) {
            if (!piece.isPieceAvailable()) return false;
        }
        return true;
    }

    /**
     * Find the first piece this peer is missing that the other BitField has.
     * @return piece index or –1 if none
     */
    public int findFirstMissingPiece(BitField other) {
        for (int i = 0; i < totalPieces; i++) {
            if (!filePieces[i].isPieceAvailable() && other.filePieces[i].isPieceAvailable()) {
                return i;
            }
        }
        return -1;
    }

    public void updateBitField(String peerId, FilePiece received) {
        int idx = received.getPieceIndex();
        if (!filePieces[idx].isPieceAvailable()) {
            File dir  = new File(Peer.peerID);
            File file = new File(dir, SysConfig.fileName);
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.seek((long) idx * SysConfig.pieceSize);
                raf.write(received.getContent());

                filePieces[idx].setPieceAvailable(true);
                filePieces[idx].setFromPeer(peerId);
                Helper.writeLog(Peer.peerID + " received piece " + idx + " from " + peerId);

                if (isDownloadComplete()) {
                    Peer.remotePeerDetails.get(peerId).setCompletedFile(true);
                    Helper.writeLog(Peer.peerID + " completed file download.");
                }
            } catch (IOException e) {
                Helper.writeLog("Error updating bitfield: " + e.getMessage());
            }
        }
    }

    public FilePiece[] getFilePieces() {
        return filePieces;
    }

    public int getTotalPieces() {
        return totalPieces;
    }
}
