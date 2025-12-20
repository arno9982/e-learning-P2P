package org.example.ai;

import org.example.util.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/** Config IA : lit d'abord les variables d'environnement, puis (optionnel) data/config.json */
public final class AIConfig {
    private static final Path CONF_DIR  = Paths.get("data");
    private static final Path CONF_FILE = CONF_DIR.resolve("config.json");

    private static volatile String provider = "GROQ"; // GROQ ou OPENAI
    private static volatile String apiKey;
    private static volatile boolean bootstrapped = false;

    /** Charge depuis data/config.json si présent (optionnel) */
    public static synchronized void load() {
        if (Files.exists(CONF_FILE)) {
            try {
                String json = Files.readString(CONF_FILE, StandardCharsets.UTF_8);
                Map<?,?> m = Json.decode(json, Map.class);
                if (m != null) {
                    Object p = m.get("ai_provider");
                    Object k = m.get("ai_api_key");
                    if (p != null && !p.toString().isBlank()) provider = p.toString().trim().toUpperCase();
                    if (k != null && !k.toString().isBlank())  apiKey   = k.toString().trim();
                }
            } catch (Exception ignored) {}
        }
    }

    /** Injection silencieuse depuis l'environnement (prioritaire, non persisté) */
    public static synchronized void bootstrapFromEnv() {
        if (bootstrapped) return;
        bootstrapped = true;

        String p = System.getenv("AI_PROVIDER");  // "GROQ" ou "OPENAI"
        if (p != null && !p.isBlank()) provider = p.trim().toUpperCase();

        String envKey = System.getenv("GROQ_API_KEY");
        if (envKey == null || envKey.isBlank()) envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.isBlank()) apiKey = envKey.trim();
    }

    /** (Optionnel) persistance sur disque si tu veux garder une conf locale */
    public static synchronized void save() {
        try {
            if (!Files.exists(CONF_DIR)) Files.createDirectories(CONF_DIR);
            Map<String,Object> m = new HashMap<>();
            m.put("ai_provider", provider);
            m.put("ai_api_key", apiKey == null ? "" : apiKey);
            Files.writeString(CONF_FILE, Json.encode(m), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }

    public static String getProvider() { return provider; }
    public static String getApiKey()   { return apiKey;   }
    public static void setProvider(String p) { provider = (p==null?"GROQ":p.trim().toUpperCase()); }
    public static void setApiKey(String k)   { apiKey   = (k==null?null:k.trim()); }

    private AIConfig() {}
}
