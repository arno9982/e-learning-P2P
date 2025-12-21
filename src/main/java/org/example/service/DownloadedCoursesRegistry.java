package org.example.service;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.example.util.Json;

import com.google.gson.reflect.TypeToken;

/** Mémorise quels cours viennent du téléchargement (donc non éditables) */
public class DownloadedCoursesRegistry {
    
    // CORRECTION : On utilise CourseRepository.getCoursesDir() au lieu de la constante fixe
    private static Path getRegistryPath() {
        return CourseRepository.getCoursesDir().resolve("downloaded.json");
    }

    private static final Type TYPE = new TypeToken<Set<String>>(){}.getType();
    private static Set<String> ids = load();

    private static Set<String> load() {
        try {
            Path regPath = getRegistryPath();
            if (Files.exists(regPath)) {
                String s = Files.readString(regPath);
                Set<String> set = Json.decode(s, TYPE);
                return (set != null) ? set : new HashSet<>();
            }
        } catch (IOException ignored) {}
        return new HashSet<>();
    }

    private static void save() {
        try {
            Path regPath = getRegistryPath();
            Files.createDirectories(regPath.getParent());
            Files.writeString(regPath, Json.encode(ids));
        } catch (IOException ignored) {}
    }

    public static void markDownloaded(String remoteCourseId) {
        if (remoteCourseId == null) return;
        ids.add(remoteCourseId);
        save();
    }

    public static boolean isDownloaded(String remoteCourseId) {
        return remoteCourseId != null && ids.contains(remoteCourseId);
    }
}