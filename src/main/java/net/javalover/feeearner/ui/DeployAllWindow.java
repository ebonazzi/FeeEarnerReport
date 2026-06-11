package net.javalover.feeearner.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.service.DeployService;

public class DeployAllWindow {

    private final DeployService deploySvc;
    private final RunRepository runRepo;
    private final AppConfig config;

    public DeployAllWindow(DeployService deploySvc, RunRepository runRepo, AppConfig config) {
        this.deploySvc = deploySvc;
        this.runRepo   = runRepo;
        this.config    = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Deploy All Spreadsheets");

        var totalLabel     = new Label("Total: —");
        var completedLabel = new Label("Completed: 0");
        var remainingLabel = new Label("Remaining: —");
        var failureList    = new ListView<String>();
        failureList.setItems(FXCollections.observableArrayList());

        var deployBtn = new Button("Deploy All");
        var exitBtn   = new Button("Exit");
        exitBtn.setOnAction(e -> stage.close());

        final ProgressTracker[] trackerRef = { null };
        var timeline = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            var t = trackerRef[0];
            if (t == null) return;
            int completed = t.completed().get(), total = t.total().get(), failed = t.failed().get();
            completedLabel.setText("Completed: " + (completed + failed));
            remainingLabel.setText("Remaining: " + Math.max(0, total - completed - failed));
            t.failures().forEach(f -> {
                String entry = f.usrID() + " | " + f.feeEarner() + " | " + f.errorMessage();
                if (!failureList.getItems().contains(entry)) failureList.getItems().add(entry);
            });
        }));
        timeline.setCycleCount(Animation.INDEFINITE);

        deployBtn.setOnAction(e -> {
            deployBtn.setDisable(true);
            var tracker = new ProgressTracker(0);
            trackerRef[0] = tracker;
            timeline.play();

            var thread = new Thread(() -> {
                boolean[] showAlert = { true };
                try {
                    var allRuns = runRepo.getAllRuns();
                    if (allRuns.isEmpty()) {
                        showAlert[0] = false;
                        Platform.runLater(() -> {
                            timeline.stop();
                            var a = new Alert(Alert.AlertType.WARNING);
                            a.setTitle("No Runs Found");
                            a.setContentText("No runs found. Generate spreadsheets first.");
                            a.showAndWait();
                            deployBtn.setDisable(false);
                        });
                        return;
                    }
                    int latestRunId = allRuns.get(0).runId();
                    var feeEarnerRuns = runRepo.getFeeEarnerRunsForRun(latestRunId);
                    tracker.total().set(feeEarnerRuns.size());
                    Platform.runLater(() -> totalLabel.setText("Total: " + feeEarnerRuns.size()));
                    deploySvc.deployAll(feeEarnerRuns, config, tracker);
                } catch (Exception ex) {
                    showAlert[0] = false;
                    Platform.runLater(() -> {
                        var a = new Alert(Alert.AlertType.ERROR);
                        a.setTitle("Error");
                        a.setContentText("Deploy failed: " + String.valueOf(ex.getMessage()));
                        a.showAndWait();
                    });
                } finally {
                    final boolean show = showAlert[0];
                    Platform.runLater(() -> {
                        timeline.stop();
                        var t = trackerRef[0];
                        if (t != null) {
                            completedLabel.setText("Completed: " + (t.completed().get() + t.failed().get()));
                            remainingLabel.setText("Remaining: 0");
                        }
                        if (show) {
                            var a = new Alert(Alert.AlertType.INFORMATION);
                            a.setTitle("Complete");
                            a.setContentText("Deploy complete. Completed: " +
                                (t != null ? t.completed().get() : 0) +
                                "  Failed: " + (t != null ? t.failed().get() : 0));
                            a.showAndWait();
                        }
                        deployBtn.setDisable(false);
                    });
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        var statsBox = new HBox(20, totalLabel, completedLabel, remainingLabel);
        var buttons  = new HBox(10, deployBtn, exitBtn);
        var root = new VBox(10, statsBox, new Label("Failures:"), failureList, buttons);
        root.setPadding(new Insets(12));
        VBox.setVgrow(failureList, Priority.ALWAYS);

        stage.setScene(new Scene(root, 600, 450));
        stage.showAndWait();
    }
}
