package Logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {

    // Format for date and time in log messages
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String format(LogRecord record) {
        return getFormattedMessage(record.getMessage());
    }

    /**
     * Generates a formatted log message with timestamp.
     *
     * @param message message to format
     * @return formatted log entry
     */
    public static String getFormattedMessage(String message) {
        return dateTimeFormatter.format(LocalDateTime.now()) + " - Peer: " + message + "\n";
    }
}
