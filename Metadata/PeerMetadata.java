package Metadata;

import Msgs.BitField;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PeerMetadata {
    private String peerId;
    private String ipAddress;
    private String portNumber;
    private boolean hasFile;
    private int peerIndex;
    private int currentState = -1;
    private int previousState = -1;
    private boolean isPreferredNeighbor = false;
    private BitField bitField;
    private boolean isOptimisticallyUnchoked;
    private boolean isInterested;
    private boolean isHandshaked;
    private boolean isChoked;
    private boolean hasCompletedFile;
    private Date startTime;
    private Date endTime;
    private double dataRate;

    public PeerMetadata(String peerId, String ipAddress, String portNumber, int hasFile, int peerIndex) {
        this.peerId = peerId;
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.hasFile = hasFile == 1;
        this.peerIndex = peerIndex;
        this.dataRate = 0.0;
        this.isOptimisticallyUnchoked = false;
    }

    public void updatePeerFileStatus(String peerId, boolean fileStatus) throws IOException {
        Path path = Paths.get("Configs/PeerInfo.cfg");
        Stream<String> lines = Files.lines(path);

        List<String> updatedLines = lines.map(line -> {
            String[] tokens = line.trim().split("\\s+");
            if (tokens[0].equals(peerId)) {
                return tokens[0] + " " + tokens[1] + " " + tokens[2] + " " + (fileStatus ? 1 : 0);
            }
            return line;
        }).collect(Collectors.toList());
        
        Files.write(path, updatedLines);
        lines.close();
    }

    public boolean isInterested() {
        return isInterested;
    }

    public void setInterested(boolean interested) {
        isInterested = interested;
    }

    public boolean hasCompletedFile() {
        return hasCompletedFile;
    }

    public void setCompletedFile(boolean completedFile) {
        hasCompletedFile = completedFile;
    }

    public boolean isChoked() {
        return isChoked;
    }

    public void setChoked(boolean choked) {
        isChoked = choked;
    }
}
