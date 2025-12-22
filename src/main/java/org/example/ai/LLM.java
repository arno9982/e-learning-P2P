package org.example.ai;

public final class LLM {
    private static volatile LLMService INSTANCE;
    private static volatile String cacheProvider;
    private static volatile String cacheKey;

    public static synchronized LLMService get() {
        // 1. On charge la configuration depuis le fichier data/config.json
        AIConfig.load();

        String provider = AIConfig.getProvider();
        String key      = AIConfig.getApiKey();

        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Clé IA absente. Veuillez la configurer dans les paramètres de l'application.");
        }

        // 2. Si le fournisseur ou la clé a changé, on recrée l'instance
        if (INSTANCE == null || !provider.equals(cacheProvider) || !key.equals(cacheKey)) {
            // GroqService est maintenant notre client universel compatible OpenAI
            INSTANCE = new GroqService(key);
            
            cacheProvider = provider;
            cacheKey = key;
        }
        return INSTANCE;
    }

    private LLM() {}
}