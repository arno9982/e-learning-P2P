package org.example.ai;

public interface LLMService {
    /**
     * Génère du HTML pédagogique pour une notion.
     * @param title  Titre de la notion
     * @param prompt Consignes / contenu attendu
     * @param words  Longueur cible (en mots)
     */
    String generateNotionHtml(String title, String prompt, int words) throws Exception;
}

