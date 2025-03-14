package Process;

import Configs.SysConfig;
import Logging.Helper;
import Msgs.BitField;
import Metadata.PeerMetadata;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Timer;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public class Peer {
    public Thread fileServerThread;
    public ServerSocket serverSocket = null;
    public static String peerID;
    public static int peerIndex;
    public static boolean isFirstPeer = false;
    public static int peerPort;
    public static boolean hasFile;
    public static BitField bitFieldMessage = null;
    public static Thread messageProcessor;
    public static boolean isDownloadComplete = false;
    public static Vector<Thread> peerThreads = new Vector<>();
    public static Vector<Thread> serverThreads = new Vector<>();
    public static volatile Timer preferredNeighborsTimer;
    public static volatile Timer optimisticallyUnChokedNeighborTimer;
    public static volatile ConcurrentHashMap<String, PeerMetadata> remotePeerDetails = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, PeerMetadata> preferredNeighboursMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Socket> peerToSocketMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, PeerMetadata> optimisticUnChokedNeighbors = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        Peer process = new Peer();
        peerID = args[0];

        try {
            Helper logHelper = new Helper();
            logHelper.setupLogger(peerID);
            Helper.writeLog("Peer " + peerID + " started");
            System.out.println("Reading configurations...");
            loadConfiguration();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Helper.writeLog("Peer " + peerID + " is shutting down...");
            System.exit(0);
        }
    }

    public static void loadConfiguration() throws Exception {
        initializeSystemConfig();
        loadPeerMetadata();
    }

    public static void initializeSystemConfig() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("Configs/Common.cfg"));
        for (String line : lines) {
            String[] properties = line.split("\\s+");
            switch (properties[0]) {
                case "NumberOfPreferredNeighbors":
                    SysConfig.preferredNeighbourCount = Integer.parseInt(properties[1]);
                    break;
                case "UnchokingInterval":
                    SysConfig.unchokingInterval = Integer.parseInt(properties[1]);
                    break;
                case "OptimisticUnchokingInterval":
                    SysConfig.optimisticUnchokingInterval = Integer.parseInt(properties[1]);
                    break;
                case "FileSize":
                    SysConfig.fileSize = Integer.parseInt(properties[1]);
                    break;
                case "FileName":
                    SysConfig.fileName = properties[1];
                    break;
                case "PieceSize":
                    SysConfig.pieceSize = Integer.parseInt(properties[1]);
                    break;
            }
        }
    }

    public static void loadPeerMetadata() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("Configs/PeerInfo.cfg"));
        for (int i = 0; i < lines.size(); i++) {
            String[] properties = lines.get(i).split("\\s+");
            remotePeerDetails.put(properties[0], new PeerMetadata(properties[0], properties[1], properties[2], Integer.parseInt(properties[3]), i));
        }
    }

    public Thread getFileServerThread() {
        return fileServerThread;
    }

    public void setFileServerThread(Thread fileServerThread) {
        this.fileServerThread = fileServerThread;
    }
}
