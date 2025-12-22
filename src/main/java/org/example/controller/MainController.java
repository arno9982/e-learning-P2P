package org.example.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Duration;
import org.example.model.Course;
import org.example.model.CourseListDisplay;
import org.example.service.P2PService;
import org.example.ai.LLM; // pour le test IA (optionnel)
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import org.example.ai.AIConfig;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.example.service.RelayClient;

public class MainController {

    @FXML
    private TabPane mainTabPane;
    @FXML
    private Tab myCoursesTab;
    @FXML
    private Tab courseEditorTab;
    @FXML
    private Tab courseConsultationTab;
    @FXML
    private CheckMenuItem teacherModeCheck;
    @FXML private ComboBox<String> aiProvider1Combo;
    @FXML private PasswordField apiKey1Field;

    @FXML
    private TextField pseudoField;
    @FXML
    private CheckMenuItem p2pVisibilityCheck;
    @FXML
    private TableView<Course> p2pCourseTable;
    @FXML
    private TableColumn<Course, String> p2pCourseTitleColumn;
    @FXML
    private TableColumn<Course, String> p2pCourseAuthorColumn;
    @FXML
    private TextField p2pSearchField;

    private MyCoursesController myCoursesController;
    private CourseEditorController courseEditorController;
    private CourseConsultationController courseConsultationController;
    private P2PService p2pService;

    private ObservableList<Course> observableP2pCourseList;
    private FilteredList<Course> filteredData;

    @FXML
    private void initialize() {
        // P2P
        p2pService = P2PService.getInstance();
        p2pService.setMainController(this);

       if (aiProvider1Combo != null) {
        aiProvider1Combo.getItems().setAll("OPENAI", "GROQ");
        aiProvider1Combo.setValue(AIConfig.getProvider());
        apiKey1Field.setText(AIConfig.getApiKey());

        // Écouteur : Changement de Provider
        aiProvider1Combo.valueProperty().addListener((obs, oldVal, newVal) -> {
            AIConfig.updateConfig(newVal, apiKey1Field.getText());
        });

        // Écouteur : Saisie de la clé (Temps réel)
        apiKey1Field.textProperty().addListener((obs, oldVal, newVal) -> {
            AIConfig.updateConfig(aiProvider1Combo.getValue(), newVal);
        });
    }
        // Table P2P
        if (p2pCourseTable != null) {
            p2pCourseTitleColumn.setCellValueFactory(cellData -> cellData.getValue().titleProperty());
            p2pCourseAuthorColumn.setCellValueFactory(cellData -> cellData.getValue().authorPseudoProperty());

            observableP2pCourseList = FXCollections.observableArrayList();
            filteredData = new FilteredList<>(observableP2pCourseList, p -> true);

            SortedList<Course> sortedData = new SortedList<>(filteredData);
            sortedData.comparatorProperty().bind(p2pCourseTable.comparatorProperty());
            p2pCourseTable.setItems(sortedData);

            // Colonne Actions (Télécharger)
            TableColumn<Course, Void> actionsCol = new TableColumn<>("Actions");
            actionsCol.setPrefWidth(140);
            actionsCol.setCellFactory(col -> new TableCell<>() {
                private final Button btn = new Button("Télécharger");

                {
                    btn.setOnAction(e -> {
                        Course c = getTableView().getItems().get(getIndex());
                        handleDownloadFromP2P(c);
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : btn);
                }
            });
            boolean already = p2pCourseTable.getColumns().stream().anyMatch(tc -> "Actions".equals(tc.getText()));
            if (!already) p2pCourseTable.getColumns().add(actionsCol);
        }

        // Recherche
        if (p2pSearchField != null) {
            p2pSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
                filteredData.setPredicate(course -> {
                    if (newValue == null || newValue.isEmpty()) return true;
                    String lowerCaseFilter = newValue.toLowerCase();
                    if (course.getTitle() != null && course.getTitle().toLowerCase().contains(lowerCaseFilter))
                        return true;
                    return course.getAuthorPseudo() != null && course.getAuthorPseudo().toLowerCase().contains(lowerCaseFilter);
                });
            });
        }

        // Pseudo par défaut
        if (pseudoField != null) {
            pseudoField.setText("User-" + (int) (Math.random() * 1000));
        }

        // Rafraîchissement périodique de la liste P2P
        Timeline refresher = new Timeline(new KeyFrame(Duration.seconds(3), e -> updateP2PCourseList()));
        refresher.setCycleCount(Timeline.INDEFINITE);
        refresher.play();

