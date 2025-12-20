package org.example.controller; // Assurez-vous que le package est correct

import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.embed.swing.SwingFXUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;

public class PDFViewer extends ScrollPane {

    private VBox pageContainer;
    private PDDocument document;
    private ExecutorService executorService = Executors.newFixedThreadPool(2); // Utilisation d'un thread pool pour le rendu en arrière-plan

    public PDFViewer() {
        this.pageContainer = new VBox(10); // Espacement entre les pages
        this.setContent(pageContainer);
        this.setFitToWidth(true); // Permet le défilement horizontal si la page est trop large
    }

    public void setDocument(PDDocument newDocument) {
        // Fermer l'ancien document s'il existe
        if (this.document != null) {
            try {
                this.document.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.document = newDocument;
        renderDocument();
    }

    private void renderDocument() {
        pageContainer.getChildren().clear();
        if (document == null) return;

        PDFRenderer renderer = new PDFRenderer(document);
        int pageCount = document.getNumberOfPages();

        for (int i = 0; i < pageCount; i++) {
            final int pageIndex = i;
            ImageView imageView = new ImageView();
            imageView.setFitWidth(800); // Définissez une largeur fixe pour toutes les pages
            imageView.setPreserveRatio(true);
            pageContainer.getChildren().add(imageView);

            // Utilisation du thread pool pour le rendu de chaque page en arrière-plan
            executorService.submit(() -> {
                try {
                    java.awt.image.BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, 150); // Rendu à 150 DPI pour un bon compromis qualité/performance
                    Image fxImage = SwingFXUtils.toFXImage(pageImage, null);

                    Platform.runLater(() -> {
                        imageView.setImage(fxImage);
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
