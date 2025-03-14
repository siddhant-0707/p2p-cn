package Msgs;

import Configs.SysConfig;
import Logging.Helper;
import Process.Peer;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class BitField {
    private FilePiece[] filePieces;
    private int totalPieces;

    public BitField() {
        int fileSize = SysConfig.fileSize;
        int pieceSize = SysConfig.pieceSize;
        this.totalPieces = (int) Math.ceil((double) fileSize / pieceSize);
        this.filePieces = new FilePiece[this.totalPieces];
        Arrays.setAll(filePieces, index -> new FilePiece());
    }

    public void initializePieces(String peerId, boolean hasFile) {
        for (FilePiece filePiece : filePieces) {
            filePiece.setPieceAvailable(hasFile);
            filePiece.setFromPeer(peerId);
        }
    }

    public byte[] encodeBitField() {
        int numBytes = (int) Math.ceil((double) totalPieces / 8);
        byte[] encodedBitField = new byte[numBytes];
        
        for (int i = 0; i < totalPieces; i++) {
            if (filePieces[i].isPieceAvailable()) {
                encodedBitField[i / 8] |= (1 << (7 - (i % 8)));
            }
        }
        return encodedBitField;
    }

    public static BitField decodeBitField(byte[] encodedBitField) {
        BitField bitField = new BitField();
        
        for (int i = 0; i < encodedBitField.length * 8; i++) {
            if (i < bitField.getTotalPieces()) {
                bitField.getFilePieces()[i].setPieceAvailable((encodedBitField[i / 8] & (1 << (7 - (i % 8)))) != 0);
            }
        }
        return bitField;
    }

    public int countAvailablePieces() {
        int count = 0;
        for (FilePiece filePiece : filePieces) {
            if (filePiece.isPieceAvailable()) {
                count++;
            }
        }
        return count;
    }

    public boolean isDownloadComplete() {
        for (FilePiece filePiece : filePieces) {
            if (!filePiece.isPieceAvailable()) {
                return false;
            }
        }
        return true;
    }

    public int findFirstMissingPiece(BitField otherBitField) {
        for (int i = 0; i < totalPieces; i++) {
            if (!filePieces[i].isPieceAvailable() && otherBitField.getFilePieces()[i].isPieceAvailable()) {
                return i;
            }
        }
        return -1;
    }

    public void updateBitField(String peerID, FilePiece receivedPiece) {
        int index = receivedPiece.getPieceIndex();
        
        if (!filePieces[index].isPieceAvailable()) {
            try {
                String fileName = SysConfig.fileName;
                File file = new File(Peer.peerID, fileName);
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                raf.seek(index * SysConfig.pieceSize);
                raf.write(receivedPiece.getContent());
                raf.close();
                
                filePieces[index].setPieceAvailable(true);
                filePieces[index].setFromPeer(peerID);
                Helper.writeLog(Peer.peerID + " received piece " + index + " from Peer " + peerID);
                
                if (isDownloadComplete()) {
                    Peer.remotePeerDetails.get(peerID).setCompletedFile(true);
                    Helper.writeLog(Peer.peerID + " completed file download.");
                }
            } catch (IOException e) {
                Helper.writeLog("Error updating bitfield: " + e.getMessage());
                e.printStackTrace();
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
