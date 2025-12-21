package org.example.util;

import java.io.File;

public class PathUtil {
    
    /**
     * Retourne le chemin racine pour stocker les données de l'application
     * de manière autorisée sur chaque OS.
     */
    public static String getBasePath() {
        // On récupère la propriété définie dans le Main, 
        // sinon on utilise le dossier utilisateur par défaut.
        return System.getProperty("app.base.path", System.getProperty("user.home"));
    }

    /**
     * Retourne le dossier spécifique aux cours.
     */
    public static File getCoursesFolder() {
        File f = new File(getBasePath(), "courses");
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }
}