package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;     // pour FXMLLoader
import javafx.scene.Parent;       // pour Parent
import javafx.scene.Scene;        // pour Scene
import javafx.scene.control.*;    // pour RadioButton, TextField, Button, Alert, etc.
import javafx.stage.Stage;        // pour Stage

import java.io.IOException;       // pour IOException


import javax.swing.*;

public class LoginController {

    @FXML
    private RadioButton studentRadio;

    @FXML
    private RadioButton teacherRadio;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField password;

    @FXML
    private Button loginButton;

    @FXML
    private void handleLogin() {
        String email = emailField.getText();
        String pass = password.getText();

        if (email.isEmpty() || pass.isEmpty()) {
            showAlert("Erreur", "Veuillez remplir tous les champs !");
            return;
        }

        try {
            if (studentRadio.isSelected()) {
                loadHomePage("/student_home.fxml", "Accueil Étudiant");
            } else if (teacherRadio.isSelected()) {
                loadHomePage("/teacher_home.fxml", "Accueil Enseignant");
            } else {
                showAlert("Erreur", "Veuillez sélectionner un rôle !");
            }
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Erreur", "Erreur lors du chargement de la page d'accueil.");
        }
    }

    private void loadHomePage(String fxmlPath, String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = loader.load();
        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.setTitle(title);
        stage.show();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}