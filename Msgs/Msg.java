package Msgs;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static Logging.Helper.writeLog;

public class Msg {
    private String type;
    private int dataLength;
    private byte[] payload;
    private byte[] typeBytes;
    private byte[] lengthBytes;

    public Msg() {}

    public Msg(String messageType) {
        if (isValidMessageType(messageType)) {
            this.dataLength = Constants.MESSAGE_TYPE;
            this.typeBytes = messageType.trim().getBytes(StandardCharsets.UTF_8);
            this.payload = null;
        } else {
            writeLog("Invalid message type.");
        }
    }

    public Msg(String messageType, byte[] payload) {
        this();
        if (payload != null) {
            this.dataLength = payload.length + 1;
            this.payload = payload;
        } else if (isValidMessageType(messageType)) {
            this.dataLength = Constants.MESSAGE_TYPE;
            this.payload = null;
        }
        this.type = messageType;
        this.typeBytes = messageType.trim().getBytes(StandardCharsets.UTF_8);
    }

    private boolean isValidMessageType(String messageType) {
        return messageType.equals(Constants.INTERESTED) || messageType.equals(Constants.NOT_INTERESTED) ||
                messageType.equals(Constants.CHOKE) || messageType.equals(Constants.UNCHOKE) ||
                messageType.equals(Constants.MESSAGE_DOWNLOADED);
    }

    public String getType() {
        return this.type;
    }

    public byte[] getPayload() {
        return this.payload;
    }

    public byte[] getLengthBytes() {
        return this.lengthBytes;
    }

    public byte[] getTypeBytes() {
        return this.typeBytes;
    }

    public void setMessageLength(int length) {
        this.dataLength = length;
        this.lengthBytes = ByteBuffer.allocate(Integer.BYTES).putInt(length).array();
    }

    public void setMessageLength(byte[] length) {
        this.dataLength = ByteBuffer.wrap(length).getInt();
        this.lengthBytes = length;
    }

    public void setMessageType(byte[] type) {
        this.type = new String(type, StandardCharsets.UTF_8);
        this.typeBytes = type;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public static byte[] serializeMessage(Msg message) throws Exception {
        if (!isValidMessageCode(message.getType())) {
            throw new Exception("Invalid message type");
        }

        int totalLength = Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE + (message.getPayload() != null ? message.getPayload().length : 0);
        byte[] serializedMessage = new byte[totalLength];

        System.arraycopy(message.getLengthBytes(), 0, serializedMessage, 0, Constants.MESSAGE_LENGTH);
        System.arraycopy(message.getTypeBytes(), 0, serializedMessage, Constants.MESSAGE_LENGTH, Constants.MESSAGE_TYPE);

        if (message.getPayload() != null) {
            System.arraycopy(message.getPayload(), 0, serializedMessage, Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE, message.getPayload().length);
        }

        return serializedMessage;
    }

    public static Msg deserializeMessage(byte[] message) {
        Msg msg = new Msg();
        byte[] msgLength = new byte[Constants.MESSAGE_LENGTH];
        byte[] msgType = new byte[Constants.MESSAGE_TYPE];

        System.arraycopy(message, 0, msgLength, 0, Constants.MESSAGE_LENGTH);
        System.arraycopy(message, Constants.MESSAGE_LENGTH, msgType, 0, Constants.MESSAGE_TYPE);

        msg.setMessageLength(msgLength);
        msg.setMessageType(msgType);

        int messageLength = ByteBuffer.wrap(msgLength).getInt();
        if (messageLength > 1) {
            byte[] payload = new byte[messageLength - 1];
            System.arraycopy(message, Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE, payload, 0, payload.length);
            msg.setPayload(payload);
        }

        return msg;
    }

    private static boolean isValidMessageCode(String messageType) {
        try {
            int messageCode = Integer.parseInt(messageType);
            return messageCode > 0 && messageCode <= 8;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public int getDataLength() {
        return dataLength;
    }

    public void setDataLength(int dataLength) {
        this.dataLength = dataLength;
    }
}
