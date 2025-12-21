package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.HTMLEditor;
import javafx.stage.FileChooser;
import org.example.model.CourseNode;
import org.example.model.CourseNode.ContentType;
import org.example.model.CourseNode.NodeType;
import org.example.model.courseStructure;
import org.example.ai.LLM;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

public class CourseEditorController {

    @FXML private TreeView<CourseNode> courseStructureTree;
    @FXML private HTMLEditor htmlEditor;
    @FXML private TextField linkTextField;
    @FXML private VBox resourceBox;
    @FXML private Button importResourceButton;
    @FXML private Button removeResourceButton;
    @FXML private Label contentTitleLabel;
    @FXML private Label contentTypeLabel;
    @FXML private Label resourceLabel;
    @FXML private Button editButton;

    // --- Formulaire d’ajout ---
    @FXML private TitledPane addFormPane;
    @FXML private TextField addTitleField;
    @FXML private ComboBox<NodeType> addTypeCombo;
    @FXML private ComboBox<ContentType> addContentTypeCombo;
    @FXML private Label addContentTypeLabel;
    @FXML private Label addFormErrorLabel;

    // --- Assistant IA ---
    @FXML private TitledPane aiPane;
    @FXML private TextArea aiPromptArea;
    @FXML private Spinner<Integer> aiWordsSpinner;
    @FXML private Button aiGenerateButton;

    // CORRECTION : Le chemin n'est plus "final" et utilise la propriété système définie dans Main
    private Path saveDirectory;
    private TreeItem<CourseNode> rootNode;
    private File currentCourseFile;
    private MainController mainController;

    public void setMainController(MainController mainController) { this.mainController = mainController; }

