package Logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {
    // Timestamp format: YYYY-MM-DD HH:mm:ss
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String format(LogRecord record) {
        return formatMessage(record.getMessage());
    }

    /**
     * Applies timestamp and peer label to a log message.
     *
     * @param message Raw log text
     * @return Formatted log entry
     */
    public static String formatMessage(String message) {
        String timestamp = TIMESTAMP_FORMATTER.format(LocalDateTime.now());
        return String.format("%s - Peer %s: %s%n", timestamp, System.getProperty("peer.id"), message);
    }
}
