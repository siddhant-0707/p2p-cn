package Configs;

public class SysConfig {

    // Number of neighbors preferred by each peer
    public static int preferredNeighbourCount;

    // Time interval (in seconds) to update preferred neighbors
    public static int unchokingInterval;

    // Time interval (in seconds) to optimistically unchoke a neighbor
    public static int optimisticUnchokingInterval;

    // Name of the shared file
    public static String fileName;

    // Total size of the shared file (in bytes)
    public static int fileSize;

    // Size of each piece of the shared file (in bytes)
    public static int pieceSize;

    public static final String peerInfoFile = System.getProperty("user.dir") + "/Configs/PeerInfo.cfg";
    public static final String systemConfigurationFile = System.getProperty("user.dir") + "/Configs/Common.cfg";
}