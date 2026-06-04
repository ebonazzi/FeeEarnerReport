package net.javalover.feeearner.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.javalover.feeearner.model.FeeEarnerRun;

public class FeeEarnerDetailWindow {

    private final FeeEarnerRun run;

    public FeeEarnerDetailWindow(FeeEarnerRun run) {
        this.run = run;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Fee Earner Run Detail");

        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));

        String[][] rows = {
            {"Run ID",         String.valueOf(run.runId())},
            {"Day Run",        run.dayRun().toString()},
            {"User ID",        String.valueOf(run.usrID())},
            {"Fee Earner",     run.feeEarner()},
            {"Email",          run.usrEmail()},
            {"Excel Filename", run.excelFilename()},
            {"Stored At",      run.storedAt() != null ? run.storedAt().toString() : ""}
        };

        for (int i = 0; i < rows.length; i++) {
            var keyLabel = new Label(rows[i][0] + ":");
            keyLabel.setFont(Font.font(null, FontWeight.BOLD, 13));
            var valLabel = new Label(rows[i][1]);
            grid.add(keyLabel, 0, i);
            grid.add(valLabel, 1, i);
        }

        var col0 = new ColumnConstraints();
        col0.setPrefWidth(140);
        var col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col0, col1);

        var closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());

        var root = new VBox(16, grid, closeBtn);
        root.setPadding(new Insets(0, 16, 16, 16));

        stage.setScene(new Scene(root, 450, 320));
        stage.showAndWait();
    }
}
