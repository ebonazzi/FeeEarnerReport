package net.javalover.feeearner.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.model.RunInfo;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.service.SpreadsheetService;

public class GenerateAllPastWindow {

    private final SpreadsheetService spreadsheetSvc;
    private final RunRepository runRepo;
    private final AppConfig config;

    public GenerateAllPastWindow(SpreadsheetService spreadsheetSvc, RunRepository runRepo,
                                 AppConfig config) {
        this.spreadsheetSvc = spreadsheetSvc;
        this.runRepo        = runRepo;
        this.config         = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Generate All Past Spreadsheets");

        var runTable = buildRunTable();
        runTable.setItems(FXCollections.observableArrayList(runRepo.getAllRuns()));

        var totalLabel     = new Label("Total: —");
        var completedLabel = new Label("Completed: 0");
        var remainingLabel = new Label("Remaining: —");
        var failureList    = new ListView<String>();
        failureList.setItems(FXCollections.observableArrayList());

        var generateBtn = new Button("Generate All Past");
        var exitBtn     = new Button("Exit");
        exitBtn.setOnAction(e -> stage.close());

        final ProgressTracker[] trackerRef = { null };
        var timeline = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            var t = trackerRef[0];
            if (t == null) return;
            int completed = t.completed().get(), total = t.total().get(), failed = t.failed().get();
            totalLabel.setText("Total: " + total);
            completedLabel.setText("Completed: " + (completed + failed));
            remainingLabel.setText("Remaining: " + Math.max(0, total - completed - failed));
            t.failures().forEach(f -> {
                String entry = f.usrID() + " | " + f.feeEarner() + " | " + f.errorMessage();
                if (!failureList.getItems().contains(entry)) failureList.getItems().add(entry);
            });
        }));
        timeline.setCycleCount(Animation.INDEFINITE);

        generateBtn.setOnAction(e -> {
            var selected = runTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                var a = new Alert(Alert.AlertType.WARNING, "Select a run first.", ButtonType.OK);
                a.initOwner(stage);
                a.showAndWait();
                return;
            }
            generateBtn.setDisable(true);
            var tracker = new ProgressTracker(0);
            trackerRef[0] = tracker;
            timeline.play();
            int runId = selected.runId();

            var thread = new Thread(() -> {
                boolean[] showAlert = { true };
                try {
                    spreadsheetSvc.generateAllFromArchive(runId, config, tracker);
                    Platform.runLater(() -> totalLabel.setText("Total: " + tracker.total().get()));
                } catch (Exception ex) {
                    showAlert[0] = false;
                    Platform.runLater(() -> {
                        var a = new Alert(Alert.AlertType.ERROR);
                        a.setTitle("Error");
                        a.setContentText("Regeneration failed: " + String.valueOf(ex.getMessage()));
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
                            a.setContentText("Regeneration complete. Completed: " +
                                (t != null ? t.completed().get() : 0) +
                                "  Failed: " + (t != null ? t.failed().get() : 0));
                            a.showAndWait();
                        }
                        generateBtn.setDisable(false);
                    });
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        var statsBox = new HBox(20, totalLabel, completedLabel, remainingLabel);
        var buttons  = new HBox(10, generateBtn, exitBtn);
        var root = new VBox(10, new Label("Select a past run:"), runTable,
                            statsBox, new Label("Failures:"), failureList, buttons);
        root.setPadding(new Insets(12));
        VBox.setVgrow(runTable, Priority.ALWAYS);
        VBox.setVgrow(failureList, Priority.ALWAYS);

        stage.setScene(new Scene(root, 640, 560));
        stage.showAndWait();
    }

    public static TableView<RunInfo> buildRunTable() {
        var table = new TableView<RunInfo>();
        var idCol = new TableColumn<RunInfo, String>("Run ID");
        idCol.setPrefWidth(80);
        idCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().runId())));
        var dayCol = new TableColumn<RunInfo, String>("Day Run");
        dayCol.setPrefWidth(120);
        dayCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().dayRun())));
        var startCol = new TableColumn<RunInfo, String>("Started");
        startCol.setPrefWidth(180);
        startCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().startedAt())));
        var finishCol = new TableColumn<RunInfo, String>("Finished");
        finishCol.setPrefWidth(180);
        finishCol.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().finishedAt() != null ? String.valueOf(d.getValue().finishedAt()) : "—"));
        table.getColumns().addAll(idCol, dayCol, startCol, finishCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }
}
