package org.example.service;

import org.example.model.HelloMessage;
import org.example.util.Json;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class RelayPoller {
    public static void start() {
        Timer t = new Timer(true);
        t.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                String json = RelayClient.fetchCatalog();
                if (json == null || json.isEmpty()) return;
                HelloMessage[] arr = Json.decode(json, HelloMessage[].class);
                Arrays.stream(arr).forEach(h ->
                        DiscoveredPeers.get().updateFromHello(h, "<relay>"));
            }
        }, 0, 4000);
    }
}

