package org.example.ai;

public class LocalLLMService implements LLMService {
    @Override
    public String generateNotionHtml(String title, String prompt, int words) {
        String t = (title == null || title.isBlank()) ? "Nouvelle notion" : title;
        String p = (prompt == null || prompt.isBlank()) ? "Contenu pédagogique" : prompt;

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(escape(t)).append("</h2>");
        sb.append("<p>").append(escape(p)).append("</p>");
        sb.append("<h3>Points clés</h3><ul>");
        sb.append("<li>Définition principale</li>");
        sb.append("<li>Exemple expliqué étape par étape</li>");
        sb.append("<li>Bonnes pratiques et pièges à éviter</li>");
        sb.append("</ul>");
        sb.append("<h3>Exercice rapide</h3>");
        sb.append("<ol><li>Question 1</li><li>Question 2</li></ol>");
        return sb.toString();
    }
    private static String escape(String s){
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}

