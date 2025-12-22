package org.example.service;

import javafx.application.Platform;
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

public class P2PService {

    private static P2PService INSTANCE;
    public static synchronized P2PService getInstance() {
        if (INSTANCE == null) INSTANCE = new P2PService();
        return INSTANCE;
    }
    private P2PService() {}

    public static final String MCAST_ADDR = "239.255.42.42";
    public static final int MCAST_PORT = 42424;
    private static final int HEARTBEAT_MS = 3000;
    private static final int EXPIRY_MS = HEARTBEAT_MS * 5;

    private String peerId;
    private String pseudo;
    private String token;
    private int tcpPort = 5055;

    private final Set<CourseAdvert> visible = ConcurrentHashMap.newKeySet();
    private final Map<String, HostPort> idSource = new ConcurrentHashMap<>();
    private record HostPort(String host, int port) {}

    private MulticastSocket socket;
    private InetAddress group;
    private volatile boolean running;
    private CourseServer courseServer;
    private final List<Course> lastSharedCourses = new CopyOnWriteArrayList<>();
    private MainController mainController;
    private boolean iHaveTeacherRole = false; 

public void setIHaveTeacherRole(boolean active) {
    this.iHaveTeacherRole = active;
}

    public void setMainController(MainController mainController) { this.mainController = mainController; }

    public synchronized void startP2PService(String pseudo, boolean withRelay) throws IOException {
        if (running) return;
        this.peerId = UUID.randomUUID().toString();
        this.token  = UUID.randomUUID().toString();
        this.pseudo = (pseudo == null || pseudo.isBlank()) ? "user" : pseudo.trim();

        // 1) Serveur TCP local
        startCourseServerOnFreePort();

        // 2) Multicast corrigé pour le WiFi
        group = InetAddress.getByName(MCAST_ADDR);
        socket = new MulticastSocket(MCAST_PORT);
        socket.setReuseAddress(true);
        
        // Sélection de l'interface réseau (WiFi/Ethernet au lieu de Virtuelle)
        NetworkInterface ni = findBestNetworkInterface();
        if (ni != null) {
            socket.setNetworkInterface(ni);
            socket.joinGroup(new InetSocketAddress(group, MCAST_PORT), ni);
            System.out.println("[P2P] Interface choisie : " + ni.getDisplayName());
        } else {
            // Fallback si aucune interface spécifique n'est trouvée
            socket.joinGroup(group);
        }

        socket.setTimeToLive(2); // Autorise le passage de switchs réseau locaux
        socket.setLoopbackMode(false);

        Thread listen = new Thread(this::listenLoop, "mcast-listen");
        listen.setDaemon(true);
        listen.start();

        Thread hb = new Thread(this::heartbeatLoop, "mcast-hb");
        hb.setDaemon(true);
        hb.start();

        running = true;
        sendHello();

        if (withRelay) {
            try {
                RelayClient.register(peerId, this.pseudo, tcpPort, token);
                RelayPoller.start();
            } catch (Exception ignored) {}
        }
    }

    /** Trouve l'interface réseau active (WiFi ou LAN) en ignorant les cartes virtuelles */
    private NetworkInterface findBestNetworkInterface() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (ni.isLoopback() || !ni.isUp() || !ni.supportsMulticast() || ni.isVirtual()) continue;
            
