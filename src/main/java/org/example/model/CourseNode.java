package org.example.model;

public class CourseNode {

    public enum NodeType {
        COURS, PARTIE, CHAPITRE, SECTION, NOTION
    }

    private NodeType type;
    private String title;
    private String content; // utile seulement pour les notions

    public CourseNode(NodeType type, String title) {
        this.type = type;
        this.title = title;
        this.content = "";
    }

    public NodeType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        if (type == NodeType.NOTION) {
            this.content = content;
        }
    }

    @Override
    public String toString() {
        return title;
    }
}
