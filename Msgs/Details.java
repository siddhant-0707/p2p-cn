package Msgs;

public class Details {

    private Msg message;
    private String fromPeerID;

    public Details() {
        message = new Msg();
        fromPeerID = null;
    }

    public Msg getMessage() {
        return message;
    }

    public void setMessage(Msg message) {
        this.message = message;
    }

    public String getFromPeerID() {
        return fromPeerID;
    }

    public void setFromPeerID(String fromPeerID) {
        this.fromPeerID = fromPeerID;
    }
}