        // Toggle Visibilité P2P
        if (p2pVisibilityCheck != null) {
            p2pVisibilityCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    String pseudo = pseudoField.getText();
                    if (pseudo == null || pseudo.trim().isEmpty()) {
                        showError("Erreur", "Veuillez entrer un pseudo pour activer le P2P.");
                        p2pVisibilityCheck.setSelected(false);
                        return;
                    }
                    try {
                        p2pService.startP2PService(pseudo, true);
                        System.out.println("Service P2P démarré pour " + pseudo);
                        updateP2PCourseList();
                    } catch (IOException e) {
                        showError("Erreur", "Impossible de démarrer le service P2P: " + e.getMessage());
                        p2pVisibilityCheck.setSelected(false);
                        e.printStackTrace();
                    }
                } else {
                    p2pService.stopP2PService();
                    System.out.println("Service P2P arrêté.");
                    updateP2PCourseList();
                }
            });
        }

        // Chargement des sous-vues
        try {
            FXMLLoader myCoursesLoader = new FXMLLoader(getClass().getResource("/fxml/myCourses.fxml"));
            Parent myCoursesView = myCoursesLoader.load();
            myCoursesController = myCoursesLoader.getController();
            myCoursesController.setMainController(this);
            myCoursesTab.setContent(myCoursesView);

            FXMLLoader editorLoader = new FXMLLoader(getClass().getResource("/fxml/courseEditor.fxml"));
            Parent editorView = editorLoader.load();
            courseEditorController = editorLoader.getController();
            courseEditorController.setMainController(this);
            courseEditorTab.setContent(editorView);

            FXMLLoader consultationLoader = new FXMLLoader(getClass().getResource("/fxml/courseConsultation.fxml"));
            Parent consultationView = consultationLoader.load();
            courseConsultationController = consultationLoader.getController();
            courseConsultationController.setMainController(this);
            courseConsultationTab.setContent(consultationView);

            myCoursesController.refreshCourses();

           if (teacherModeCheck != null) {
    teacherModeCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
        // 1. Gérer l'UI
        courseEditorTab.setDisable(!newVal);
        if (!newVal) {
            mainTabPane.getSelectionModel().select(myCoursesTab);
        }

        // 2. Informer le service P2P de notre rôle
        p2pService.setIHaveTeacherRole(newVal);

        if (newVal) {
            // 3. LANCER LE BOOTSTRAP (Seulement pour l'enseignant)
            new Thread(() -> {
                try {
                    // On essaie de lancer le serveur. S'il tourne déjà, l'exception sera ignorée.
                    org.example.server.BootstrapServer.main(new String[0]);
                } catch (Exception e) {
                    System.out.println("Le serveur Bootstrap tourne déjà ou erreur : " + e.getMessage());
                }
            }).start();

            // 4. L'enseignant utilise son propre serveur
            RelayClient.RELAY_BASE = "http://localhost:8080";
        }

        // 5. Envoyer un message immédiatement pour prévenir les élèves
        if (p2pService.isServiceRunning()) {
            try {
                // Créer une méthode publique triggerHello() dans P2PService qui appelle sendHello()
                p2pService.triggerHello(); 
            } catch (Exception ignored) {}
        }
    });
}
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateP2PCourseList() {
        if (p2pService != null && p2pService.isServiceRunning()) {
            List<Course> courses = p2pService.getAllNetworkCourses();
            Platform.runLater(() -> observableP2pCourseList.setAll(courses));
        } else {
            Platform.runLater(() -> observableP2pCourseList.clear());
        }
    }

    public void refreshP2PCourses() {
        Platform.runLater(this::updateP2PCourseList);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void showCourseEditor(CourseListDisplay course) {
        courseEditorController.initData(course != null ? new File(course.getFilePath()) : null);
        mainTabPane.getSelectionModel().select(courseEditorTab);
    }

    public void showCourseConsultation(CourseListDisplay course) {
        courseConsultationController.loadCourse(new File(course.getFilePath()));
        mainTabPane.getSelectionModel().select(courseConsultationTab);
    }

    @FXML
    private void handleNewCourse() {
        showCourseEditor(null);
    }

    public void handleCourseListChange(List<Course> courses) {
        if (p2pService != null && p2pService.isServiceRunning()) {
            p2pService.setSharedCourses(courses);
        }
    }

    public void refreshMyCourses() {
        if (myCoursesController != null) {
            myCoursesController.refreshCourses();
        }
    }

    // === Télécharger un cours P2P et l’installer localement (.crs) puis rafraîchir "Mes cours"
    private void handleDownloadFromP2P(org.example.model.Course course) {
        if (course == null) return;

        // petit dialogue de progression
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Téléchargement");
        dlg.setHeaderText("Téléchargement du cours…");
        ProgressIndicator pi = new ProgressIndicator();
        dlg.getDialogPane().setContent(pi);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        // tâche en arrière-plan
        javafx.concurrent.Task<byte[]> task = new javafx.concurrent.Task<>() {
            @Override
            protected byte[] call() throws Exception {
                return p2pService.downloadCourseBytes(course);
            }
        };

        task.setOnSucceeded(ev -> {
            dlg.close();
            byte[] data = task.getValue();
            if (data == null || data.length == 0) {
                showError("Téléchargement", "Le téléchargement a échoué (aucune donnée).");
                return;
            }
            try {
                String filePath = course.getFilePath(); // p2p://host:port/<courseId>
                String remoteId = filePath.substring(filePath.lastIndexOf('/') + 1);

                String saved = org.example.service.CourseRepository.installDownloadedCourse(
                        data, remoteId, course.getTitle()
                );
                refreshMyCourses();
                Alert ok = new Alert(Alert.AlertType.INFORMATION,
                        "Cours téléchargé : data/courses/" + saved);
                ok.setHeaderText(null);
                ok.showAndWait();
            } catch (Exception ex) {
                ex.printStackTrace();
                showError("Téléchargement", "Erreur à l'installation : " + ex.getMessage());
            }
        });

        task.setOnFailed(ev -> {
            dlg.close();
            Throwable e = task.getException();
            showError("Téléchargement", (e != null ? e.getMessage() : "échec inconnu"));
        });

        // Annulation (facultatif)
        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.CANCEL) task.cancel(true);
            return null;
        });

        Thread t = new Thread(task, "download-course");
        t.setDaemon(true);
        t.start();
        dlg.show();
    }
}