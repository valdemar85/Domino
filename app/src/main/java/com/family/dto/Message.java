package com.family.dto;

public class Message {
    public static final String TEAM_PARTICIPATION_REQUEST = "TEAM PARTICIPATION REQUEST";
    public static final String ALERT = "ALERT";

    private String fromId;
    private String fromName;
    private String toId;
    private String messageType;
    private String messageText;

    public Message() {
    }

    public Message(String fromId, String fromName, String toId, String messageType) {
        this.fromId = fromId;
        this.fromName = fromName;
        this.toId = toId;
        this.messageType = messageType;
    }

    public String getFromId() {
        return fromId;
    }

    public void setFromId(String fromId) {
        this.fromId = fromId;
    }

    public String getToId() {
        return toId;
    }

    public void setToId(String toId) {
        this.toId = toId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }
}
