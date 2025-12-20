package org.example.model;

import java.io.File;

public class CourseListDisplay {
    private final String courseId; // Le type a été changé en String
    private final String title;
    private final String filePath;
    private boolean visible;

    // Le constructeur corrigé, avec le 1er paramètre en String
    public CourseListDisplay(String courseId, String title, String filePath, boolean visible) {
        this.courseId = courseId;
        this.title = title;
        this.filePath = filePath;
        this.visible = visible;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public String toString() {
        return title;
    }
}