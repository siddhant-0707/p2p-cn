package Queue;

import Metadata.MsgMetadata;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MsgQueue {
    private static final ConcurrentLinkedQueue<MsgMetadata> messageQueue = new ConcurrentLinkedQueue<>();

    public static synchronized void addMessage(MsgMetadata messageDetails) {
        messageQueue.offer(messageDetails);
    }

    public static synchronized MsgMetadata retrieveMessage() {
        return messageQueue.poll();
    }

    public static synchronized boolean isEmpty() {
        return messageQueue.isEmpty();
    }
}
