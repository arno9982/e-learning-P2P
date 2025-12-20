package org.example.service;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.model.CourseAdvert;
import org.example.model.HelloMessage;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache des pairs découverts (via multicast + relai).
 * Expose :
 *  - observableRows() : pour binder une TableView
 *  - snapshotRows()   : pour récupérer une copie côté service
 */
public class DiscoveredPeers {
    private static final DiscoveredPeers I = new DiscoveredPeers();
    public static DiscoveredPeers get(){ return I; }

    public static class PeerState {
        public String peerId, pseudo, ip; public int tcpPort;
        public long lastSeen;
        public final Map<String, CourseAdvert> courses = new ConcurrentHashMap<>();
    }

    public static class Row {
        public final String peerId, pseudo, ip, courseId, title;
        public final int version, tcpPort;
        public Row(String peerId, String pseudo, String ip, int tcpPort, CourseAdvert c) {
            this.peerId = peerId; this.pseudo = pseudo; this.ip = ip; this.tcpPort = tcpPort;
            this.courseId = c.id; this.title = c.title; this.version = c.version;
        }
    }

    private final Map<String, PeerState> peers = new ConcurrentHashMap<>();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();

    private DiscoveredPeers() {
        Timer t = new Timer(true);
        t.scheduleAtFixedRate(new TimerTask() {
            public void run(){ purgeAndRefresh(); }
        }, 3000, 3000);
    }

    public void updateFromHello(HelloMessage m, String ip) {
        PeerState s = peers.computeIfAbsent(m.peerId, k -> new PeerState());
        s.peerId = m.peerId; s.pseudo = m.pseudo; s.tcpPort = m.tcpPort; s.lastSeen = System.currentTimeMillis();

        // NE PAS écraser une IP réelle par "<relay>"
        if (s.ip == null || s.ip.isBlank() || (!"<relay>".equals(s.ip))) {
            // on a déjà une vraie IP -> on la garde
            s.ip = (s.ip != null && !"<relay>".equals(s.ip)) ? s.ip : ip;
        } else {
            // cas initial: s.ip vaut null ou "<relay>" -> on prend ip
            s.ip = ip;
        }

        s.courses.clear();
        if (m.visibleCourses != null) m.visibleCourses.forEach(c -> s.courses.put(c.id, c));
        refreshRows();
    }


    private void purgeAndRefresh() {
        long now = Instant.now().getEpochSecond();
        peers.values().forEach(p -> p.courses.values().removeIf(c -> c.expiresAt < now));
        peers.values().removeIf(p -> (System.currentTimeMillis() - p.lastSeen) > 15000 && p.courses.isEmpty());
        refreshRows();
    }

    private void refreshRows() {
        List<Row> list = new ArrayList<>();
        for (PeerState p : peers.values())
            for (CourseAdvert c : p.courses.values())
                list.add(new Row(p.peerId, p.pseudo, p.ip, p.tcpPort, c));
        Platform.runLater(() -> rows.setAll(list));
    }

    /** Pour binder directement une TableView */
    public ObservableList<Row> observableRows() { return rows; }

    /** Copie immuable pour le service (évite les problèmes de thread / UI) */
    public List<Row> snapshotRows() { return new ArrayList<>(rows); }
}
