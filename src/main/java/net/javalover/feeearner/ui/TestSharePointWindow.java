package net.javalover.feeearner.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.service.DeployService;

import java.io.PrintWriter;
import java.io.StringWriter;

public class TestSharePointWindow {

    private final DeployService deploySvc;
    private final AppConfig config;

    public TestSharePointWindow(DeployService deploySvc, AppConfig config) {
        this.deploySvc = deploySvc;
        this.config    = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Test Sharepoint");
        stage.setWidth(600);
        stage.setHeight(450);

        var paramsArea = new TextArea(formatParams());
        paramsArea.setEditable(false);
        paramsArea.setWrapText(true);
        paramsArea.setPrefRowCount(6);

        var resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(true);

        var testBtn = new Button("Test");
        testBtn.setOnAction(e -> resultArea.setText(runTest()));

        var dismissBtn = new Button("Dismiss");
        dismissBtn.setOnAction(e -> stage.close());

        var footer = new HBox(10, testBtn, dismissBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8, 12, 8, 12));

        var center = new VBox(8, paramsArea, resultArea);
        center.setPadding(new Insets(12, 12, 0, 12));
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        var root = new BorderPane();
        root.setCenter(center);
        root.setBottom(footer);

        stage.setScene(new Scene(root));
        stage.showAndWait();
    }

    private String formatParams() {
        return "Client ID:    " + config.sharePointClientId() + "\n"
            + "Tenant ID:    " + config.sharePointTenantId() + "\n"
            + "Secret:       " + config.sharePointSecretId() + "\n"
            + "Host:         " + config.sharePointHost() + "\n"
            + "Site Name:    " + config.sharePointSiteName() + "\n"
            + "Target Dir:   " + config.sharePointTargetDir();
    }

    private String runTest() {
        try {
            var result = deploySvc.testConnection(config);
            var sb = new StringBuilder("SUCCESS\nFile stored: ").append(result.filename());
            if (result.cleanupWarning() != null) {
                sb.append("\nNote: cleanup delete failed: ").append(result.cleanupWarning());
            }
            return sb.toString();
        } catch (Exception ex) {
            var sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            return sw.toString();
        }
    }
}
