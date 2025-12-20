package org.example.ai;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** OpenAI "comme ChatGPT" (si tu veux OPENAI au lieu de GROQ) */
public class OpenAIService implements LLMService {
    private final String apiKey;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public OpenAIService(String apiKey) { this.apiKey = apiKey; }

    @Override
    public String generateNotionHtml(String title, String prompt, int words) throws Exception {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("Clé OpenAI manquante.");

        int target = Math.max(80, Math.min(250, (words<=0?150:words)));
        String system = """
                Tu es ChatGPT, assistant pédagogique FR.
                Retourne du HTML affichable (sans <html>/<body>), 1–2 <p>, bref et clair.
                """;
        String user = "NOTION: " + ns(title) + "\nCONSIGHES: " + ns(prompt) + "\nLONGUEUR: ~" + target + " mots.";

        String body = """
        {
          "model": "gpt-4o-mini",
          "messages": [
            {"role":"system","content": %s},
            {"role":"user","content": %s}
          ],
          "temperature": 0.5,
          "max_tokens": %d
        }
        """.formatted(jq(system), jq(user), Math.min(1200, target*6));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type","application/json")
                .header("Authorization","Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp;
        try { resp = client.send(req, HttpResponse.BodyHandlers.ofString()); }
        catch (Exception net) { throw new IllegalStateException("Pas d'Internet ou API OpenAI injoignable."); }

        if (resp.statusCode() < 200 || resp.statusCode() >= 300)
            throw new IllegalStateException("Échec OpenAI (HTTP " + resp.statusCode() + ")");

        String html = extractFirstMessage(resp.body());
        if (html == null || html.isBlank())
            throw new IllegalStateException("Réponse vide du modèle.");
        return html.trim();
    }

    private static String ns(String s){ return s==null? "": s; }
    private static String jq(String s){ return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\""; }

    private static String extractFirstMessage(String json){
        int i = json.indexOf("\"content\"");
        if (i < 0) return null;
        int q1 = json.indexOf('"', i + 10);
        if (q1 < 0) return null;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int k = q1 + 1; k < json.length(); k++) {
            char c = json.charAt(k);
            if (esc) { sb.append(c == 'n' ? '\n' : c); esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') break;
            sb.append(c);
        }
        return sb.toString();
    }
}
