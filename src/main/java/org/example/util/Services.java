package org.example.util;

import org.example.service.P2PService;
import org.example.service.CourseRepository;

public class Services {

    /** À appeler depuis Paramètres après saisie du pseudo (pas de login) */
    public static void bootstrapP2P(String pseudo) {
        try {
            // (optionnel) si tu utilises le repo pour remplir "Mes cours"
            CourseRepository.reloadAllCourses();
            // Démarre le P2P (multicast + relai) via le singleton
            P2PService.getInstance().startP2PService(pseudo, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void stopP2P() {
        try {
            P2PService.getInstance().stopP2PService();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
