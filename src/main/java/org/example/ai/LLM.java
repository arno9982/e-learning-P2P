package org.example.ai;

public final class LLM {
    private static volatile LLMService INSTANCE;
    private static volatile String cacheProvider;
    private static volatile String cacheKey;

    public static synchronized LLMService get() {
        // ordre : fichier -> ENV (l’ENV écrase si présent)
        AIConfig.load();
        AIConfig.bootstrapFromEnv();

        String provider = AIConfig.getProvider();
        String key      = AIConfig.getApiKey();

        if (key == null || key.isBlank())
            throw new IllegalStateException("Clé IA absente. Définis GROQ_API_KEY (ou OPENAI_API_KEY).");

        if (INSTANCE == null || !provider.equals(cacheProvider) || !key.equals(cacheKey)) {
            switch (provider) {
                case "GROQ"   -> INSTANCE = new GroqService(key);
                case "OPENAI" -> INSTANCE = new OpenAIService(key);
                default       -> throw new IllegalStateException("Provider IA inconnu: " + provider);
            }
            cacheProvider = provider;
            cacheKey = key;
        }
        return INSTANCE;
    }

    private LLM() {}
}
