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
import org.example.service.CourseRepository;

public class MyCoursesController {

    @FXML private TableView<CourseListDisplay> courseTableView;
    @FXML private TableColumn<CourseListDisplay, String> courseNameColumn;
    @FXML private TableColumn<CourseListDisplay, Void> optionsColumn;
    @FXML private TextField searchField;
    @FXML private Button newCourseButton;
    @FXML private Button importCourseButton;

    private MainController mainController;
    
    // CORRECTION : Le chemin est maintenant calculé dynamiquement
    private Path saveDirectory;

    private final ObservableList<CourseListDisplay> masterCourses = FXCollections.observableArrayList();
    private FilteredList<CourseListDisplay> filteredCourses;
    private boolean isTeacher = true; 

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setTeacherMode(boolean isTeacher) {
        this.isTeacher = isTeacher;
        if (importCourseButton != null) importCourseButton.setVisible(isTeacher);
        if (newCourseButton != null) newCourseButton.setVisible(isTeacher);
        refreshCourses(); 
    }

    @FXML
    public void initialize() {
        // CORRECTION : Utilisation du chemin AppData pour éviter l'erreur de permissions
        String basePath = System.getProperty("app.base.path", System.getProperty("user.home"));
        this.saveDirectory = CourseRepository.getCoursesDir();

        try {
            if (!Files.exists(saveDirectory)) {
                Files.createDirectories(saveDirectory);
            }
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur système", "Impossible de créer le dossier de stockage : " + e.getMessage());
        }

        courseNameColumn.setCellValueFactory(new PropertyValueFactory<>("title"));

        optionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button consultButton = styledBtn("Consulter", "primary");
            private final Button editButton = styledBtn("Éditer", "ghost");
            private final Button deleteButton = styledBtn("Supprimer", "danger");
            private final Button exportButton = styledBtn("Exporter", "ghost");
            private final Button visibilityButton = styledBtn("Visibilité", "ghost");
            private final HBox pane = new HBox(5);

            {
                consultButton.setPrefWidth(90);
                editButton.setPrefWidth(70);
                deleteButton.setPrefWidth(90);
                exportButton.setPrefWidth(80);
                visibilityButton.setPrefWidth(120);
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
                } else {
                    CourseListDisplay course = getTableView().getItems().get(getIndex());
                    visibilityButton.setText(course.isVisible() ? "Masquer" : "Rendre visible");
                    
                    // Un cours téléchargé (P2P) ne peut pas être édité localement
                    boolean isDownloaded = isDownloadedCourse(course);
                    editButton.setDisable(isDownloaded);

                    pane.getChildren().clear();
                    pane.getChildren().add(consultButton);

                    if (isTeacher) {
                        pane.getChildren().addAll(editButton, deleteButton, exportButton, visibilityButton);
                    }
                    setGraphic(pane);
                }
            }
        });

        // Configuration de la recherche et du filtrage
        filteredCourses = new FilteredList<>(masterCourses, c -> true);
        SortedList<CourseListDisplay> sorted = new SortedList<>(filteredCourses);
        sorted.comparatorProperty().bind(courseTableView.comparatorProperty());
        courseTableView.setItems(sorted);

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                final String q = normalize(newVal);
                filteredCourses.setPredicate(c -> {
                    if (q == null || q.isBlank()) return true;
                    return normalize(c.getTitle()).contains(q);
                });
            });
        }

        refreshCourses();
    }

    public void refreshCourses() {
        List<CourseListDisplay> tmp = new ArrayList<>();
        try {
            if (Files.exists(saveDirectory)) {
                Files.list(saveDirectory)
                        .filter(path -> path.toString().endsWith(".crs"))
                        .forEach(path -> {
                            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path.toFile()))) {
                                courseStructure loaded = (courseStructure) ois.readObject();
                                String id = path.getFileName().toString().replace(".crs", "");
                                tmp.add(new CourseListDisplay(id, loaded.getRoot().getTitle(), path.toString(), false));
                            } catch (Exception e) {
                                System.err.println("Fichier corrompu ignoré : " + path);
                            }
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        masterCourses.setAll(tmp);
    }

   @FXML
private void handleImportCourse() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Choisir un cours à ajouter");
    fileChooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Cours Plateforme (*.crs)", "*.crs")
    );

    File file = fileChooser.showOpenDialog(null);
    if (file != null) {
        try {
            // Créer le nom de destination
            Path dest = saveDirectory.resolve(file.getName());
            
            // Copie avec écrasement si nécessaire
            Files.copy(file.toPath(), dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            // Forcer le refresh du repository et de l'UI
            CourseRepository.reloadAllCourses();
            refreshCourses();
            
            showAlert(Alert.AlertType.INFORMATION, "Importation", "Le cours a été ajouté avec succès.");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible d'importer le fichier : " + e.getMessage());
        }
    }
}

    private void handleExportCourse(CourseListDisplay course) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter le cours");
        fileChooser.setInitialFileName(course.getTitle());
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Fichier plateforme (.crs)", "*.crs"),
                new FileChooser.ExtensionFilter("Package SCORM (.zip)", "*.zip")
        );

        File targetFile = fileChooser.showSaveDialog(null);
        if (targetFile != null) {
            try {
                Path source = Paths.get(course.getFilePath());
                if (targetFile.getName().endsWith(".zip")) {
                    ScormExporter.exportToScorm(source, targetFile.toPath());
                } else {
                    Files.copy(source, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                showAlert(Alert.AlertType.INFORMATION, "Succès", "Exportation réussie.");
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Erreur", "Exportation échouée.");
            }
        }
    }

    @FXML
    private void handleDeleteCourse(CourseListDisplay course) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer définitivement " + course.getTitle() + " ?", ButtonType.YES, ButtonType.NO);
        if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            try {
                Files.deleteIfExists(Paths.get(course.getFilePath()));
                refreshCourses();
                updateP2PSharedCourses();
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de supprimer le fichier.");
            }
        }
    }

    private void handleVisibilityToggle(CourseListDisplay course) {
        course.setVisible(!course.isVisible());
        courseTableView.refresh();
        updateP2PSharedCourses();
    }

    private void updateP2PSharedCourses() {
        List<Course> coursesToShare = new ArrayList<>();
        for (CourseListDisplay item : masterCourses) {
            if (item.isVisible()) {
                coursesToShare.add(new Course(item.getTitle(), "", "Auteur", item.getFilePath()));
            }
        }
        if (mainController != null) mainController.handleCourseListChange(coursesToShare);
    }

    private boolean isDownloadedCourse(CourseListDisplay course) {
        String fileName = new File(course.getFilePath()).getName();
        return fileName.contains("-") || DownloadedCoursesRegistry.isDownloaded(fileName);
    }

    private static Button styledBtn(String text, String... styles) {
        Button b = new Button(text);
        b.getStyleClass().addAll(styles);
        return b;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase().trim();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
}