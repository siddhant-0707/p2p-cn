package Metadata;

import Msgs.Msg;

public class MessageMetadata {
    private Msg message;
    private String senderId;

    public MessageMetadata() {
        this.message = new Msg();
        this.senderId = "";
    }

    public Msg getMessage() {
        return message;
    }

    public void setMessage(Msg message) {
        this.message = message;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
}
