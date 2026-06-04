package net.javalover.feeearner.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.*;
import net.javalover.feeearner.model.AppParam;
import net.javalover.feeearner.service.ParameterService;

public class ParameterEditorWindow {

    private final ParameterService paramSvc;

    public ParameterEditorWindow(ParameterService paramSvc) {
        this.paramSvc = paramSvc;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Edit Parameters");

        var table = new TableView<AppParam>();
        table.setEditable(true);

        var nameCol = new TableColumn<AppParam, String>("Parameter Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(250);
        nameCol.setEditable(false);

        var valueCol = new TableColumn<AppParam, String>("Value");
        valueCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().value()));
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setPrefWidth(300);
        valueCol.setEditable(true);
        valueCol.setOnEditCommit(event -> {
            var old = event.getRowValue();
            var updated = new AppParam(old.paramId(), old.name(), event.getNewValue(),
                    old.defaultValue(), old.active());
            table.getItems().set(event.getTablePosition().getRow(), updated);
        });

        table.getColumns().addAll(nameCol, valueCol);
        table.setItems(FXCollections.observableArrayList(paramSvc.loadAll()));

        var saveBtn = new Button("Save");
        var cancelBtn = new Button("Cancel");

        saveBtn.setOnAction(e -> {
            for (var param : table.getItems()) {
                var result = ParameterService.validate(param.name(), param.value());
                if (!result.valid()) {
                    var alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Validation Error");
                    alert.setHeaderText("Invalid value for: " + param.name());
                    alert.setContentText(result.message());
                    alert.showAndWait();
                    return;
                }
            }
            table.getItems().forEach(paramSvc::save);
            paramSvc.reload();
            stage.close();
        });

        cancelBtn.setOnAction(e -> stage.close());

        var buttons = new HBox(10, saveBtn, cancelBtn);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        var root = new VBox(10, table, buttons);
        root.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);

        stage.setScene(new Scene(root, 600, 400));
        stage.showAndWait();
    }
}
