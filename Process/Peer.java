package Process;

import Configs.SysConfig;
import Handler.FileServerHandler;
import Handler.MsgHandler;
import Handler.MsgProcessingHandler;
import Logging.Helper;
import Msgs.BitField;
import Metadata.PeerMetadata;
import Tasks.OptimisticallyUnchoke;
import Tasks.PreferredNeighbors;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

import static Logging.Helper.writeLog;

/**
 * Main peer process: sets up configuration, networking,
 * neighbour-selection timers, and overall life-cycle.
 */
public class Peer {

    /* ---------- thread-pools / sockets ---------- */
    private static final ExecutorService fileServerExecutor = Executors.newSingleThreadExecutor();
    private static final ExecutorService messageProcessor   = Executors.newSingleThreadExecutor();
    private static final ExecutorService receivingExecutor  = Executors.newCachedThreadPool();
    private static       ServerSocket    fileServingSocket;

    /* ---------- peer identity & run-time state ---------- */
    public  static String  peerID;
    public  static String  peerFolder;
    private static int     peerIndex;
    private static int     peerPort;
    public  static boolean isFirstPeer;
    private static boolean hasFile;

    /* ---------- file / piece state ---------- */
    public  static BitField bitFieldMessage;

    /* ---------- neighbour data structures ---------- */
    public  static final Map<String,PeerMetadata> remotePeerDetails  = new ConcurrentHashMap<>();
    public  static final Map<String,PeerMetadata> preferredNeighbours= new ConcurrentHashMap<>();
    public  static final Map<String,PeerMetadata> optimisticUnchoked = new ConcurrentHashMap<>();
    public  static final Map<String,Socket>       peerToSocketMap    = new ConcurrentHashMap<>();

    /* ---------- timers ---------- */
    private static Timer preferredNeighboursTimer;
    private static Timer optimisticUnchokeTimer;

    /* ---------- entry point ---------- */
    public static void main(String[] args) {

        peerID     = args[0];
        peerFolder = "peer_" + peerID;

        try {
            new Helper().setupLogger(peerID);
            writeLog("Peer " + peerID + " started");

            readConfiguration();
            setCurrentPeerDetails();
            initialiseBitfield();
            startMessageProcessor();
            startNetworking();
            scheduleNeighbourTasks();
            waitUntilComplete();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            writeLog("Peer " + peerID + " shutting down.");
            System.exit(0);
        }
    }

    /* ---------- configuration ---------- */

    private static void readConfiguration() throws IOException {
        /* ---------- Common.cfg ---------- */
        Files.lines(Paths.get("Configs/Common.cfg")).forEach(line -> {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "NumberOfPreferredNeighbors": SysConfig.preferredNeighbourCount     = Integer.parseInt(p[1]); break;
                case "UnchokingInterval":          SysConfig.unchokingInterval          = Integer.parseInt(p[1]); break;
                case "OptimisticUnchokingInterval":SysConfig.optimisticUnchokingInterval = Integer.parseInt(p[1]); break;
                case "FileSize":                   SysConfig.fileSize                   = Integer.parseInt(p[1]); break;
                case "FileName":                   SysConfig.fileName                   = p[1];                   break;
                case "PieceSize":                  SysConfig.pieceSize                  = Integer.parseInt(p[1]); break;
            }
        });

        /* ---------- PeerInfo.cfg ---------- */
        int idx = 0;
        for (String line : Files.readAllLines(Paths.get("Configs/PeerInfo.cfg"))) {
            String[] t = line.trim().split("\\s+");
            remotePeerDetails.put(t[0],
                    new PeerMetadata(t[0], t[1], t[2], Integer.parseInt(t[3]), idx++));
        }
    }

    /* ---------- peer-specific setup ---------- */

    private static void setCurrentPeerDetails() {
        PeerMetadata me = remotePeerDetails.get(peerID);
        peerPort    = Integer.parseInt(me.getPortNumber());
        peerIndex   = me.getPeerIndex();
        isFirstPeer = me.hasCompletedFile();
        hasFile     = isFirstPeer;
    }

    private static void initialiseBitfield() {
        bitFieldMessage = new BitField();
        bitFieldMessage.initializePieces(peerID, hasFile);
    }

    /* ---------- worker threads ---------- */

    private static void startMessageProcessor() {
        messageProcessor.execute(new MsgProcessingHandler(peerID));
    }

    private static void startNetworking() throws IOException {
        /* ---------- act as a server ---------- */
        fileServingSocket = new ServerSocket(peerPort);
        fileServerExecutor.execute(new FileServerHandler(fileServingSocket, peerID));

        /* ---------- connect to “previous” peers if we need pieces ---------- */
        if (!isFirstPeer) {
            createPlaceholderFile();
            for (PeerMetadata pm : remotePeerDetails.values()) {
                if (peerIndex > pm.getPeerIndex()) {
                    receivingExecutor.execute(new MsgHandler(
                            peerID, 1,
                            pm.getIpAddress(),
                            Integer.parseInt(pm.getPortNumber())));
                }
            }
        }
    }

    private static void createPlaceholderFile() throws IOException {
        File dir = new File(peerFolder);
        if (dir.mkdirs()) {
            try (OutputStream os =
                         new FileOutputStream(new File(dir, SysConfig.fileName))) {
                for (int i = 0; i < SysConfig.fileSize; i++) os.write(0);
            }
        }
    }

    /* ---------- neighbour timers ---------- */

    private static void scheduleNeighbourTasks() {
        preferredNeighboursTimer = new Timer(true);
        preferredNeighboursTimer.schedule(
                new PreferredNeighbors(), 0, SysConfig.unchokingInterval * 1000L);

        optimisticUnchokeTimer   = new Timer(true);
        optimisticUnchokeTimer.schedule(
                new OptimisticallyUnchoke(), 0, SysConfig.optimisticUnchokingInterval * 1000L);
    }

    /* ---------- completion / shutdown ---------- */

    private static void waitUntilComplete() throws InterruptedException {
        while (!allPeersComplete()) {
            Thread.sleep(2000);
        }

        writeLog("All peers have finished downloading.");
        preferredNeighboursTimer.cancel();
        optimisticUnchokeTimer.cancel();
        messageProcessor.shutdownNow();
        receivingExecutor.shutdownNow();
        fileServerExecutor.shutdownNow();
    }

    private static boolean allPeersComplete() {
        try {
            for (String line : Files.readAllLines(Paths.get("Configs/PeerInfo.cfg"))) {
                if (line.trim().endsWith("0")) return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /* ---------- helper used by OptimisticallyUnchoke ---------- */

    public synchronized static void updateOtherPeerMetadata() {
        try {
            for (String l : Files.readAllLines(Paths.get("Configs/PeerInfo.cfg"))) {
                String[] t = l.trim().split("\\s+");
                PeerMetadata pm = remotePeerDetails.get(t[0]);
                if ("1".equals(t[3])) {
                    pm.setInterested(false);
                    pm.setCompletedFile(true);
                    pm.setChoked(false);
                }
            }
        } catch (IOException ignored) {}
    }
}
