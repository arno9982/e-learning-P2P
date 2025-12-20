package org.example.service;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gère les .crs locaux dans data/courses/
 * - lire un .crs (pour le serveur)
 * - installer un .crs téléchargé
 * - exposer une liste observable pour "Mes cours"
 *
 * NOTE: on ne dépend PAS de ton model.Course ici.
 * Si ton écran "Mes cours" lit déjà le dossier, tu peux garder ta logique.
 * Sinon, lie ta TableView à getObservableCourses() + reloadAllCourses().
 */
public class CourseRepository {

    public static final Path COURSES_DIR = Paths.get("data", "courses");
    private static final ObservableList<Path> COURSES = FXCollections.observableArrayList();

    static {
        try { Files.createDirectories(COURSES_DIR); } catch (IOException ignored) {}
    }

    /** Lire un .crs local en bytes (id = nom de fichier sans extension OU identifiant distant) */
    public static byte[] loadAsCrs(String idOrFileName) throws IOException {
        Path p = resolveCoursePath(idOrFileName);
        return Files.readAllBytes(p);
    }

    private static Path resolveCoursePath(String idOrFileName) {
        Path p = idOrFileName.endsWith(".crs") ?
                COURSES_DIR.resolve(idOrFileName) :
                COURSES_DIR.resolve(idOrFileName + ".crs");
        if (!Files.exists(p)) {
            // fallback : quand l'id distant ne matche pas le nom local — on tente une recherche
            try {
                return Files.list(COURSES_DIR)
                        .filter(f -> f.getFileName().toString().endsWith(idOrFileName + ".crs")
                                || f.getFileName().toString().equals(idOrFileName))
                        .findFirst().orElse(p);
            } catch (IOException ignored) {}
        }
        return p;
    }

    /** Installer un cours téléchargé dans data/courses et rafraîchir la liste */
    public static String installDownloadedCourse(byte[] data, String remoteCourseId, String title) throws IOException {
        String safeTitle = sanitize(title);
        String baseName = (safeTitle.isEmpty() ? "course" : safeTitle) + "-" + remoteCourseId;
        String fileName = uniqueFileName(baseName, ".crs");
        Path dst = COURSES_DIR.resolve(fileName);
        Files.write(dst, data, StandardOpenOption.CREATE_NEW);

        DownloadedCoursesRegistry.markDownloaded(remoteCourseId);
        reloadAllCourses();
        return fileName;
    }

    /** Rescan du dossier pour TableView (si tu l’utilises) */
    public static void reloadAllCourses() {
        try {
            List<Path> list = Files.list(COURSES_DIR)
                    .filter(p -> p.toString().endsWith(".crs"))
                    .sorted()
                    .collect(Collectors.toList());
            COURSES.setAll(list);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Liste observable de fichiers .crs (optionnelle si tu as déjà ta propre liste/DAO) */
    public static ObservableList<Path> getObservableCourses() {
        return COURSES;
    }

    // Helpers
    private static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[^a-zA-Z0-9-_]", "_").trim();
    }
    private static String uniqueFileName(String base, String ext) {
        String candidate = base + ext;
        int i = 1;
        while (Files.exists(COURSES_DIR.resolve(candidate))) {
            candidate = base + "_" + (i++) + ext;
        }
        return candidate;
    }
}
