package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.HTMLEditor;
import javafx.scene.control.TreeItem;
import javafx.stage.FileChooser;
import org.example.model.CourseNode;
import org.example.model.CourseNode.NodeType;

import java.io.File;
import java.util.List;

public class CourseEditorController {

    @FXML private TreeView<CourseNode> courseStructureTree;
    @FXML private TextField titleField;
    @FXML private HTMLEditor htmlEditor;

    private TreeItem<CourseNode> rootNode;

    public void initialize() {
        rootNode = new TreeItem<>(new CourseNode(NodeType.COURS, "Cours"));
        rootNode.setExpanded(true);
        courseStructureTree.setRoot(rootNode);

        courseStructureTree.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (oldSel != null) {
                saveNodeContent(oldSel);
            }

            if (newSel != null) {
                titleField.setDisable(false);
                titleField.setText(newSel.getValue().getTitle());

                if (newSel.getValue().getType() == NodeType.NOTION) {
                    htmlEditor.setDisable(false);
                    htmlEditor.setHtmlText(newSel.getValue().getContent());
                } else {
                    htmlEditor.setDisable(true);
                    htmlEditor.setHtmlText("");
                }
            } else {
                titleField.setDisable(true);
                htmlEditor.setDisable(true);
                titleField.clear();
                htmlEditor.setHtmlText("");
            }
        });

        titleField.setDisable(true);
        htmlEditor.setDisable(true);

        titleField.textProperty().addListener((obs, oldVal, newVal) -> {
            TreeItem<CourseNode> selected = courseStructureTree.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selected.getValue().setTitle(newVal);
                selected.setValue(selected.getValue()); // rafraîchit l'affichage
            }
        });
    }

    private void saveNodeContent(TreeItem<CourseNode> node) {
        node.getValue().setTitle(titleField.getText());
        if (node.getValue().getType() == NodeType.NOTION) {
            node.getValue().setContent(htmlEditor.getHtmlText());
        }
    }

    @FXML
    private void handleAddNode() {
        TreeItem<CourseNode> selected = courseStructureTree.getSelectionModel().getSelectedItem();
        TreeItem<CourseNode> finalParent = (selected != null) ? selected : rootNode;

        NodeType[] allTypes = NodeType.values();
        ChoiceDialog<NodeType> dialog = new ChoiceDialog<>(NodeType.PARTIE, allTypes);
        dialog.setTitle("Ajouter un élément");
        dialog.setHeaderText("Choisissez le type d'élément à ajouter :");
        dialog.setContentText("Type :");

        dialog.showAndWait().ifPresent(type -> {
            if (!isValidChild(finalParent.getValue().getType(), type)) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Ce type ne peut pas être ajouté ici.", ButtonType.OK);
                alert.showAndWait();
                return;
            }

            String title = type.name().charAt(0) + type.name().substring(1).toLowerCase() + " " + (finalParent.getChildren().size() + 1);
            CourseNode nodeData = new CourseNode(type, title);
            TreeItem<CourseNode> newNode = new TreeItem<>(nodeData);
            finalParent.getChildren().add(newNode);
            finalParent.setExpanded(true);

            courseStructureTree.getSelectionModel().select(newNode);
        });
    }



    private boolean isValidChild(NodeType parent, NodeType child) {
        switch (parent) {
            case COURS: return child == NodeType.PARTIE || child == NodeType.CHAPITRE || child == NodeType.SECTION || child == NodeType.NOTION;
            case PARTIE: return child == NodeType.CHAPITRE || child == NodeType.SECTION || child == NodeType.NOTION;
            case CHAPITRE: return child == NodeType.SECTION || child == NodeType.NOTION;
            case SECTION: return child == NodeType.NOTION;
            case NOTION: return false;
            default: return false;
        }
    }

    @FXML
    private void handleRemoveNode() {
        TreeItem<CourseNode> selected = courseStructureTree.getSelectionModel().getSelectedItem();
        if (selected != null && selected != rootNode) {
            TreeItem<CourseNode> parent = selected.getParent();
            parent.getChildren().remove(selected);
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Vous ne pouvez pas supprimer le nœud racine 'Cours'.", ButtonType.OK);
            alert.showAndWait();
        }
    }

    @FXML
    private void handleImportResource() {
        List<String> choices = List.of("PDF", "Vidéo", "Lien externe");

        ChoiceDialog<String> dialog = new ChoiceDialog<>("PDF", choices);
        dialog.setTitle("Importer une ressource");
        dialog.setHeaderText("Choisissez le type de ressource à importer :");

        dialog.showAndWait().ifPresent(choice -> {
            switch (choice) {
                case "PDF" -> importPdf();
                case "Vidéo" -> importVideo();
                case "Lien externe" -> importLink();
            }
        });
    }

    private void importPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir un fichier PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers PDF", "*.pdf"));

        File selectedFile = fileChooser.showOpenDialog(courseStructureTree.getScene().getWindow());

        if (selectedFile != null) {
            showAlert("PDF importé", "Fichier sélectionné : " + selectedFile.getName());
            // Tu peux aussi sauvegarder le chemin du fichier dans la notion sélectionnée si nécessaire
        }
    }
    private void importVideo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir une vidéo");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers vidéo", "*.mp4", "*.avi", "*.mov"));

        File selectedFile = fileChooser.showOpenDialog(courseStructureTree.getScene().getWindow());

        if (selectedFile != null) {
            showAlert("Vidéo importée", "Vidéo sélectionnée : " + selectedFile.getName());
        }
    }
    private void importLink() {
        TextInputDialog dialog = new TextInputDialog("https://");
        dialog.setTitle("Importer un lien");
        dialog.setHeaderText("Entrez l'URL de la ressource externe :");

        dialog.showAndWait().ifPresent(link -> {
            if (link.startsWith("http://") || link.startsWith("https://")) {
                showAlert("Lien importé", "Lien : " + link);
            } else {
                showAlert("Lien invalide", "Le lien doit commencer par http:// ou https://");
            }
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }



    @FXML private void handleSaveCourse() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Fonction de sauvegarde à venir.", ButtonType.OK);
        alert.showAndWait();
    }
}
