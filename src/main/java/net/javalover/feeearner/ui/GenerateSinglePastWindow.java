package net.javalover.feeearner.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FeeEarnerRun;
import net.javalover.feeearner.model.RunInfo;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.service.SpreadsheetService;

public class GenerateSinglePastWindow {

    private final SpreadsheetService spreadsheetSvc;
    private final RunRepository runRepo;
    private final AppConfig config;

    public GenerateSinglePastWindow(SpreadsheetService spreadsheetSvc, RunRepository runRepo,
                                    AppConfig config) {
        this.spreadsheetSvc = spreadsheetSvc;
        this.runRepo        = runRepo;
        this.config         = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Generate Single Past Spreadsheet");
        stage.setWidth(720);
        stage.setHeight(620);

        var runTable = GenerateAllPastWindow.buildRunTable();
        runTable.setItems(FXCollections.observableArrayList(runRepo.getAllRuns()));

        var feTable = buildFeeEarnerTable(stage);

        runTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                feTable.setItems(FXCollections.observableArrayList(
                    runRepo.getFeeEarnerRunsForRun(sel.runId())));
            } else {
                feTable.getItems().clear();
            }
        });

        var closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());
        var footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8));

        var root = new VBox(8,
            new Label("Select a past run:"), runTable,
            new Label("Then a fee earner to regenerate:"), feTable, footer);
        root.setPadding(new Insets(10));
        VBox.setVgrow(runTable, Priority.ALWAYS);
        VBox.setVgrow(feTable, Priority.ALWAYS);

        stage.setScene(new Scene(root));
        stage.show();
    }

    private TableView<FeeEarnerRun> buildFeeEarnerTable(Stage stage) {
        var table = new TableView<FeeEarnerRun>();

        var idCol = new TableColumn<FeeEarnerRun, String>("User ID");
        idCol.setPrefWidth(80);
        idCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().usrID())));

        var nameCol = new TableColumn<FeeEarnerRun, String>("Fee Earner");
        nameCol.setPrefWidth(240);
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().feeEarner()));

        var actionCol = new TableColumn<FeeEarnerRun, Void>("Action");
        actionCol.setPrefWidth(140);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Regenerate");
            {
                btn.setOnAction(event -> {
                    var fer = getTableView().getItems().get(getIndex());
                    handleRegenerate(fer, getScene().getWindow());
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(idCol, nameCol, actionCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }

    private void handleRegenerate(FeeEarnerRun fer, Window owner) {
        try {
            spreadsheetSvc.generateFromArchive(fer.usrID(), fer.runId(), config);
            showAlert(owner, Alert.AlertType.INFORMATION, "Success",
                "Regenerated spreadsheet for " + fer.feeEarner());
        } catch (Exception ex) {
            showAlert(owner, Alert.AlertType.ERROR, "Error",
                ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private static void showAlert(Window owner, Alert.AlertType type, String title, String msg) {
        var alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
}
