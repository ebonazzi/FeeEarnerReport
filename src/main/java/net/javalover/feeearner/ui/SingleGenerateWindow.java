package net.javalover.feeearner.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FeeEarner;
import net.javalover.feeearner.repository.FeeEarnerRepository;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.service.SpreadsheetService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SingleGenerateWindow {

    private final SpreadsheetService spreadsheetSvc;
    private final RunRepository runRepo;
    private final FeeEarnerRepository feeEarnerRepo;
    private final AppConfig config;

    public SingleGenerateWindow(SpreadsheetService spreadsheetSvc,
                                RunRepository runRepo,
                                FeeEarnerRepository feeEarnerRepo,
                                AppConfig config) {
        this.spreadsheetSvc = spreadsheetSvc;
        this.runRepo        = runRepo;
        this.feeEarnerRepo  = feeEarnerRepo;
        this.config         = config;
    }

    public void show(Window owner) {
        var feeEarners = merge(feeEarnerRepo.getLeadFeeEarners(),
                               feeEarnerRepo.getMatterFeeEarners());

        var stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Generate Single Spreadsheet");
        stage.setWidth(700);
        stage.setHeight(500);

        var table = buildTable(stage);
        table.setItems(FXCollections.observableArrayList(feeEarners));

        var closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());
        var footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8, 12, 8, 12));

        var root = new BorderPane();
        root.setCenter(table);
        root.setBottom(footer);
        BorderPane.setMargin(table, new Insets(8, 8, 0, 8));

        stage.setScene(new Scene(root));
        stage.show();
    }

    @SuppressWarnings("unchecked")
    private TableView<FeeEarner> buildTable(Stage stage) {
        var table = new TableView<FeeEarner>();

        var idCol = new TableColumn<FeeEarner, Integer>("User ID");
        idCol.setPrefWidth(80);
        idCol.setCellValueFactory(new PropertyValueFactory<>("usrID"));

        var nameCol = new TableColumn<FeeEarner, String>("Fee Earner");
        nameCol.setPrefWidth(220);
        nameCol.setCellValueFactory(new PropertyValueFactory<>("feeEarner"));

        var emailCol = new TableColumn<FeeEarner, String>("Email");
        emailCol.setPrefWidth(220);
        emailCol.setCellValueFactory(new PropertyValueFactory<>("usrEmail"));

        var actionCol = new TableColumn<FeeEarner, Void>("Action");
        actionCol.setPrefWidth(120);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Generate");
            {
                btn.setOnAction(event -> {
                    var fe = getTableView().getItems().get(getIndex());
                    handleGenerate(fe, getScene().getWindow());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(idCol, nameCol, emailCol, actionCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }

    private void handleGenerate(FeeEarner fe, Window owner) {
        var mostRecent = runRepo.getMostRecent(fe.usrID());
        if (mostRecent.isEmpty()) {
            showAlert(owner, Alert.AlertType.WARNING,
                      "No Prior Run",
                      "A bulk run must be completed first.");
            return;
        }
        try {
            spreadsheetSvc.generateForFeeEarner(
                    fe,
                    mostRecent.get().runId(),
                    LocalDate.now(),
                    config,
                    evt -> {});
            showAlert(owner, Alert.AlertType.INFORMATION,
                      "Success",
                      "Spreadsheet generated for " + fe.feeEarner());
        } catch (Exception ex) {
            showAlert(owner, Alert.AlertType.ERROR,
                      "Error",
                      ex.getMessage());
        }
    }

    private static void showAlert(Window owner, Alert.AlertType type,
                                  String title, String message) {
        var alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }

    private static List<FeeEarner> merge(List<FeeEarner> lead, List<FeeEarner> matter) {
        var result  = new ArrayList<>(lead);
        var leadIds = lead.stream().map(FeeEarner::usrID).collect(Collectors.toSet());
        matter.stream().filter(fe -> !leadIds.contains(fe.usrID())).forEach(result::add);
        return result;
    }
}
