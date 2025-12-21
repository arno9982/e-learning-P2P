package org.example;

import java.io.File;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Initialisation du dossier de données autorisé
            prepareDataFolder();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            if (getClass().getResource("/css/ui.css") != null) {
                scene.getStylesheets().add(getClass().getResource("/css/ui.css").toExternalForm());
            }

            primaryStage.setTitle("EduConnect - P2P Learning");
            
            // Garantit que les boutons réduire/agrandir/fermer sont présents
            primaryStage.initStyle(StageStyle.DECORATED); 
            primaryStage.setResizable(true);

            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void prepareDataFolder() {
        String path;
        String os = System.getProperty("os.name").toLowerCase();
        
        // Résout l'erreur de permission dans Program Files
        if (os.contains("win")) {
            path = System.getenv("APPDATA") + File.separator + "E-Learning-P2P";
        } else {
            path = System.getProperty("user.home") + File.separator + ".e-learning-p2p";
        }

        File dataDir = new File(path, "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        
        File coursesDir = new File(path, "courses");
        if (!coursesDir.exists()) coursesDir.mkdirs();

        // On définit une propriété système pour que les autres classes y accèdent
        System.setProperty("app.base.path", path);
    }

    public static void main(String[] args) {
        launch(args);
    }
}