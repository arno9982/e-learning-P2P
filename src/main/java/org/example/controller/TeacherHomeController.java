package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Button;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;     // pour FXMLLoader
import javafx.scene.Parent;       // pour Parent
import javafx.scene.Scene;        // pour Scene
import javafx.scene.control.*;    // pour RadioButton, TextField, Button, Alert, etc.
import javafx.stage.Stage;        // pour Stage

import javafx.event.ActionEvent;
import java.io.IOException;

public class TeacherHomeController {

    @FXML
    private ToggleButton presenceToggle;

    @FXML
    private Button logoutButton;



    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            System.out.println("Déconnexion de l'utilisateur enseignant...");

            // Charger la page de login
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/login.fxml"));
            Parent root = loader.load();

            // Créer une nouvelle scène
            Scene scene = new Scene(root);

            // Récupérer la fenêtre (stage) actuelle via le bouton cliqué
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // Mettre à jour la scène avec login.fxml
            stage.setScene(scene);
            stage.setTitle("Connexion");
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Erreur lors du chargement de la page de login.");
        }
    }

}