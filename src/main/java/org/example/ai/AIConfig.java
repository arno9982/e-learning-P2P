package org.example.ai;

import org.example.util.Json;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/** Config IA : corrigée pour utiliser le dossier AppData autorisé */
public final class AIConfig {
    
    // CORRECTION : On utilise une méthode pour obtenir le chemin dynamique défini dans Main
    private static Path getConfDir() {
        String basePath = System.getProperty("app.base.path", System.getProperty("user.home"));
        return Paths.get(basePath, "data");
    }

    private static Path getConfFile() {
        return getConfDir().resolve("config.json");
    }

    private static volatile String provider = "GROQ"; 
    private static volatile String apiKey;
    private static volatile boolean bootstrapped = false;

    public static synchronized void load() {
        Path confFile = getConfFile();
        if (Files.exists(confFile)) {
            try {
                String json = Files.readString(confFile, StandardCharsets.UTF_8);
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

    public static synchronized void bootstrapFromEnv() {
        if (bootstrapped) return;
        bootstrapped = true;

        String p = System.getenv("AI_PROVIDER");
        if (p != null && !p.isBlank()) provider = p.trim().toUpperCase();

        String envKey = System.getenv("GROQ_API_KEY");
        if (envKey == null || envKey.isBlank()) envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.isBlank()) apiKey = envKey.trim();
    }

    public static synchronized void save() {
        try {
            Path confDir = getConfDir();
            if (!Files.exists(confDir)) Files.createDirectories(confDir);
            
            Map<String,Object> m = new HashMap<>();
            m.put("ai_provider", provider);
            m.put("ai_api_key", apiKey == null ? "" : apiKey);
            
            Files.writeString(getConfFile(), Json.encode(m), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }

    public static String getProvider() { return provider; }
    public static String getApiKey()   { return apiKey;   }
    public static void setProvider(String p) { provider = (p==null?"GROQ":p.trim().toUpperCase()); }
    public static void setApiKey(String k)   { apiKey   = (k==null?null:k.trim()); }

    private AIConfig() {}
}