    public void initialize() {
        // CORRECTION : On récupère le chemin sécurisé (AppData sur Windows)
        String basePath = System.getProperty("app.base.path", System.getProperty("user.home"));
        this.saveDirectory = Paths.get(basePath, "courses");

        try {
            // Création du dossier si inexistant dans l'espace utilisateur autorisé
            if (!Files.exists(saveDirectory)) {
                Files.createDirectories(saveDirectory);
            }
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur de dossier", "Impossible d'accéder au dossier utilisateur : " + e.getMessage());
            e.printStackTrace();
        }

        rootNode = new TreeItem<>(new CourseNode(NodeType.COURS, "Nouveau Cours"));
        rootNode.setExpanded(true);
        courseStructureTree.setRoot(rootNode);

        setEditorsVisibility(false, false, false, false);
        editButton.setDisable(true);

        setAddFormVisible(false);
        if (addTypeCombo != null) {
            addTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateContentTypeVisibility());
        }
        if (aiWordsSpinner != null) {
            aiWordsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(80, 500, 150, 10));
        }

        courseStructureTree.getSelectionModel().select(rootNode);

        courseStructureTree.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (oldSel != null) saveNodeContent(oldSel);
            if (newSel != null) {
                loadNodeContent(newSel);
                editButton.setDisable(false);
                populateAddFormAllowedTypes();
            } else {
                setEditorsVisibility(false, false, false, false);
                contentTitleLabel.setText("Sélectionnez un élément...");
                contentTypeLabel.setText("");
                editButton.setDisable(true);
            }
        });

        removeResourceButton.setDisable(true);
    }

    public void initData(File courseFile) {
        this.currentCourseFile = courseFile;
        if (courseFile == null) handleNewCourse(); else loadCourseFromFile(courseFile);
    }

    private void loadCourseFromFile(File file) {
        if (!file.exists()) {
            showAlert(Alert.AlertType.ERROR, "Fichier non trouvé", "Le fichier de cours n'a pas été trouvé : " + file.getAbsolutePath());
            handleNewCourse();
            return;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            courseStructure loadedStructure = (courseStructure) ois.readObject();
            rootNode = buildTreeItem(loadedStructure);
            rootNode.setExpanded(true);
            courseStructureTree.setRoot(rootNode);
            if (!rootNode.getChildren().isEmpty()) courseStructureTree.getSelectionModel().selectFirst();
            showAlert(Alert.AlertType.INFORMATION, "Chargement réussi", "Le cours a été chargé pour édition.");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur de chargement", "Impossible de charger la structure du cours.");
            handleNewCourse();
        }
    }

    private TreeItem<CourseNode> buildTreeItem(courseStructure structure) {
        TreeItem<CourseNode> item = new TreeItem<>(structure.getRoot());
        item.setExpanded(true);
        for (courseStructure childStructure : structure.getChildren()) {
            item.getChildren().add(buildTreeItem(childStructure));
        }
        return item;
    }

    private void setEditorsVisibility(boolean showHtml, boolean showLink, boolean showResource, boolean isNotion) {
        htmlEditor.setVisible(showHtml);
        htmlEditor.setManaged(showHtml);
        linkTextField.setVisible(showLink);
        linkTextField.setManaged(showLink);
        resourceBox.setVisible(showResource);
        resourceBox.setManaged(showResource);
        contentTitleLabel.setText(isNotion ? "Contenu de la Notion" : "Détails de l'élément");
        contentTypeLabel.setVisible(isNotion);

        boolean showAI = isNotion && showHtml;
        if (aiPane != null) {
            aiPane.setDisable(!showAI);
        }
    }

    private void updateContentDisplay(TreeItem<CourseNode> treeItem) {
        CourseNode node = treeItem.getValue();
        ContentType contentType = node.getContentType();
        contentTypeLabel.setText(contentType != null ? contentType.getDisplayName() : "");

        if (contentType == null) {
            setEditorsVisibility(false, false, false, node.getType() == NodeType.NOTION);
            return;
        }

        switch (contentType) {
            case TEXT -> {
                setEditorsVisibility(true, false, false, true);
                htmlEditor.setHtmlText(Objects.requireNonNullElse(node.getContent(), ""));
            }
            case LINK -> {
                setEditorsVisibility(false, true, false, true);
                linkTextField.setText(Objects.requireNonNullElse(node.getContent(), ""));
            }
            case PDF, VIDEO, AUDIO -> {
                setEditorsVisibility(false, false, true, true);
                String filePath = node.getContent();
                if (filePath != null && !filePath.isEmpty()) {
                    Path p = Paths.get(filePath);
                    resourceLabel.setText("Fichier importé : " + p.getFileName());
                    removeResourceButton.setDisable(false);
                } else {
                    resourceLabel.setText("Aucun fichier importé.");
                    removeResourceButton.setDisable(true);
                }
            }
        }
    }

    private void saveNodeContent(TreeItem<CourseNode> treeItem) {
        CourseNode node = treeItem.getValue();
        if (node.getType() == NodeType.NOTION) {
            ContentType contentType = node.getContentType();
            if (contentType != null) {
                switch (contentType) {
                    case TEXT -> node.setContent(htmlEditor.getHtmlText());
                    case LINK -> node.setContent(linkTextField.getText());
                }
            }
        }
    }

    private void loadNodeContent(TreeItem<CourseNode> newSel) {
        CourseNode node = newSel.getValue();
        boolean isNotion = node.getType() == NodeType.NOTION;
        if (isNotion) updateContentDisplay(newSel);
        else {
            setEditorsVisibility(false, false, false, false);
            contentTitleLabel.setText("Détails de l'élément : " + node.getTitle());
        }
    }

    private void populateAddFormAllowedTypes() {
        TreeItem<CourseNode> selected = courseStructureTree.getSelectionModel().getSelectedItem();
        TreeItem<CourseNode> parent = (selected != null) ? selected : rootNode;

        List<NodeType> allowedChildren = switch (parent.getValue().getType()) {
            case COURS -> List.of(NodeType.PARTIE, NodeType.NOTION);
            case PARTIE -> List.of(NodeType.CHAPITRE, NodeType.NOTION);
            case CHAPITRE -> List.of(NodeType.SECTION, NodeType.NOTION);
            case SECTION -> List.of(NodeType.NOTION);
            case NOTION -> List.of();
        };

        if (addTypeCombo != null) {
            addTypeCombo.getItems().setAll(allowedChildren);
            if (!allowedChildren.isEmpty()) addTypeCombo.getSelectionModel().select(allowedChildren.get(0));
        }
        updateContentTypeVisibility();
    }

    private void updateContentTypeVisibility() {
        if (addTypeCombo == null) return;
        boolean notion = addTypeCombo.getValue() == NodeType.NOTION;
        if (addContentTypeCombo != null) {
            addContentTypeCombo.setVisible(notion);
            addContentTypeCombo.setManaged(notion);
            if (notion && addContentTypeCombo.getItems().isEmpty()) {
                addContentTypeCombo.getItems().setAll(ContentType.values());
                addContentTypeCombo.getSelectionModel().select(ContentType.TEXT);
            }
        }
        if (addContentTypeLabel != null) {
            addContentTypeLabel.setVisible(notion);
            addContentTypeLabel.setManaged(notion);
        }
    }

    private void setAddFormVisible(boolean visible) {
        if (addFormPane != null) {
            addFormPane.setVisible(visible);
            addFormPane.setManaged(visible);
        }
        if (addFormErrorLabel != null) addFormErrorLabel.setText("");
        if (visible) populateAddFormAllowedTypes();
    }

    @FXML
    private void handleAddNode() {
        setAddFormVisible(addFormPane == null || !addFormPane.isVisible());
    }

    @FXML
    private void handleAddNodeFromForm() {
        TreeItem<CourseNode> selected = courseStructureTree.getSelectionModel().getSelectedItem();
        TreeItem<CourseNode> parent = (selected != null) ? selected : rootNode;

        NodeType type = addTypeCombo != null ? addTypeCombo.getValue() : null;
        String title = (addTitleField != null && addTitleField.getText() != null) ? addTitleField.getText().trim() : "";

        if (type == null) {
            if (addFormErrorLabel != null) addFormErrorLabel.setText("Choisissez un type.");
            return;
        }
        if (title.isEmpty()) {
            if (addFormErrorLabel != null) addFormErrorLabel.setText("Le titre est obligatoire.");
            return;
        }

        CourseNode newNode = new CourseNode(type, title);
        if (type == NodeType.NOTION) {
            ContentType ct = (addContentTypeCombo != null) ? addContentTypeCombo.getValue() : ContentType.TEXT;
            if (ct == null) ct = ContentType.TEXT;
            newNode.setContentType(ct);
        }

        TreeItem<CourseNode> newItem = new TreeItem<>(newNode);
        parent.getChildren().add(newItem);
        parent.setExpanded(true);
        courseStructureTree.getSelectionModel().select(newItem);

        if (addTitleField != null) addTitleField.clear();
        setAddFormVisible(false);
    }

    @FXML
    private void handleCancelAddForm() { setAddFormVisible(false); }

    @FXML
    private void handleGenerateWithAI() {
        TreeItem<CourseNode> selectedItem = courseStructureTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Assistant IA", "Sélectionnez une **Notion (Texte)**.");
            return;
        }
        CourseNode node = selectedItem.getValue();
        if (node.getType() != NodeType.NOTION || node.getContentType() != ContentType.TEXT) {
            showAlert(Alert.AlertType.WARNING, "Assistant IA", "L’IA ne s’applique qu’aux **Notions** de **type Texte**.");
            return;
        }

        String notionTitle = node.getTitle();
        String contextPath = buildContextPath(selectedItem);
        String extra = (aiPromptArea != null && aiPromptArea.getText() != null) ? aiPromptArea.getText().trim() : "";
        int words = (aiWordsSpinner != null && aiWordsSpinner.getValue() != null) ? aiWordsSpinner.getValue() : 150;

        try {
            // CORRECTION : L'appel IA utilisera désormais la config chargée depuis AppData
            String html = LLM.get().generateNotionHtml(
                    notionTitle + "  |  " + contextPath,
                    extra.isBlank() ? ("Contexte: " + contextPath) 
                            : (extra + "\nContexte: " + contextPath),
                    words
            );

            htmlEditor.setHtmlText(html);
            node.setContent(html);
            showAlert(Alert.AlertType.INFORMATION, "Assistant IA", "Contenu généré avec succès.");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Assistant IA", "La génération a échoué. Vérifiez votre clé API dans les paramètres.");
        }
    }

    private String buildContextPath(TreeItem<CourseNode> selectedItem) {
        StringBuilder ctx = new StringBuilder(selectedItem.getValue().getTitle());
        TreeItem<CourseNode> cur = selectedItem.getParent();
        while (cur != null) {
            ctx.insert(0, cur.getValue().getTitle() + " ▸ ");
            cur = cur.getParent();
        }
        return ctx.toString();
    }

    @FXML
    private void handleNewCourse() {
        rootNode = new TreeItem<>(new CourseNode(NodeType.COURS, "Nouveau Cours"));
        rootNode.setExpanded(true);
        courseStructureTree.setRoot(rootNode);

        setEditorsVisibility(false, false, false, false);
        htmlEditor.setHtmlText("");
        linkTextField.setText("");
        resourceLabel.setText("Aucun fichier importé.");
        editButton.setDisable(true);
        contentTitleLabel.setText("Nouveau Cours");
        contentTypeLabel.setText("");

        this.currentCourseFile = null;
        courseStructureTree.getSelectionModel().select(rootNode);
        setAddFormVisible(false);
    }

    @FXML
    private void handleSaveCourse() {
        try {
            // CORRECTION : Utilisation de saveDirectory dynamique
            if (!Files.exists(saveDirectory)) {
                Files.createDirectories(saveDirectory);
            }

            File saveFile;
            boolean isNewCourse = (currentCourseFile == null);
            if (isNewCourse) {
                String courseTitle = rootNode.getValue().getTitle();
                String fileName = courseTitle.replaceAll("[^a-zA-Z0-9.-]", "_") + ".crs";
                saveFile = saveDirectory.resolve(fileName).toFile();
            } else {
                saveFile = currentCourseFile;
            }

            courseStructure saveStructure = buildCourseStructure(rootNode);
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(saveFile))) {
                oos.writeObject(saveStructure);
            }

            showAlert(Alert.AlertType.INFORMATION, "Sauvegarde réussie", "Cours enregistré dans : " + saveFile.getAbsolutePath());
            if (mainController != null) mainController.refreshMyCourses();
            handleNewCourse();
        } catch (IOException e) {
            e.printStackTrace();
            // L'erreur ne mentionnera plus C:\Program Files
            showAlert(Alert.AlertType.ERROR, "Erreur de sauvegarde", "Impossible d'écrire le fichier : " + e.getMessage());
        }
    }

    private courseStructure buildCourseStructure(TreeItem<CourseNode> treeItem) {
        courseStructure structure = new courseStructure(treeItem.getValue());
        for (TreeItem<CourseNode> child : treeItem.getChildren()) structure.addChild(buildCourseStructure(child));
        return structure;
    }

    @FXML
    private void handleRemoveNode() {
        TreeItem<CourseNode> selected = courseStructureTree.getSelectionModel().getSelectedItem();
        if (selected != null && selected != rootNode) {
            TreeItem<CourseNode> parent = selected.getParent();
            parent.getChildren().remove(selected);
        } else {
            showAlert(Alert.AlertType.WARNING, "Suppression impossible", "Impossible de supprimer la racine.");
        }
    }

    @FXML
    private void handleEditNode() {
        TreeItem<CourseNode> selectedItem = courseStructureTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;
        CourseNode node = selectedItem.getValue();
        TextInputDialog dialog = new TextInputDialog(node.getTitle());
        dialog.setTitle("Modifier le titre");
        dialog.setHeaderText("Nouveau titre :");
        dialog.showAndWait().ifPresent(newTitle -> {
            if (!newTitle.trim().isEmpty()) {
                node.setTitle(newTitle.trim());
                treeItem_forceRefresh(selectedItem);
            }
        });
    }

    @FXML
    private void handleImportResource() {
        TreeItem<CourseNode> selectedItem = courseStructureTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null || selectedItem.getValue().getType() != NodeType.NOTION) return;

        ContentType contentType = selectedItem.getValue().getContentType();
        if (contentType == null || contentType == ContentType.TEXT || contentType == ContentType.LINK) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Importer une ressource");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(contentType.getDisplayName(), contentType.getExtensions()));
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            selectedItem.getValue().setContent(file.getAbsolutePath());
            resourceLabel.setText("Fichier : " + file.getName());
            removeResourceButton.setDisable(false);
        }
    }

    @FXML
    private void handleRemoveResource() {
        TreeItem<CourseNode> selectedItem = courseStructureTree.getSelectionModel().getSelectedItem();
        if (selectedItem != null && selectedItem.getValue().getType() == NodeType.NOTION) {
            selectedItem.getValue().setContent(null);
            resourceLabel.setText("Aucun fichier importé.");
            removeResourceButton.setDisable(true);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void treeItem_forceRefresh(TreeItem<CourseNode> item) {
        CourseNode node = item.getValue();
        item.setValue(null);
        item.setValue(node);
    }
}