package org.example.model;

import java.io.Serializable;
import java.util.UUID;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Command {
        JOIN,
        PEER_LIST_REQUEST,
        PEER_LIST_RESPONSE,
        SHARE_COURSES,
        REQUEST_COURSE,
        DOWNLOAD_REQUEST,
        DOWNLOAD_RESPONSE
    }

    private Command command;
    private Serializable data;
    private String senderId;

    public Message(Command command, Serializable data, String senderId) {
        this.command = command;
        this.data = data;
        this.senderId = senderId;
    }

    // Getters
    public Command getCommand() {
        return command;
    }

    public Serializable getData() {
        return data;
    }

    public String getSenderId() {
        return senderId;
    }
}