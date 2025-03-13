package Logging;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 * Utility class for managing logging operations.
 */
public class Helper {

    private FileHandler logFileHandler;

    private static final Logger logger = Logger.getLogger(Helper.class.getName());

    /**
     * Initializes the logger by creating a log file and configuring its handler.
     *
     * @param peerID The ID of the peer for which the log file is created
     */
    public void setupLogger(String peerID) {
        try {
            logFileHandler = new FileHandler("log_peer_" + peerID + ".log");
            logFileHandler.setFormatter(new LogFormatter());
            logger.addHandler(logFileHandler);
            logger.setUseParentHandlers(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Logs a message to both the log file and the console.
     *
     * @param logMessage The message to be logged and displayed
     */
    public static void writeLog(String logMessage) {
        logger.info(logMessage);
        System.out.println(LogFormatter.getFormattedMessage(logMessage));
    }
}
