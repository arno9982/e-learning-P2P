package org.example.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gère l'importation d'un package SCORM.
 */
public class ScormImporter {

    public static void importScorm(Path sourcePath, Path destinationDirectory) throws IOException {
        // PROTECTION : Si aucun dossier n'est fourni, on utilise le dossier sécurisé défini dans CourseRepository
        if (destinationDirectory == null) {
            destinationDirectory = CourseRepository.getCoursesDir();
        }

        if (!Files.exists(destinationDirectory)) {
            Files.createDirectories(destinationDirectory);
        }

        System.out.println("Début de l'importation SCORM...");
        System.out.println("Fichier source : " + sourcePath);
        System.out.println("Dossier cible : " + destinationDirectory);

        // TODO: La logique de décompression et lecture XML viendra ici

        System.out.println("Importation SCORM terminée.");
    }
}