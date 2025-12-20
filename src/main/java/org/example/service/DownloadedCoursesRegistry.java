package org.example.service;

import com.google.gson.reflect.TypeToken;
import org.example.util.Json;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

/** Mémorise quels cours viennent du téléchargement (donc non éditables) */
public class DownloadedCoursesRegistry {
    private static final Path REG_PATH = CourseRepository.COURSES_DIR.resolve("downloaded.json");
    private static final Type TYPE = new TypeToken<Set<String>>(){}.getType();
    private static Set<String> ids = load();

    private static Set<String> load() {
        try {
            if (Files.exists(REG_PATH)) {
                String s = Files.readString(REG_PATH);
                Set<String> set = Json.decode(s, TYPE);
                return (set != null) ? set : new HashSet<>();
            }
        } catch (IOException ignored) {}
        return new HashSet<>();
    }

    private static void save() {
        try {
            Files.createDirectories(REG_PATH.getParent());
            Files.writeString(REG_PATH, Json.encode(ids));
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

