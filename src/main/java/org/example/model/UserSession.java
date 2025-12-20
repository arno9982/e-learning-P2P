package org.example.model;

public class UserSession {

    private static UserSession instance;

    private int userId;
    private String username;
    private String role;

    // Constructeur privé pour empêcher l'instanciation directe (pattern Singleton)
    private UserSession(int userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    // Méthode statique pour obtenir l'instance de la session
    public static UserSession getInstance() {
        return instance;
    }

    // Méthode pour se connecter
    public static void login(int userId, String username, String role) {
        instance = new UserSession(userId, username, role);
    }

    // Méthode pour se déconnecter
    public void logout() {
        instance = null;
    }

    // Getters pour récupérer les informations de la session
    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}
