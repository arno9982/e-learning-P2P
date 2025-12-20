// src/main/java/org/example/service/CourseServer.java
package org.example.service;

import org.example.model.Course;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Serveur P2P (protocole binaire UTF via DataInput/DataOutput).
 *
 * Requête client :
 *   writeUTF("GET"); writeUTF(courseId); writeUTF(token)
 *
 * Réponses serveur :
 *   OK  : writeUTF("OK"); writeLong(length); puis bytes du .crs
 *   ERR : writeUTF("ERR"); writeUTF(message)
 */
public class CourseServer {

    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int BUF_SIZE        = 64 * 1024;
    private static final long MAX_LEN        = 200L * 1024 * 1024; // 200 Mo sécurité

    private final int port;
    private volatile boolean running;
    private ServerSocket server;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    /** cours visibles (courseId -> chemin .crs) */
    private final Map<String, Path> visible = new ConcurrentHashMap<>();

    public CourseServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (running) return;
        server = new ServerSocket();
        server.setReuseAddress(true);
        // écoute sur toutes les interfaces pour le LAN
        server.bind(new InetSocketAddress("0.0.0.0", port));
        running = true;

        Thread t = new Thread(this::acceptLoop, "course-server");
        t.setDaemon(true);
        t.start();

        System.out.println("[CourseServer] listening on 0.0.0.0:" + port);
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
    }

    /** Appelé par l'UI (Mes cours → Rendre visible/Masquer) */
    public void setSharedCourses(List<Course> courses) {
        visible.clear();
        if (courses == null) {
            System.out.println("[CourseServer] visible = {}");
            return;
        }
        for (Course c : courses) {
            try {
                Path p = Paths.get(c.getFilePath());
                if (!Files.exists(p)) continue;
                String fileName = p.getFileName().toString();
                // id = nom de fichier sans .crs
                String id = fileName.endsWith(".crs") ? fileName.substring(0, fileName.length() - 4) : fileName;
                if (!id.isBlank()) visible.put(id, p);
            } catch (Exception ignored) {}
        }
        System.out.println("[CourseServer] visible = " + visible.keySet().stream().sorted().collect(Collectors.toList()));
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                pool.submit(() -> handleClient(s));
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }
    }

    private void handleClient(Socket s) {
        try (s;
             DataInputStream  in  = new DataInputStream(new BufferedInputStream(s.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()))) {

            s.setSoTimeout(READ_TIMEOUT_MS);
            s.setTcpNoDelay(true);

            // --- lecture requête ---
            String cmd      = in.readUTF();     // "GET"
            String courseId = in.readUTF();     // ex: "python" (sans .crs)
            String token    = in.readUTF();     // ignoré ici, mais prêt si tu veux vérifier

            System.out.println("[CourseServer] " + s.getRemoteSocketAddress() + " -> " + cmd + " " + courseId);

            if (!"GET".equals(cmd)) {
                sendErr(out, "Bad command");
                return;
            }

            // sécurité courseId
            courseId = courseId.strip();
            if (courseId.endsWith(".crs")) courseId = courseId.substring(0, courseId.length() - 4);

            Path coursePath = visible.get(courseId);
            if (coursePath == null) {
                sendErr(out, "Not visible");
                System.out.println("[CourseServer] not visible: " + courseId);
                return;
            }
            if (!Files.exists(coursePath)) {
                sendErr(out, "File not found");
                System.out.println("[CourseServer] file not found: " + coursePath);
                return;
            }

            long len = Files.size(coursePath);
            if (len < 0 || len > MAX_LEN) {
                sendErr(out, "File too large or invalid size");
                System.out.println("[CourseServer] refused size " + len + " for " + courseId);
                return;
            }

            // --- en-tête OK ---
            out.writeUTF("OK");
            out.writeLong(len);
            out.flush(); // important : réveille le client avant le gros flux

            // --- envoi du fichier ---
            long sent = 0;
            try (InputStream fis = Files.newInputStream(coursePath, StandardOpenOption.READ)) {
                byte[] buf = new byte[BUF_SIZE];
                int r;
                while ((r = fis.read(buf)) != -1) {
                    out.write(buf, 0, r);
                    sent += r;
                }
            }
            out.flush();
            System.out.println("[CourseServer] sent " + courseId + " " + sent + "/" + len + " bytes to " + s.getRemoteSocketAddress());

        } catch (IOException e) {
            System.err.println("[CourseServer] client error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void sendErr(DataOutputStream out, String msg) throws IOException {
        out.writeUTF("ERR");
        out.writeUTF(msg == null ? "error" : msg);
        out.flush();
    }
}
