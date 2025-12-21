package org.example.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gère l'exportation d'un cours au format SCORM.
 */
public class ScormExporter {

    public static void exportToScorm(Path sourcePath, Path destinationPath) throws IOException {
        // Sécurité : Vérifie que le dossier de destination existe (dans AppData ou ailleurs)
        Path parentDir = destinationPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        System.out.println("Début de l'exportation SCORM...");
        System.out.println("Source (.crs) : " + sourcePath);
        System.out.println("Destination (.zip) : " + destinationPath);

        // TODO: La logique de création du ZIP et du imsmanifest.xml viendra ici
        
        System.out.println("Exportation SCORM terminée.");
    }
}