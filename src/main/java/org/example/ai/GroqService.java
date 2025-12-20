package org.example.ai;

import org.example.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Implémentation LLM via Groq (API compatible OpenAI) */
public class GroqService implements LLMService {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    // Change ce modèle si ton compte n’y a pas accès :
    // Ex: "llama3-8b-8192", "llama-3.1-8b-instant", "llama-3.1-70b-versatile"
    private static final String MODEL = "llama-3.1-8b-instant";

    /** Clé fournie par LLM.get() (optionnelle). Si null, on lira AIConfig/env. */
    private final String overrideKey;

    /** Constructeur utilisé si tu veux laisser la clé venir des variables d’environnement. */
    public GroqService() {
        this.overrideKey = null;
    }

    /** Constructeur compatible avec LLM.get() qui passe déjà la clé. */
    public GroqService(String apiKey) {
        this.overrideKey = apiKey;
    }

    @Override
    public String generateNotionHtml(String subjectWithContext, String extraPrompt, int targetWords) throws Exception {
        // 1) Récupère la clé : priorité à celle passée au constructeur, sinon ENV
        AIConfig.bootstrapFromEnv(); // charge env et/ou config.json si présent
        String apiKey = (overrideKey != null && !overrideKey.isBlank())
                ? overrideKey
                : AIConfig.getApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Clé IA absente. Définis GROQ_API_KEY dans la configuration Run/Debug.");
        }

        // 2) Construire le prompt (surtout pas String.format -> pas de %d/pièges)
        String userPrompt = buildPrompt(subjectWithContext, extraPrompt, targetWords);

        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content",
                "Tu es un assistant pédagogique concis. " +
                        "Réponds UNIQUEMENT en HTML simple (titres <h3>, paragraphes <p>, listes <ul><li>, emphase <strong>). " +
                        "Ne mets pas de balises <html> ou <body>. Pas d'introduction superflue."
        ));
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);

        body.put("temperature", 0.4);
        body.put("max_tokens", 1200);

        String json = Json.encode(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (resp.statusCode() >= 300) {
            throw new IOException("Échec Groq (" + resp.statusCode() + "): " + resp.body());
        }

        Map<?, ?> parsed = Json.decode(resp.body(), Map.class);
        if (parsed == null) throw new IOException("Réponse Groq illisible");

        Object choicesObj = parsed.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new IOException("Réponse Groq vide (choices)");
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?,?> firstMap)) throw new IOException("Réponse Groq invalide (choice[0])");
        Object msgObj = firstMap.get("message");
        if (!(msgObj instanceof Map<?,?> msg)) throw new IOException("Réponse Groq invalide (message)");
        Object contentObj = msg.get("content");
        String content = (contentObj == null) ? null : contentObj.toString();

        if (content == null || content.isBlank()) {
            throw new IOException("Contenu vide renvoyé par Groq");
        }

        return content.trim();
    }

    private static String buildPrompt(String subjectWithContext, String extra, int words) {
        StringBuilder sb = new StringBuilder();
        sb.append("Sujet: ").append(subjectWithContext).append("\n");
        if (extra != null && !extra.isBlank()) {
            sb.append("Consignes supplémentaires: ").append(extra).append("\n");
        }
        sb.append("Objectif: Rédige une explication pédagogique BRÈVE et CLAIRE, environ ")
                .append(words)
                .append(" mots. Utilise uniquement du HTML simple (<h3>, <p>, <ul><li>, <strong>), ")
                .append("sans balises <html> ni <body>. Commence directement par l'essentiel.\n");
        return sb.toString();
    }
}
