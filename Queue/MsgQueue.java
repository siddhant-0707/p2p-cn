package Queue;

import Metadata.MsgMetadata;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MsgQueue {
    private static final ConcurrentLinkedQueue<MsgMetadata> queue = new ConcurrentLinkedQueue<>();

    public static void addMessageToMessageQueue(MsgMetadata message) {
        queue.offer(message);
    }

    public static MsgMetadata getMessageFromQueue() {
        return queue.poll();
    }

    public static boolean hasNext() {
        return !queue.isEmpty();
    }
}
