package org.example.ai;

import org.example.util.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GroqService implements LLMService {

    @Override
    public String generateNotionHtml(String title, String extraPrompt, int words) throws Exception {
        
        // On récupère les valeurs actuelles (mis à jour par l'UI)
        String apiKey = AIConfig.getApiKey();
        String url = AIConfig.getEndpoint();
        String model = AIConfig.getModel();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Clé API manquante. Veuillez la saisir dans les paramètres.");
        }

        String userPrompt = buildPrompt(title, extraPrompt, words);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "Tu es un assistant pédagogique. Réponds UNIQUEMENT en HTML simple."),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("temperature", 0.4);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.encode(body), StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (resp.statusCode() >= 300) {
            throw new IOException("Erreur API (" + resp.statusCode() + "): " + resp.body());
        }

        // Extraction de la réponse
        Map<?, ?> parsed = Json.decode(resp.body(), Map.class);
        List<?> choices = (List<?>) parsed.get("choices");
        Map<?, ?> first = (Map<?, ?>) choices.get(0);
        Map<?, ?> msg = (Map<?, ?>) first.get("message");
        
        return msg.get("content").toString().trim();
    }

    private String buildPrompt(String title, String extra, int words) {
        return "Sujet: " + title + "\nConsignes: " + extra + "\nLongueur: " + words + " mots environ.";
    }
}