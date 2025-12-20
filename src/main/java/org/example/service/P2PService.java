// src/main/java/org/example/service/P2PService.java
package org.example.service;

import org.example.controller.MainController;
import org.example.model.Course;
import org.example.model.CourseAdvert;
import org.example.model.HelloMessage;
import org.example.util.Json;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service P2P (singleton) :
 *  - Découverte LAN (multicast) + relai
 *  - Publication de la liste des cours visibles (setSharedCourses) -> déléguée à CourseServer
 *  - Petit serveur TCP local (CourseServer) pour servir les .crs (protocole DataInput/DataOutput)
 *  - Téléchargement d'un cours distant (downloadCourseBytes)
 *  - Exposition d'une liste de cours réseau pour l'UI (getAllNetworkCourses)
 */
public class P2PService {

    // ====== Singleton ======
    private static P2PService INSTANCE;
    public static synchronized P2PService getInstance() {
        if (INSTANCE == null) INSTANCE = new P2PService();
        return INSTANCE;
    }
    private P2PService() {}

    // ====== Réseau & état ======
    public static final String MCAST_ADDR = "239.255.42.42";
    public static final int MCAST_PORT = 42424;
    private static final int HEARTBEAT_MS = 3000;
    private static final int EXPIRY_MS = HEARTBEAT_MS * 5;

    private String peerId;
    private String pseudo;
    private String token;
    private int tcpPort = 5055;

    /** inventaire publié dans HELLO (id/titre/ttl) */
    private final Set<CourseAdvert> visible = ConcurrentHashMap.newKeySet();

    /** source connue pour un courseId (utile si tu veux d’autres schémas ensuite) */
    private final Map<String, HostPort> idSource = new ConcurrentHashMap<>();
    private record HostPort(String host, int port) {}

    private MulticastSocket socket;
    private InetAddress group;
    private volatile boolean running;

    /** Serveur de fichiers .crs */
    private CourseServer courseServer;

    /** Dernière liste partagée (pour re-pousser au serveur si redémarrage/port changé) */
    private final List<Course> lastSharedCourses = new CopyOnWriteArrayList<>();

    private MainController mainController; // pour refresh UI si besoin

    // ====== API pour les contrôleurs ======
    public void setMainController(MainController mainController) { this.mainController = mainController; }

    /** Démarre le P2P si pas déjà lancé */
    public synchronized void startP2PService(String pseudo, boolean withRelay) throws IOException {
        if (running) return;
        this.peerId = UUID.randomUUID().toString();
        this.token  = UUID.randomUUID().toString();
        this.pseudo = (pseudo == null || pseudo.isBlank()) ? "user" : pseudo.trim();

        // 1) serveur TCP local pour servir les .crs (essaie une fenêtre de ports)
        startCourseServerOnFreePort();

        // 2) multicast
        group = InetAddress.getByName(MCAST_ADDR);
        socket = new MulticastSocket(MCAST_PORT);
        socket.setReuseAddress(true);
        socket.joinGroup(group);
        socket.setLoopbackMode(false);

        Thread listen = new Thread(this::listenLoop, "mcast-listen");
        listen.setDaemon(true);
        listen.start();

        Thread hb = new Thread(this::heartbeatLoop, "mcast-hb");
        hb.setDaemon(true);
        hb.start();

        running = true;

        // 3) premier HELLO + relai éventuel
        sendHello();

        if (withRelay) {
            try {
                RelayClient.register(peerId, this.pseudo, tcpPort, token);
                RelayPoller.start();
            } catch (Exception ignored) {}
        }
    }

    /** Arrête P2P + serveur TCP */
    public synchronized void stopP2PService() {
        running = false;
        try { if (socket != null) { socket.leaveGroup(group); socket.close(); } } catch (Exception ignored) {}
        stopCourseServer();
        visible.clear(); idSource.clear();
    }

    public boolean isServiceRunning() { return running; }

    /** Met à jour la liste des cours à partager (bouton "Rendre visible" côté Mes cours) */
    public synchronized void setSharedCourses(List<Course> courses) {
        // on mémorise pour rediffuser côté serveur si on redémarre/port change
        lastSharedCourses.clear();
        if (courses != null) lastSharedCourses.addAll(courses);

        // publier en multicast (même id que CourseServer: nom de fichier sans .crs)
        visible.clear();
        if (courses != null) {
            for (Course c : courses) {
                String path = c.getFilePath();                 // ex: data/courses/python.crs
                String file = (path == null) ? "" : new java.io.File(path).getName();
                String id = file.endsWith(".crs") ? file.substring(0, file.length() - 4) : file;
                if (id == null || id.isBlank()) continue;
                String title = (c.getTitle() == null || c.getTitle().isBlank()) ? id : c.getTitle();
                visible.add(new CourseAdvert(id, title, 1, 0).withNewExpiry(EXPIRY_MS));
            }
        }

        // pousser immédiatement au CourseServer
        if (courseServer != null) {
            courseServer.setSharedCourses(lastSharedCourses);
        }

        try { if (running) sendHello(); } catch (IOException ignored) {}
    }

