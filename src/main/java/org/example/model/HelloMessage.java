package org.example.model;

import java.util.List;

public class HelloMessage {
    public String type = "HELLO";
    public String peerId;
    public String pseudo;
    public int tcpPort;
    public String token; // simple anti-spam
    public List<CourseAdvert> visibleCourses;

    public HelloMessage() {}

    public HelloMessage(String peerId, String pseudo, int tcpPort, List<CourseAdvert> visible, String token) {
        this.peerId = peerId; this.pseudo = pseudo; this.tcpPort = tcpPort;
        this.visibleCourses = visible; this.token = token;
    }
}
