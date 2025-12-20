package org.example.service;

import org.example.model.HelloMessage;
import org.example.util.Json;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class RelayClient {
    public static String RELAY_BASE = "http://localhost:8080"; // <- mets l’IP de ton relai

    public static void register(String peerId, String pseudo, int tcpPort, String token){
        try {
            post("/register", String.format(
                    "{\"peerId\":\"%s\",\"pseudo\":\"%s\",\"tcpPort\":%d,\"token\":\"%s\"}",
                    peerId, pseudo, tcpPort, token));
        } catch (Exception ignored) {}
    }

    public static void announce(HelloMessage hello){
        try { post("/announce", Json.encode(hello)); } catch (Exception ignored) {}
    }

    public static String fetchCatalog(){
        try { return get("/catalog"); } catch (Exception e) { return null; }
    }

    private static String get(String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(RELAY_BASE + path).openConnection();
        c.setRequestMethod("GET");
        try (InputStream in = c.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void post(String path, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(RELAY_BASE + path).openConnection();
        c.setDoOutput(true); c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type","application/json");
        try (OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
        c.getInputStream().close();
    }
}

