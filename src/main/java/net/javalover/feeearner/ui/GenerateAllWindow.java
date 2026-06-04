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
import net.javalover.feeearner.model.FeeEarner;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.repository.FeeEarnerRepository;
import net.javalover.feeearner.service.RunService;
import net.javalover.feeearner.service.SpreadsheetService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GenerateAllWindow {

    private final SpreadsheetService spreadsheetSvc;
    private final RunService runService;
    private final FeeEarnerRepository feeEarnerRepo;
    private final AppConfig config;

    public GenerateAllWindow(SpreadsheetService spreadsheetSvc, RunService runService,
                             FeeEarnerRepository feeEarnerRepo, AppConfig config) {
        this.spreadsheetSvc = spreadsheetSvc;
        this.runService     = runService;
        this.feeEarnerRepo  = feeEarnerRepo;
        this.config         = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Generate All Spreadsheets");

        var totalLabel     = new Label("Total: —");
        var completedLabel = new Label("Completed: 0");
        var remainingLabel = new Label("Remaining: —");

        var failureList = new ListView<String>();
        failureList.setItems(FXCollections.observableArrayList());

        var generateBtn = new Button("Generate All");
        var exitBtn     = new Button("Exit");

        exitBtn.setOnAction(e -> stage.close());

        final ProgressTracker[] trackerRef = { null };

        var timeline = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            var t = trackerRef[0];
            if (t == null) return;
            int completed = t.completed().get();
            int total     = t.total().get();
            int failed    = t.failed().get();
            completedLabel.setText("Completed: " + (completed + failed));
            remainingLabel.setText("Remaining: " + Math.max(0, total - completed - failed));
            t.failures().forEach(f -> {
                String entry = f.usrID() + " | " + f.feeEarner() + " | " + f.errorMessage();
                if (!failureList.getItems().contains(entry)) {
                    failureList.getItems().add(entry);
                }
            });
        }));
        timeline.setCycleCount(Animation.INDEFINITE);

        generateBtn.setOnAction(e -> {
            generateBtn.setDisable(true);
            var tracker = new ProgressTracker(0);
            trackerRef[0] = tracker;
            timeline.play();

            var thread = new Thread(() -> {
                try {
                    var leadFEs   = feeEarnerRepo.getLeadFeeEarners();
                    var matterFEs = feeEarnerRepo.getMatterFeeEarners();
                    var feeEarners = merge(leadFEs, matterFEs);

                    tracker.total().set(feeEarners.size());
                    Platform.runLater(() -> totalLabel.setText("Total: " + feeEarners.size()));

                    int runId = runService.startRun();
                    spreadsheetSvc.generateAll(feeEarners, runId, LocalDate.now(), config, tracker);
                    runService.finishRun(runId);
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        var alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setContentText("Generation failed: " + ex.getMessage());
                        alert.showAndWait();
                    });
                } finally {
                    Platform.runLater(() -> {
                        timeline.stop();
                        var t = trackerRef[0];
                        if (t != null) {
                            int completed = t.completed().get();
                            int failed    = t.failed().get();
                            completedLabel.setText("Completed: " + (completed + failed));
                            remainingLabel.setText("Remaining: 0");
                        }
                        var alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Complete");
                        alert.setContentText("Generation complete. Completed: " +
                            (trackerRef[0] != null ? trackerRef[0].completed().get() : 0) +
                            "  Failed: " +
                            (trackerRef[0] != null ? trackerRef[0].failed().get() : 0));
                        alert.showAndWait();
                        generateBtn.setDisable(false);
                    });
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        var statsBox = new HBox(20, totalLabel, completedLabel, remainingLabel);
        var buttons  = new HBox(10, generateBtn, exitBtn);
        var root = new VBox(10, statsBox,
                            new Label("Failures:"), failureList, buttons);
        root.setPadding(new Insets(12));
        VBox.setVgrow(failureList, Priority.ALWAYS);

        stage.setScene(new Scene(root, 600, 450));
        stage.showAndWait();
    }

    private static List<FeeEarner> merge(List<FeeEarner> lead, List<FeeEarner> matter) {
        var result  = new ArrayList<>(lead);
        var leadIds = lead.stream().map(FeeEarner::usrID).collect(Collectors.toSet());
        matter.stream()
              .filter(fe -> !leadIds.contains(fe.usrID()))
              .forEach(result::add);
        return result;
    }
}
