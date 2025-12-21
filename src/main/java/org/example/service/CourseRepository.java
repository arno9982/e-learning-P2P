package org.example.service;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.util.PathUtil; // Assure-toi d'avoir créé PathUtil comme vu précédemment

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class CourseRepository {

    // On utilise une méthode au lieu d'une constante statique fixe pour garantir le bon chemin
    public static Path getCoursesDir() {
        // PathUtil.getBasePath() retourne le chemin vers AppData (Windows) ou .e-learning-p2p (Linux)
        Path p = Paths.get(System.getProperty("app.base.path", System.getProperty("user.home")), "courses");
        try { 
            if (!Files.exists(p)) Files.createDirectories(p); 
        } catch (IOException ignored) {}
        return p;
    }

    private static final ObservableList<Path> COURSES = FXCollections.observableArrayList();

    /** Lire un .crs local en bytes */
    public static byte[] loadAsCrs(String idOrFileName) throws IOException {
        Path p = resolveCoursePath(idOrFileName);
        return Files.readAllBytes(p);
    }

    private static Path resolveCoursePath(String idOrFileName) {
        Path dir = getCoursesDir();
        Path p = idOrFileName.endsWith(".crs") ?
                dir.resolve(idOrFileName) :
                dir.resolve(idOrFileName + ".crs");
        
        if (!Files.exists(p)) {
            try {
                return Files.list(dir)
                        .filter(f -> f.getFileName().toString().endsWith(idOrFileName + ".crs")
                                || f.getFileName().toString().equals(idOrFileName))
                        .findFirst().orElse(p);
            } catch (IOException ignored) {}
        }
        return p;
    }

    /** Installer un cours téléchargé dans le dossier utilisateur autorisé */
    public static String installDownloadedCourse(byte[] data, String remoteCourseId, String title) throws IOException {
        String safeTitle = sanitize(title);
        String baseName = (safeTitle.isEmpty() ? "course" : safeTitle) + "-" + remoteCourseId;
        String fileName = uniqueFileName(baseName, ".crs");
        
        // On écrit dans le dossier autorisé (AppData ou Home)
        Path dst = getCoursesDir().resolve(fileName);
        Files.write(dst, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Si cette classe existe, on l'appelle
        try {
            DownloadedCoursesRegistry.markDownloaded(remoteCourseId);
        } catch (NoClassDefFoundError | Exception ignored) {}
        
        reloadAllCourses();
        return fileName;
    }

    /** Rescan du dossier pour TableView */
    public static void reloadAllCourses() {
        try {
            Path dir = getCoursesDir();
            if (Files.exists(dir)) {
                List<Path> list = Files.list(dir)
                        .filter(p -> p.toString().endsWith(".crs"))
                        .sorted()
                        .collect(Collectors.toList());
                COURSES.setAll(list);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ObservableList<Path> getObservableCourses() {
        return COURSES;
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[^a-zA-Z0-9-_]", "_").trim();
    }

    private static String uniqueFileName(String base, String ext) {
        String candidate = base + ext;
        int i = 1;
        Path dir = getCoursesDir();
        while (Files.exists(dir.resolve(candidate))) {
            candidate = base + "_" + (i++) + ext;
        }
        return candidate;
    }
}