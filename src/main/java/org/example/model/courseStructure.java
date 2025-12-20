package org.example.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class courseStructure implements Serializable {

    private static final long serialVersionUID = 1L;

    private CourseNode root;
    private List<courseStructure> children;

    public courseStructure(CourseNode root) {
        this.root = root;
        this.children = new ArrayList<>();
    }

    public CourseNode getRoot() {
        return root;
    }

    public List<courseStructure> getChildren() {
        return children;
    }

    public void addChild(courseStructure child) {
        this.children.add(child);
    }
}
