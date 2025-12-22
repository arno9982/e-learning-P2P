package org.example.model;

import java.util.List;

public class HelloMessage {
    public String type = "HELLO";
    public String peerId;
    public String pseudo;
    public int tcpPort;
    public List<CourseAdvert> visibleCourses;
    public String token;
    public boolean isTeacher; // Nouveau champ

    public HelloMessage() {}

    public HelloMessage(String peerId, String pseudo, int tcpPort, List<CourseAdvert> visibleCourses, String token, boolean isTeacher) {
        this.peerId = peerId;
        this.pseudo = pseudo;
        this.tcpPort = tcpPort;
        this.visibleCourses = visibleCourses;
        this.token = token;
        this.isTeacher = isTeacher;
    }
}