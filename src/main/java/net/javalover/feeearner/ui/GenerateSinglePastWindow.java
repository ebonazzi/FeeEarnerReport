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
import java.util.HashSet;
import java.util.Set;

public class GenerateSinglePastWindow {

    private final SpreadsheetService spreadsheetSvc;
    private final RunRepository runRepo;
    private final AppConfig config;
    private TableView<FeeEarnerRun> feTable;
    // Window-instance-scoped: does not survive closing and reopening this window while a
    // background regeneration is still running, so that narrow sequence can still race a second
    // concurrent regeneration for the same fee earner.
    private final Set<Integer> inFlightUsrIds = new HashSet<>();

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

        feTable = buildFeeEarnerTable(stage);

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
                if (empty) {
                    setGraphic(null);
                } else {
                    var fer = getTableView().getItems().get(getIndex());
                    btn.setDisable(inFlightUsrIds.contains(fer.usrID()));
                    setGraphic(btn);
                }
            }
        });

        table.getColumns().addAll(idCol, nameCol, actionCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }

    private void handleRegenerate(FeeEarnerRun fer, Window owner) {
        inFlightUsrIds.add(fer.usrID());
        feTable.refresh();
        GenerationProgressDialog.run(owner, GenerationProgressDialog.Mode.REGENERATE, fer.feeEarner(),
            () -> spreadsheetSvc.generateFromArchive(fer.usrID(), fer.runId(), config),
            () -> {
                inFlightUsrIds.remove(fer.usrID());
                feTable.refresh();
            });
    }
}
