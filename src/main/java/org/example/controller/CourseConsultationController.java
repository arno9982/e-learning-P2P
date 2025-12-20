package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.example.model.CourseNode;
import org.example.model.courseStructure;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URI;
import javafx.util.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.util.ArrayList;
import java.util.List;

public class CourseConsultationController {

    @FXML private TreeView<CourseNode> courseTreeView;
    @FXML private Label notionTitleLabel;
    @FXML private WebView contentWebView;
    @FXML private MediaView mediaView;
    @FXML private StackPane contentPane;
    @FXML private Button prevButton;
    @FXML private Button nextButton;
    @FXML private HBox mediaControls;
    @FXML private Button playButton;
    @FXML private Button pauseButton;
    @FXML private Slider progressBar;
    private MainController mainController;

    private MediaPlayer mediaPlayer;
    private TreeItem<CourseNode> selectedNode;
    private ArrayList<TreeItem<CourseNode>> notionList;
    private PDFViewer pdfViewer;


    @FXML
    public void initialize() {
        prevButton.setDisable(true);
        nextButton.setDisable(true);
        mediaControls.setVisible(false);
        mediaControls.setManaged(false);

        courseTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                selectedNode = newValue;
                displayContent(selectedNode.getValue());
                updateNavigationButtons();
            } else {
                displayDefaultContent();
            }
        });

    }

    public void loadCourse(File courseFile) {
        stopMedia();
        if (courseFile == null || !courseFile.exists()) {
            displayDefaultContent();
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(courseFile))) {
            courseStructure loadedStructure = (courseStructure) ois.readObject();
            TreeItem<CourseNode> rootItem = buildTreeItem(loadedStructure);
            courseTreeView.setRoot(rootItem);
            notionList = flattenTreeToList(rootItem);
            courseTreeView.getSelectionModel().select(rootItem);
            updateNavigationButtons();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            displayDefaultContent("Erreur de chargement du cours.");
        }
    }

    private void displayDefaultContent() {
        displayDefaultContent("Sélectionnez une notion pour afficher son contenu.");
    }

    private void displayDefaultContent(String message) {
        notionTitleLabel.setText("Aucun cours chargé");
        stopMedia();
        mediaControls.setVisible(false);
        mediaControls.setManaged(false);
        contentWebView.setVisible(true);
        contentWebView.getEngine().loadContent("<p style='padding: 20px; font-size: 16px;'>" + message + "</p>");
    }

    private TreeItem<CourseNode> buildTreeItem(courseStructure structure) {
        TreeItem<CourseNode> item = new TreeItem<>(structure.getRoot());
        item.setExpanded(true);
        for (courseStructure childStructure : structure.getChildren()) {
            item.getChildren().add(buildTreeItem(childStructure));
        }
        return item;
    }

    private void displayContent(CourseNode node) {
        stopMedia();
        contentWebView.setVisible(false);
        mediaView.setVisible(false);
        mediaControls.setVisible(false);
        mediaControls.setManaged(false);
        if (pdfViewer != null) {
            pdfViewer.setVisible(false);
        }

        if (node != null) {
            notionTitleLabel.setText(node.getTitle());
            if (node.getType() == CourseNode.NodeType.NOTION && node.getContentType() != null) {
                switch (node.getContentType()) {
                    case TEXT:
                        contentWebView.setVisible(true);
                        contentWebView.getEngine().loadContent(node.getContent());
                        break;
                    case LINK:
                        contentWebView.setVisible(true);
                        String linkUrl = node.getContent();
                        if (linkUrl != null && !linkUrl.isEmpty()) {
                            contentWebView.getEngine().loadContent(
                                    "<p>Le contenu est un lien. Cliquez ci-dessous pour l'ouvrir :</p>" +
                                            "<button onclick=\"app.openLink('" + linkUrl + "');\">Ouvrir le lien</button>"
                            );
                            contentWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                                    JSObject window = (JSObject) contentWebView.getEngine().executeScript("window");
                                    window.setMember("app", new LinkBridge());
                                }
                            });
                        } else {
                            contentWebView.getEngine().loadContent("Aucun lien à afficher.");
                        }
                        break;
                    case PDF:
                        String filePath = node.getContent();
                        File pdfFile = new File(filePath);

                        if (pdfFile.exists()) {
                            if (pdfViewer == null) {
                                pdfViewer = new PDFViewer();
                                contentPane.getChildren().add(pdfViewer);
                            }
                            pdfViewer.setVisible(true);
                            try {
                                PDDocument document = PDDocument.load(pdfFile);
                                pdfViewer.setDocument(document);
                            } catch (IOException e) {
                                e.printStackTrace();
                                contentWebView.getEngine().loadContent("<h1>Erreur: Impossible de charger le fichier PDF</h1>");
                                contentWebView.setVisible(true);
                            }
                        } else {
                            contentWebView.getEngine().loadContent("<h1>Erreur: Fichier PDF non trouvé</h1><p>Le fichier n'existe pas à l'emplacement: " + pdfFile.getAbsolutePath() + "</p>");
                            contentWebView.setVisible(true);
                        }
                        break;
                    case VIDEO:
                        File videoFile = new File(node.getContent());
                        if (videoFile.exists()) {
                            mediaView.setVisible(true);
                            mediaControls.setVisible(true);
                            mediaControls.setManaged(true);
                            mediaView.setFitWidth(contentPane.getWidth());
                            mediaView.setFitHeight(contentPane.getHeight());

                            Media media = new Media(videoFile.toURI().toString());
                            mediaPlayer = new MediaPlayer(media);
                            mediaView.setMediaPlayer(mediaPlayer);

                            setupMediaControlsAndPlay();
                        } else {
                            contentWebView.setVisible(true);
                            contentWebView.getEngine().loadContent("<h1>Erreur: Fichier vidéo non trouvé</h1><p>Le fichier n'existe pas à l'emplacement: " + videoFile.getAbsolutePath() + "</p>");
                        }
                        break;
                    case AUDIO:
                        File audioFile = new File(node.getContent());
                        if (audioFile.exists()) {
                            mediaView.setVisible(false);
                            mediaControls.setVisible(true);
                            mediaControls.setManaged(true);

                            Media media = new Media(audioFile.toURI().toString());
                            mediaPlayer = new MediaPlayer(media);

                            setupMediaControlsAndPlay();
                        } else {
                            contentWebView.setVisible(true);
                            contentWebView.getEngine().loadContent("<h1>Erreur: Fichier audio non trouvé</h1><p>Le fichier n'existe pas à l'emplacement: " + audioFile.getAbsolutePath() + "</p>");
                        }
                        break;
                }
            } else {
                contentWebView.setVisible(true);
                contentWebView.getEngine().loadContent("<h1>" + node.getTitle() + "</h1><p>Veuillez sélectionner une notion pour voir son contenu.</p>");
            }
        }
    }

    private void setupMediaControlsAndPlay() {
        if (mediaPlayer != null) {
            mediaPlayer.setOnReady(() -> {
                progressBar.setMin(0);
                progressBar.setMax(mediaPlayer.getMedia().getDuration().toSeconds());
                mediaPlayer.play();
            });
            playButton.setOnAction(e -> {
                if (mediaPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                    mediaPlayer.play();
                }
            });
            pauseButton.setOnAction(e -> {
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                    mediaPlayer.pause();
                }
            });
            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (!progressBar.isValueChanging()) {
                    progressBar.setValue(newTime.toSeconds());
                }
            });
            progressBar.valueProperty().addListener(obs -> {
                if (progressBar.isValueChanging()) {
                    mediaPlayer.seek(Duration.seconds(progressBar.getValue()));
                }
            });
            mediaPlayer.setOnEndOfMedia(() -> {
                mediaPlayer.stop();
                progressBar.setValue(0);
            });
        }
    }

    public class LinkBridge {
        public void openLink(String url) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    private void stopMedia() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }

    private ArrayList<TreeItem<CourseNode>> flattenTreeToList(TreeItem<CourseNode> root) {
        ArrayList<TreeItem<CourseNode>> list = new ArrayList<>();
        flattenTreeRecursiveToList(root, list);
        return list;
    }

    private void flattenTreeRecursiveToList(TreeItem<CourseNode> item, ArrayList<TreeItem<CourseNode>> list) {
        if (item.getValue() != null && item.getValue().getType() == CourseNode.NodeType.NOTION) {
            list.add(item);
        }
        for (TreeItem<CourseNode> child : item.getChildren()) {
            flattenTreeRecursiveToList(child, list);
        }
    }

    @FXML
    private void handlePrev() {
        TreeItem<CourseNode> current = courseTreeView.getSelectionModel().getSelectedItem();
        if (current == null || notionList == null || notionList.isEmpty()) {
            return;
        }

        int currentIndex = notionList.indexOf(current);
        if (currentIndex > 0) {
            TreeItem<CourseNode> prev = notionList.get(currentIndex - 1);
            courseTreeView.getSelectionModel().select(prev);
            courseTreeView.scrollTo(courseTreeView.getRow(prev));
        } else {
            // Optionnel: Se déplacer vers le haut (racine de l'arbre)
            // if (courseTreeView.getRoot() != null) {
            //     courseTreeView.getSelectionModel().select(courseTreeView.getRoot());
            // }
        }
    }

    @FXML
    private void handleNext() {
        TreeItem<CourseNode> current = courseTreeView.getSelectionModel().getSelectedItem();
        if (current == null || notionList == null || notionList.isEmpty()) {
            return;
        }

        int currentIndex = notionList.indexOf(current);
        // Si l'élément sélectionné n'est pas une notion (ex: chapitre), on trouve la première notion.
        if (currentIndex == -1) {
            courseTreeView.getSelectionModel().select(notionList.get(0));
            courseTreeView.scrollTo(0);
            return;
        }

        if (currentIndex < notionList.size() - 1) {
            TreeItem<CourseNode> next = notionList.get(currentIndex + 1);
            courseTreeView.getSelectionModel().select(next);
            courseTreeView.scrollTo(courseTreeView.getRow(next));
        }
    }

    private void updateNavigationButtons() {
        if (selectedNode != null && notionList != null) {
            boolean isNotion = selectedNode.getValue().getType() == CourseNode.NodeType.NOTION;
            if (isNotion) {
                int currentIndex = notionList.indexOf(selectedNode);
                prevButton.setDisable(currentIndex <= 0);
                nextButton.setDisable(currentIndex >= notionList.size() - 1);
            } else {
                prevButton.setDisable(true);
                nextButton.setDisable(notionList.isEmpty());
            }
        } else {
            prevButton.setDisable(true);
            nextButton.setDisable(true);
        }
    }
}