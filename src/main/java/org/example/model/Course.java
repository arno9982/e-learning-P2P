package org.example.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.Serializable;
import java.util.UUID;

public class Course implements Serializable {
    private String id;
    private String title;
    private String description;
    private String authorPseudo;
    private String filePath;

    // Propriétés pour la liaison de données JavaFX
    private transient StringProperty titleProperty;
    private transient StringProperty authorPseudoProperty;

    public Course(String title, String description, String authorPseudo, String filePath) {
        this.id = UUID.randomUUID().toString(); // Génère un identifiant unique lors de la création
        this.title = title;
        this.description = description;
        this.authorPseudo = authorPseudo;
        this.filePath = filePath;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthorPseudo() {
        return authorPseudo;
    }

    public String getFilePath() {
        return filePath;
    }

    // Setters
    public void setTitle(String title) {
        this.title = title;
        if (titleProperty != null) {
            titleProperty.set(title);
        }
    }

    public void setAuthorPseudo(String authorPseudo) {
        this.authorPseudo = authorPseudo;
        if (authorPseudoProperty != null) {
            authorPseudoProperty.set(authorPseudo);
        }
    }

    // NOUVELLES MÉTHODES POUR LA LIAISON DE DONNÉES
    public StringProperty titleProperty() {
        if (titleProperty == null) {
            titleProperty = new SimpleStringProperty(this, "title", title);
        }
        return titleProperty;
    }

    public StringProperty authorPseudoProperty() {
        if (authorPseudoProperty == null) {
            authorPseudoProperty = new SimpleStringProperty(this, "authorPseudo", authorPseudo);
        }
        return authorPseudoProperty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Course course = (Course) o;
        return id.equals(course.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}