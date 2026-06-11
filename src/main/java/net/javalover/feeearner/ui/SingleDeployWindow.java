package net.javalover.feeearner.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FeeEarner;
import net.javalover.feeearner.repository.FeeEarnerRepository;
import net.javalover.feeearner.service.DeployService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SingleDeployWindow {

    private final DeployService deploySvc;
    private final FeeEarnerRepository feeEarnerRepo;
    private final AppConfig config;

    public SingleDeployWindow(DeployService deploySvc, FeeEarnerRepository feeEarnerRepo,
                              AppConfig config) {
        this.deploySvc     = deploySvc;
        this.feeEarnerRepo = feeEarnerRepo;
        this.config        = config;
    }

    public void show(Window owner) {
        var feeEarners = merge(feeEarnerRepo.getLeadFeeEarners(),
                               feeEarnerRepo.getMatterFeeEarners());

        var stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Deploy Single Spreadsheet");
        stage.setWidth(700);
        stage.setHeight(500);

        var table = buildTable();
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

    private TableView<FeeEarner> buildTable() {
        var table = new TableView<FeeEarner>();

        var idCol = new TableColumn<FeeEarner, String>("User ID");
        idCol.setPrefWidth(80);
        idCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().usrID())));

        var nameCol = new TableColumn<FeeEarner, String>("Fee Earner");
        nameCol.setPrefWidth(240);
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().feeEarner()));

        var actionCol = new TableColumn<FeeEarner, Void>("Action");
        actionCol.setPrefWidth(140);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Deploy");
            {
                btn.setOnAction(event -> {
                    var fe = getTableView().getItems().get(getIndex());
                    handleDeploy(fe, getScene().getWindow());
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

    private void handleDeploy(FeeEarner fe, Window owner) {
        try {
            deploySvc.deployForFeeEarner(fe.usrID(), config);
            showAlert(owner, Alert.AlertType.INFORMATION, "Success",
                "Deployed to SharePoint for " + fe.feeEarner());
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

    private static List<FeeEarner> merge(List<FeeEarner> lead, List<FeeEarner> matter) {
        var result  = new ArrayList<>(lead);
        var leadIds = lead.stream().map(FeeEarner::usrID).collect(Collectors.toSet());
        matter.stream().filter(fe -> !leadIds.contains(fe.usrID())).forEach(result::add);
        return result;
    }
}
