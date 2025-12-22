package org.example.ai;

import org.example.util.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public final class AIConfig {
    
    private static Path getConfFile() {
        String basePath = System.getProperty("app.base.path", System.getProperty("user.home"));
        return Paths.get(basePath, "data", "config.json");
    }

    private static volatile String provider = "OPENAI"; 
    private static volatile String apiKey = "";
    private static volatile String endpoint = "https://api.openai.com/v1/chat/completions";
    private static volatile String model = "gpt-4-turbo";

    /** Charge les paramètres depuis le fichier config.json au démarrage */
    public static synchronized void load() {
        Path file = getConfFile();
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                Map<?,?> m = Json.decode(json, Map.class);
                if (m != null) {
                    if (m.get("ai_provider") != null) setProviderOnly(m.get("ai_provider").toString());
                    if (m.get("ai_api_key") != null) apiKey = m.get("ai_api_key").toString();
                }
            } catch (Exception ignored) {}
        }
    }

    /** Met à jour les paramètres selon le choix UI et sauvegarde */
    public static synchronized void updateConfig(String newProvider, String newKey) {
        setProviderOnly(newProvider);
        apiKey = (newKey == null) ? "" : newKey.trim();
        save();
    }

    private static void setProviderOnly(String p) {
        provider = (p == null) ? "OPENAI" : p.toUpperCase();
        if ("GROQ".equals(provider)) {
            endpoint = "https://api.groq.com/openai/v1/chat/completions";
            model = "llama-3.1-8b-instant";
        } else {
            endpoint = "https://api.openai.com/v1/chat/completions";
            model = "gpt-4-turbo";
        }
    }

    public static synchronized void save() {
        try {
            Path file = getConfFile();
            if (!Files.exists(file.getParent())) Files.createDirectories(file.getParent());
            Map<String, Object> m = new HashMap<>();
            m.put("ai_provider", provider);
            m.put("ai_api_key", apiKey);
            Files.writeString(file, Json.encode(m), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }

    public static String getApiKey()   { return apiKey; }
    public static String getEndpoint() { return endpoint; }
    public static String getModel()    { return model; }
    public static String getProvider() { return provider; }

    private AIConfig() {}
}