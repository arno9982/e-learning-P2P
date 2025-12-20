package org.example.model;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.List;
import java.util.ArrayList;

public class Peer implements Serializable {
    private static final long serialVersionUID = 1L;

    private String pseudo;
    private String peerId;
    private InetAddress address;
    private int port;
    private boolean visibiliteP2P;

    private List<Course> sharedCourses = new ArrayList<>();

    public Peer(String pseudo, InetAddress address, int port, boolean visibiliteP2P) {
        this.pseudo = pseudo;
        this.peerId = java.util.UUID.randomUUID().toString();
        this.address = address;
        this.port = port;
        this.visibiliteP2P = visibiliteP2P;
    }

    // Getters et Setters pour tous les attributs

    public String getPseudo() {
        return pseudo;
    }

    public String getPeerId() {
        return peerId;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public boolean isVisibiliteP2P() {
        return visibiliteP2P;
    }

    public void setVisibiliteP2P(boolean visibiliteP2P) {
        this.visibiliteP2P = visibiliteP2P;
    }

    public List<Course> getSharedCourses() {
        return sharedCourses;
    }

    public void setSharedCourses(List<Course> sharedCourses) {
        this.sharedCourses = sharedCourses;
    }

    // Suppression des méthodes addSharedCourse et removeSharedCourse

    @Override
    public String toString() {
        return "Peer{" +
                "pseudo='" + pseudo + '\'' +
                ", peerId='" + peerId + '\'' +
                ", address=" + address +
                ", port=" + port +
                ", visibiliteP2P=" + visibiliteP2P +
                '}';
    }
}