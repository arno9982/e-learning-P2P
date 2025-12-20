package org.example.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.model.HelloMessage;
import org.example.util.Json;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BootstrapServer {

    static class PeerInfo {
        public String peerId, pseudo, token; public int tcpPort;
        public long lastSeen;
    }

    private static final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private static final List<HelloMessage> catalog = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress(8080), 0);

        srv.createContext("/register", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes());
            PeerInfo m = Json.decode(body, PeerInfo.class);
            m.lastSeen = System.currentTimeMillis();
            peers.put(m.peerId, m);
            ok(ex, "OK");
        });

        srv.createContext("/announce", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes());
            HelloMessage h = Json.decode(body, HelloMessage.class);
            PeerInfo p = peers.get(h.peerId);
            if (p == null) { ex.sendResponseHeaders(403, 0); ex.getResponseBody().close(); return; }
            synchronized (catalog) {
                catalog.removeIf(x -> x.peerId.equals(h.peerId));
                catalog.add(h);
            }
            ok(ex, "OK");
        });

        srv.createContext("/catalog", ex -> {
            long now = Instant.now().getEpochSecond();
            synchronized (catalog) {
                catalog.removeIf(h -> h.visibleCourses == null || h.visibleCourses.isEmpty()
                        || h.visibleCourses.stream().allMatch(c -> c.expiresAt < now));
                ok(ex, Json.encode(catalog));
            }
        });

        srv.start();
        System.out.println("BootstrapServer ON : http://localhost:8080");
    }

    private static void ok(HttpExchange ex, String s) throws IOException {
        ex.getResponseHeaders().add("Content-Type","application/json; charset=utf-8");
        byte[] b = s.getBytes();
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
}
