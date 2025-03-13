package Metadata;

import Msgs.Msg;

public class MsgMetadata {
    private Msg message;
    private String senderId;

    public MsgMetadata() {
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