            // On privilégie les interfaces WiFi ou Ethernet physiques
            String name = ni.getName().toLowerCase();
            if (name.contains("wlan") || name.contains("eth") || name.contains("en")) {
                return ni;
            }
        }
        return null;
    }

   private void listenLoop() {
    while (running) {
        try {
            byte[] buf = new byte[8192];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            socket.receive(p);

            String senderIp = p.getAddress().getHostAddress();
            String json = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
            HelloMessage m = Json.decode(json, HelloMessage.class);

            if (m != null && "HELLO".equals(m.type)) {
                
                // CORRECTION : Si c'est un prof, on configure l'IP du relais automatiquement
                if (m.isTeacher && (peerId == null || !peerId.equals(m.peerId))) {
                    RelayClient.RELAY_BASE = "http://" + senderIp + ":8080";
                    // Optionnel : System.out.println("Relais auto-configuré sur : " + senderIp);
                }

                if (peerId != null && !peerId.equals(m.peerId)) {
                    DiscoveredPeers.get().updateFromHello(m, senderIp);
                    if (mainController != null) {
                        Platform.runLater(() -> mainController.refreshP2PCourses());
                    }
                }
            }
        } catch (IOException e) {
            if (running) System.err.println("[P2P] Erreur réception : " + e.getMessage());
        }
    }
}

    private void heartbeatLoop() {
        while (running) {
            try {
                Set<CourseAdvert> snapshot = new HashSet<>();
                for (CourseAdvert c : visible) snapshot.add(c.withNewExpiry(EXPIRY_MS));
                visible.clear(); visible.addAll(snapshot);

                sendHello();
                Thread.sleep(HEARTBEAT_MS);
            } catch (Exception ignored) {}
        }
    }

  private void sendHello() throws IOException {
    if (peerId == null || socket == null) return;
    
    // Ajout de iHaveTeacherRole à la fin
    HelloMessage msg = new HelloMessage(peerId, pseudo, tcpPort, new ArrayList<>(visible), token, iHaveTeacherRole);
    
    byte[] data = Json.encode(msg).getBytes(StandardCharsets.UTF_8);
    DatagramPacket p = new DatagramPacket(data, data.length, group, MCAST_PORT);
    socket.send(p);
    try { RelayClient.announce(msg); } catch (Exception ignored) {}
}

    public synchronized void setSharedCourses(List<Course> courses) {
        lastSharedCourses.clear();
        if (courses != null) lastSharedCourses.addAll(courses);

        visible.clear();
        if (courses != null) {
            for (Course c : courses) {
                String path = c.getFilePath();
                String file = (path == null) ? "" : new java.io.File(path).getName();
                String id = file.endsWith(".crs") ? file.substring(0, file.length() - 4) : file;
                if (id == null || id.isBlank()) continue;
                String title = (c.getTitle() == null || c.getTitle().isBlank()) ? id : c.getTitle();
                visible.add(new CourseAdvert(id, title, 1, 0).withNewExpiry(EXPIRY_MS));
            }
        }

        if (courseServer != null) {
            courseServer.setSharedCourses(lastSharedCourses);
        }

        try { if (running) sendHello(); } catch (IOException ignored) {}
    }

    public List<Course> getAllNetworkCourses() {
        List<Course> out = new ArrayList<>();
        for (DiscoveredPeers.Row r : DiscoveredPeers.get().snapshotRows()) {
            String host = "<relay>".equals(r.ip) ? resolveRelayHost() : r.ip;
            idSource.put(r.courseId, new HostPort(host, r.tcpPort));
            String fp = "p2p://" + host + ":" + r.tcpPort + "/" + r.courseId;
            Course c = new Course(r.title, "", r.pseudo, fp);
            out.add(c);
        }
        return out;
    }

    public byte[] downloadCourseBytes(Course course) throws IOException {
        if (course == null || course.getFilePath() == null) throw new IOException("Course/filePath nul");
        URI uri = URI.create(course.getFilePath());
        String host = uri.getHost();
        int port = (uri.getPort() == -1) ? tcpPort : uri.getPort();
        String courseId = uri.getPath().replaceFirst("^/", "");

        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try (Socket sock = new Socket()) {
                sock.connect(new InetSocketAddress(host, port), 5_000);
                sock.setSoTimeout(15_000);

                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
                     DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()))) {

                    out.writeUTF("GET");
                    out.writeUTF(courseId);
                    out.writeUTF("none");
                    out.flush();

                    String status = in.readUTF();
                    if (!"OK".equals(status)) {
                        String msg = in.readUTF();
                        throw new IOException("Serveur a répondu : " + msg);
                    }

                    long len = in.readLong();
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

    private void startCourseServerOnFreePort() throws IOException {
        if (courseServer != null) return;
        int start = tcpPort;
        for (int p = start; p < start + 16; p++) {
            try {
                CourseServer srv = new CourseServer(p);
                srv.start();
                this.courseServer = srv;
                this.tcpPort = p;
                if (!lastSharedCourses.isEmpty()) courseServer.setSharedCourses(lastSharedCourses);
                return;
            } catch (IOException ignored) {}
        }
        throw new IOException("Aucun port TCP libre trouvé.");
    }

    public synchronized void stopP2PService() {
        running = false;
        try { if (socket != null) { socket.leaveGroup(group); socket.close(); } } catch (Exception ignored) {}
        if (courseServer != null) { courseServer.stop(); courseServer = null; }
        visible.clear(); idSource.clear();
    }

    public boolean isServiceRunning() { return running; }
    private String resolveRelayHost() { return "127.0.0.1"; }
}