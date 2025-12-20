package org.example.model;

import java.io.Serializable;
import java.util.Objects;

public class CourseNode implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum NodeType implements Serializable {
        COURS, PARTIE, CHAPITRE, SECTION, NOTION
    }

    public enum ContentType implements Serializable {
        TEXT("Texte", ""),
        PDF("PDF", "*.pdf"),
        VIDEO("Vidéo", "*.mp4", "*.avi", "*.mov"),
        AUDIO("Audio", "*.mp3", "*.wav"),
        LINK("Lien", "");

        private final String displayName;
        private final String[] extensions;

        ContentType(String displayName, String... extensions) {
            this.displayName = displayName;
            this.extensions = extensions;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String[] getExtensions() {
            return extensions;
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    private NodeType type;
    private String title;
    private ContentType contentType;
    private String content;
    private String contentFilePath; // Le champ qui manquait et causait l'erreur

    public CourseNode(NodeType type, String title) {
        this.type = type;
        this.title = Objects.requireNonNull(title);
        this.contentType = null;
        this.content = "";
    }

    public NodeType getType() { return type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = Objects.requireNonNull(title); }
    public ContentType getContentType() { return contentType; }
    public void setContentType(ContentType contentType) {
        if (this.type == NodeType.NOTION) {
            this.contentType = contentType;
        }
    }
    public String getContent() { return content; }
    public void setContent(String content) {
        if (this.type == NodeType.NOTION) {
            this.content = content;
        }
    }

    // Les méthodes qui manquaient et causent l'erreur
    public String getContentFilePath() { return contentFilePath; }
    public void setContentFilePath(String contentFilePath) { this.contentFilePath = contentFilePath; }

    @Override
    public String toString() {
        return title;
    }
}