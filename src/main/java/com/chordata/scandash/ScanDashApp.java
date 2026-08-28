package com.chordata.scandash;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * JavaFX entry point: loads the dashboard view and applies the stylesheet.
 */
public class ScanDashApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chordata/scandash/dashboard.fxml"));
        Scene scene = new Scene(loader.load(), 1440, 900);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/com/chordata/scandash/styles.css")).toExternalForm());

        stage.setTitle("Scan Log Dashboard");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
