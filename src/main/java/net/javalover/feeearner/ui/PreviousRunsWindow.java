package net.javalover.feeearner.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.RunRepository;

public class PreviousRunsWindow {

    private final RunRepository runRepo;

    public PreviousRunsWindow(RunRepository runRepo) {
        this.runRepo = runRepo;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Previous Runs");

        // --- Top table (RunInfo) ---
        var topTable = new TableView<RunInfo>();
        try {
            topTable.setItems(FXCollections.observableArrayList(runRepo.getAllRuns()));
        } catch (Exception ex) {
            var alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setContentText("Failed to load runs: " + String.valueOf(ex.getMessage()));
            alert.showAndWait();
        }

        var runIdCol = new TableColumn<RunInfo, String>("Run ID");
        runIdCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                String.valueOf(c.getValue().runId())));
        runIdCol.setPrefWidth(70);

        var dayRunCol = new TableColumn<RunInfo, String>("Day Run");
        dayRunCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().dayRun().toString()));
        dayRunCol.setPrefWidth(100);

        var startedCol = new TableColumn<RunInfo, String>("Started At");
        startedCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().startedAt().toString()));
        startedCol.setPrefWidth(160);

        var finishedCol = new TableColumn<RunInfo, String>("Finished At");
        finishedCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().finishedAt() != null
                    ? c.getValue().finishedAt().toString() : ""));
        finishedCol.setPrefWidth(160);

        topTable.getColumns().addAll(runIdCol, dayRunCol, startedCol, finishedCol);

        // --- Bottom table (FeeEarnerRun) ---
        ObservableList<FeeEarnerRun> bottomItems = FXCollections.observableArrayList();
        var bottomTable = new TableView<FeeEarnerRun>();
        bottomTable.setItems(bottomItems);

        var feIdCol = new TableColumn<FeeEarnerRun, String>("User ID");
        feIdCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                String.valueOf(c.getValue().usrID())));
        feIdCol.setPrefWidth(70);

        var feNameCol = new TableColumn<FeeEarnerRun, String>("Fee Earner");
        feNameCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().feeEarner()));
        feNameCol.setPrefWidth(180);

        var feEmailCol = new TableColumn<FeeEarnerRun, String>("Email");
        feEmailCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().usrEmail()));
        feEmailCol.setPrefWidth(180);

        // Filename column — double-click opens SpreadsheetViewerWindow
        var filenameCol = new TableColumn<FeeEarnerRun, String>("Excel Filename");
        filenameCol.setPrefWidth(200);
        filenameCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().excelFilename()));
        filenameCol.setCellFactory(col -> new TableCell<>() {
            {
                setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !isEmpty() && getTableRow() != null
                            && getTableRow().getItem() != null) {
                        new SpreadsheetViewerWindow(getTableRow().getItem()).show(stage);
                        event.consume();
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });

        var storedAtCol = new TableColumn<FeeEarnerRun, String>("Stored At");
        storedAtCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().storedAt() != null
                    ? c.getValue().storedAt().toString() : ""));
        storedAtCol.setPrefWidth(160);

        bottomTable.getColumns().addAll(feIdCol, feNameCol, feEmailCol, filenameCol, storedAtCol);

        // Double-click on a bottom row → FeeEarnerDetailWindow
        bottomTable.setRowFactory(tv -> {
            var row = new TableRow<FeeEarnerRun>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    new FeeEarnerDetailWindow(row.getItem()).show(stage);
                }
            });
            return row;
        });

        // Top table selection → populate bottom table
        topTable.getSelectionModel().selectedItemProperty()
            .addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    try {
                        bottomItems.setAll(runRepo.getFeeEarnerRunsForRun(newVal.runId()));
                    } catch (Exception ex) {
                        var alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setContentText("Failed to load fee earner runs: " + String.valueOf(ex.getMessage()));
                        alert.showAndWait();
                    }
                } else {
                    bottomItems.clear();
                }
            });

        var closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());

        var topLabel    = new Label("Runs:");
        var bottomLabel = new Label("Fee Earner Spreadsheets (select a run above):");

        var root = new VBox(6,
            topLabel, topTable,
            bottomLabel, bottomTable,
            closeBtn);
        root.setPadding(new Insets(12));
        VBox.setVgrow(topTable, Priority.ALWAYS);
        VBox.setVgrow(bottomTable, Priority.ALWAYS);

        stage.setScene(new Scene(root, 900, 600));
        stage.showAndWait();
    }
}
