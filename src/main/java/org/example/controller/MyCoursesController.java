package org.example.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.example.model.Course;
import org.example.model.CourseListDisplay;
import org.example.model.courseStructure;
import org.example.service.DownloadedCoursesRegistry;
import org.example.service.ScormExporter;
import org.example.service.ScormImporter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MyCoursesController {

    @FXML private TableView<CourseListDisplay> courseTableView;
    @FXML private TableColumn<CourseListDisplay, String> courseNameColumn;
    @FXML private TableColumn<CourseListDisplay, Void> optionsColumn;
    @FXML private TextField searchField;
    @FXML private Button newCourseButton;
    @FXML private Button importCourseButton;

    private MainController mainController;
    private final Path saveDirectory = Paths.get("data", "courses");

    private final ObservableList<CourseListDisplay> masterCourses = FXCollections.observableArrayList();
    private FilteredList<CourseListDisplay> filteredCourses;
    private boolean isTeacher = true; // Variable de test, à connecter à votre logique d'authentification.

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    // Méthode pour définir le mode d'accès de l'utilisateur (à appeler depuis MainController)
    public void setTeacherMode(boolean isTeacher) {
        this.isTeacher = isTeacher;
        // La visibilité du bouton d'importation dépend du mode enseignant
        if (importCourseButton != null) {
            importCourseButton.setVisible(isTeacher);
        }
        refreshCourses(); // Rafraîchir l'affichage pour appliquer les changements
    }

    @FXML
    public void initialize() {
        try {
            Files.createDirectories(saveDirectory);
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur de dossier", "Impossible de créer le dossier de sauvegarde : " + e.getMessage());
            e.printStackTrace();
        }

        courseNameColumn.setCellValueFactory(new PropertyValueFactory<>("title"));

        optionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button consultButton = styledBtn("Consulter", "primary");
            private final Button editButton = styledBtn("Éditer", "ghost");
            private final Button deleteButton = styledBtn("Supprimer", "danger");
            private final Button exportButton = styledBtn("Exporter", "ghost");
            private final Button visibilityButton = styledBtn("Rendre visible", "ghost");

            {
                consultButton.setPrefWidth(90);
                editButton.setPrefWidth(70);
                deleteButton.setPrefWidth(90);
                exportButton.setPrefWidth(80);
                visibilityButton.setPrefWidth(120);
            }

            private final HBox pane = new HBox(5);

            {
                pane.getStyleClass().add("table-actions");
                pane.setAlignment(Pos.CENTER_LEFT);

                consultButton.setOnAction(event -> {
                    CourseListDisplay course = getTableView().getItems().get(getIndex());
                    if (mainController != null) mainController.showCourseConsultation(course);
                });

                editButton.setOnAction(event -> {
                    CourseListDisplay course = getTableView().getItems().get(getIndex());
                    if (mainController != null) mainController.showCourseEditor(course);
                });

                deleteButton.setOnAction(event -> {
                    CourseListDisplay course = getTableView().getItems().get(getIndex());
                    handleDeleteCourse(course);
                });

                exportButton.setOnAction(event -> {
                    CourseListDisplay course = getTableView().getItems().get(getIndex());
                    handleExportCourse(course);
                });

                visibilityButton.setOnAction(event -> {
                    CourseListDisplay course = getTableView().getItems().get(getIndex());
                    handleVisibilityToggle(course);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                CourseListDisplay course = getTableView().getItems().get(getIndex());
                visibilityButton.setText(course.isVisible() ? "Masquer" : "Rendre visible");
                boolean isDownloaded = isDownloadedCourse(course);
                editButton.setDisable(isDownloaded);

                // Re-création dynamique de la HBox en fonction du mode utilisateur
                pane.getChildren().clear();
                pane.getChildren().add(consultButton); // Le bouton "Consulter" est toujours visible

                if (isTeacher) {
                    pane.getChildren().addAll(editButton, deleteButton, exportButton, visibilityButton);
                }

                setGraphic(pane);
            }
        });

        courseTableView.setPlaceholder(new Label("Aucun cours à afficher"));
        filteredCourses = new FilteredList<>(masterCourses, c -> true);
        SortedList<CourseListDisplay> sorted = new SortedList<>(filteredCourses);
        sorted.comparatorProperty().bind(courseTableView.comparatorProperty());
        courseTableView.setItems(sorted);

        courseTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        courseNameColumn.setMaxWidth(1f * Integer.MAX_VALUE * 0.40);
        optionsColumn.setMaxWidth(1f * Integer.MAX_VALUE * 0.60);
        courseNameColumn.setStyle("-fx-alignment: CENTER-LEFT;");
        optionsColumn.setStyle("-fx-alignment: CENTER;");

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                final String q = normalize(newVal);
                filteredCourses.setPredicate(c -> {
                    if (q == null || q.isBlank()) return true;
                    String title = normalize(c.getTitle());
                    String file = normalize(c.getFilePath());
                    return (title != null && title.contains(q)) || (file != null && file.contains(q));
                });
            });
        }

        // Gérer le clic sur le nouveau bouton d'importation.
        if (importCourseButton != null) {
            importCourseButton.setOnAction(event -> handleImportCourse());
        }

        refreshCourses();
    }

    @FXML
    private void handleNewCourse() {
        if (mainController != null) {
            mainController.showCourseEditor(null);
        }
    }

    public void refreshCourses() {
        List<CourseListDisplay> tmp = new ArrayList<>();

        try {
            if (Files.exists(saveDirectory)) {
                Files.list(saveDirectory)
                        .filter(path -> path.toString().endsWith(".crs"))
                        .sorted()
                        .forEach(path -> {
                            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path.toFile()))) {
                                courseStructure loadedStructure = (courseStructure) ois.readObject();
                                String courseId = path.getFileName().toString().replace(".crs", "");
                                tmp.add(new CourseListDisplay(
                                        courseId,
                                        loadedStructure.getRoot().getTitle(),
                                        path.toString(),
                                        false
                                ));
                            } catch (IOException | ClassNotFoundException e) {
                                System.err.println("Erreur lors du chargement du fichier de cours : " + path);
                                e.printStackTrace();
                            }
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur de lecture", "Impossible de lire les cours du dossier.");
        }

        masterCourses.setAll(tmp);
    }

    private void handleImportCourse() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sélectionner un cours à importer");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Fichiers de cours (*.crs, *.zip)", "*.crs", "*.zip"),
                new FileChooser.ExtensionFilter("Fichiers SCORM (*.zip)", "*.zip"),
                new FileChooser.ExtensionFilter("Fichiers de plateforme (*.crs)", "*.crs")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                if (file.getName().toLowerCase().endsWith(".zip")) {
                    ScormImporter.importScorm(file.toPath(), saveDirectory);
                    showAlert(Alert.AlertType.INFORMATION, "Importation SCORM réussie", "Le cours SCORM a été importé avec succès.");
                } else if (file.getName().toLowerCase().endsWith(".crs")) {
                    Path destinationPath = saveDirectory.resolve(file.getName());
                    Files.copy(file.toPath(), destinationPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    showAlert(Alert.AlertType.INFORMATION, "Importation réussie", "Le cours a été importé avec succès.");
                }
            } catch (IOException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Erreur d'importation", "Une erreur est survenue lors de l'importation du cours : " + e.getMessage());
            }
            refreshCourses();
        }
    }

    private void handleExportCourse(CourseListDisplay course) {
        if (course == null) {
            showAlert(Alert.AlertType.WARNING, "Sélection requise", "Veuillez sélectionner un cours à exporter.");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter le cours");
        String defaultFileName = new File(course.getFilePath()).getName().replace(".crs", "");

        FileChooser.ExtensionFilter crsFilter = new FileChooser.ExtensionFilter("Fichier de plateforme (*.crs)", "*.crs");
        FileChooser.ExtensionFilter scormFilter = new FileChooser.ExtensionFilter("Fichier SCORM (*.zip)", "*.zip");
        fileChooser.getExtensionFilters().addAll(crsFilter, scormFilter);
        fileChooser.setInitialFileName(defaultFileName + ".crs");

        File fileToExportTo = fileChooser.showSaveDialog(null);

        if (fileToExportTo != null) {
            Path sourcePath = Paths.get(course.getFilePath());
            Path destinationPath = fileToExportTo.toPath();

            try {
                if (fileToExportTo.getName().toLowerCase().endsWith(".zip")) {
                    ScormExporter.exportToScorm(sourcePath, destinationPath);
                    showAlert(Alert.AlertType.INFORMATION, "Exportation SCORM réussie", "Le cours a été exporté au format SCORM : " + destinationPath);
                } else {
                    Files.copy(sourcePath, destinationPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    showAlert(Alert.AlertType.INFORMATION, "Exportation réussie", "Le cours a été exporté avec succès à : " + destinationPath);
                }
            } catch (IOException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Erreur d'exportation", "Une erreur est survenue lors de l'exportation du cours : " + e.getMessage());
            }
        }
    }

    private void handleDeleteCourse(CourseListDisplay course) {
        if (course == null) {
            showAlert(Alert.AlertType.WARNING, "Sélection requise", "Veuillez sélectionner un cours à supprimer.");
            return;
        }

        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Confirmation de suppression");
        confirmationAlert.setHeaderText("Supprimer le cours : " + course.getTitle());
        confirmationAlert.setContentText("Êtes-vous sûr de vouloir supprimer ce cours ? Cette action est irréversible.");

        Optional<ButtonType> result = confirmationAlert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Path coursePath = Paths.get(course.getFilePath());
                Files.deleteIfExists(coursePath);
                masterCourses.remove(course);
                updateP2PSharedCourses();
                showAlert(Alert.AlertType.INFORMATION, "Suppression réussie", "Le cours a été supprimé avec succès.");
            } catch (IOException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Erreur de suppression", "Une erreur est survenue lors de la suppression du cours : " + e.getMessage());
            }
        }
    }

    private void handleVisibilityToggle(CourseListDisplay course) {
        course.setVisible(!course.isVisible());
        courseTableView.refresh();
        updateP2PSharedCourses();
        showAlert(Alert.AlertType.INFORMATION, "Visibilité mise à jour", "La visibilité de '" + course.getTitle() + "' a été changée.");
    }

    private void updateP2PSharedCourses() {
        List<Course> coursesToShare = new ArrayList<>();
        for (CourseListDisplay item : masterCourses) {
            if (item.isVisible()) {
                coursesToShare.add(new Course(item.getTitle(), "Description", "Auteur inconnu", item.getFilePath()));
            }
        }
        if (mainController != null) {
            mainController.handleCourseListChange(coursesToShare);
        }
    }

    private static Button styledBtn(String text, String... styleClasses) {
        Button b = new Button(text);
        if (styleClasses != null) b.getStyleClass().addAll(styleClasses);
        return b;
    }

    private boolean isDownloadedCourse(CourseListDisplay course) {
        String fileName = new File(course.getFilePath()).getName();
        String nameNoExt = fileName.endsWith(".crs") ? fileName.substring(0, fileName.length() - 4) : fileName;
        int dash = nameNoExt.lastIndexOf('-');
        String remoteId = (dash > 0 && dash < nameNoExt.length() - 1) ? nameNoExt.substring(dash + 1) : null;

        if (remoteId != null && DownloadedCoursesRegistry.isDownloaded(remoteId)) {
            return true;
        }
        return dash > 0;
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.toLowerCase().trim();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}