    /** Retourne la liste des cours réseau pour l’UI (Recherche des cours) */
    public List<Course> getAllNetworkCourses() {
        List<Course> out = new ArrayList<>();
        for (DiscoveredPeers.Row r : DiscoveredPeers.get().snapshotRows()) {
            String host = "<relay>".equals(r.ip) ? resolveRelayHost() : r.ip;
            idSource.put(r.courseId, new HostPort(host, r.tcpPort));

            // On encode la source dans filePath : "p2p://host:port/courseId"
            String fp = "p2p://" + host + ":" + r.tcpPort + "/" + r.courseId;

            Course c = new Course(r.title, "", r.pseudo, fp);
            out.add(c);
        }
        return out;
    }

    /** Télécharge le .crs via le protocole binaire du CourseServer (UTF, length, bytes) */
    public byte[] downloadCourseBytes(Course course) throws IOException {
        if (course == null || course.getFilePath() == null) throw new IOException("Course/filePath nul");
        URI uri = URI.create(course.getFilePath()); // p2p://IP:port/courseId
        String host = uri.getHost();
        int port = (uri.getPort() == -1) ? tcpPort : uri.getPort();
        String courseId = uri.getPath().replaceFirst("^/", "");

        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try (Socket sock = new Socket()) {
                sock.connect(new InetSocketAddress(host, port), 5_000);
                sock.setSoTimeout(15_000);

                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
                     DataInputStream  in  = new DataInputStream(new BufferedInputStream(sock.getInputStream()))) {

                    out.writeUTF("GET");
                    out.writeUTF(courseId);
                    out.writeUTF("none"); // si besoin: token
                    out.flush();

                    String status = in.readUTF();
                    if (!"OK".equals(status)) {
                        String msg = in.readUTF();
                        throw new IOException("Serveur a répondu: " + msg);
                    }

                    long len = in.readLong();
                    if (len <= 0 || len > (512L * 1024 * 1024)) {
                        throw new IOException("Taille invalide: " + len);
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream((int) len);
                    byte[] buf = new byte[64 * 1024];
                    long rest = len;
                    while (rest > 0) {
                        int r = in.read(buf, 0, (int) Math.min(buf.length, rest));
                        if (r == -1) throw new EOFException("Flux interrompu");
                        baos.write(buf, 0, r);
                        rest -= r;
                    }
                    return baos.toByteArray();
                }
            } catch (IOException e) {
                last = e;
                try { Thread.sleep(300L * attempt); } catch (InterruptedException ignored) {}
            }
        }
        throw (last != null ? last : new IOException("Échec de téléchargement"));
    }

    // ====== Boucles réseau ======

    private void listenLoop() {
        byte[] buf = new byte[8192];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        while (running) {
            try {
                socket.receive(p);
                String json = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                HelloMessage m = Json.decode(json, HelloMessage.class);
                if (m == null || !"HELLO".equals(m.type)) continue;
                if (peerId != null && peerId.equals(m.peerId)) continue;

                DiscoveredPeers.get().updateFromHello(m, p.getAddress().getHostAddress());

                if (mainController != null) mainController.refreshP2PCourses();
            } catch (IOException ignored) {}
        }
    }

    private void heartbeatLoop() {
        while (running) {
            try {
                // rafraîchir TTL + renvoyer HELLO
                Set<CourseAdvert> snapshot = new HashSet<>();
                for (CourseAdvert c : visible) snapshot.add(c.withNewExpiry(EXPIRY_MS));
                visible.clear(); visible.addAll(snapshot);

                sendHello();
                Thread.sleep(HEARTBEAT_MS);
            } catch (Exception ignored) {}
        }
    }

    private void sendHello() throws IOException {
        if (peerId == null) return;
        HelloMessage msg = new HelloMessage(peerId, pseudo, tcpPort, new ArrayList<>(visible), token);
        byte[] data = Json.encode(msg).getBytes(StandardCharsets.UTF_8);
        DatagramPacket p = new DatagramPacket(data, data.length, group, MCAST_PORT);
        socket.send(p);
        try { RelayClient.announce(msg); } catch (Exception ignored) {}
    }

    // ====== Gestion CourseServer ======

    private void startCourseServerOnFreePort() throws IOException {
        if (courseServer != null) return;

        int start = tcpPort; // 5055 par défaut
        IOException last = null;
        for (int p = start; p < start + 16; p++) {
            try {
                CourseServer srv = new CourseServer(p);
                srv.start();
                this.courseServer = srv;
                this.tcpPort = p;

                // pousser la dernière liste visible dans le serveur
                if (!lastSharedCourses.isEmpty()) {
                    courseServer.setSharedCourses(lastSharedCourses);
                }
                System.out.println("[P2P] CourseServer écoute sur " + tcpPort);
                return;
            } catch (IOException e) {
                last = e; // essaie le port suivant
            }
        }
        throw (last != null ? last : new IOException("Aucun port libre entre " + start + " et " + (start + 15)));
    }

    private void stopCourseServer() {
        if (courseServer != null) {
            try { courseServer.stop(); } catch (Exception ignored) {}
            courseServer = null;
        }
    }

    // ====== Divers ======
    private String resolveRelayHost() {
        // Si tu implémentes un téléchargement via relai plus tard,
        // retourne ici l’hôte du relai. Pour l’instant, on renvoie loopback
        // pour éviter les NPE si une ligne relai apparaît.
        return "127.0.0.1";
    }
}
