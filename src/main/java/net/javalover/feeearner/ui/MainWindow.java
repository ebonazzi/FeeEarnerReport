package net.javalover.feeearner.ui;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.repository.*;
import net.javalover.feeearner.service.*;

public class MainWindow {

    private final ParameterService paramSvc;
    private final RunRepository runRepo;
    private final SpreadsheetService spreadsheetSvc;
    private final RunService runSvc;
    private final EmailService emailSvc;
    private final FeeEarnerRepository feeEarnerRepo;
    // Config is loaded at startup; changes via ParameterEditorWindow take effect on next app start.
    private final AppConfig config;

    public MainWindow(ParameterService paramSvc, RunRepository runRepo,
                      SpreadsheetService spreadsheetSvc, RunService runSvc,
                      EmailService emailSvc, FeeEarnerRepository feeEarnerRepo,
                      AppConfig config) {
        this.paramSvc       = paramSvc;
        this.runRepo        = runRepo;
        this.spreadsheetSvc = spreadsheetSvc;
        this.runSvc         = runSvc;
        this.emailSvc       = emailSvc;
        this.feeEarnerRepo  = feeEarnerRepo;
        this.config         = config;
    }

    public void show(Stage primaryStage) {
        var modifyParams = new MenuItem("Modify Parameters");
        modifyParams.setOnAction(e ->
            new ParameterEditorWindow(paramSvc).show(primaryStage));

        var showRuns = new MenuItem("Show Previous Runs");
        showRuns.setOnAction(e ->
            new PreviousRunsWindow(runRepo).show(primaryStage));

        var generateAll = new MenuItem("Generate All Spreadsheets");
        generateAll.setOnAction(e ->
            new GenerateAllWindow(spreadsheetSvc, runSvc, feeEarnerRepo, config)
                .show(primaryStage));

        var emailAll = new MenuItem("Email All Spreadsheets");
        emailAll.setOnAction(e ->
            new EmailAllWindow(emailSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));

        var generateSingle = new MenuItem("Generate Single Spreadsheet");
        generateSingle.setOnAction(e ->
            new SingleGenerateWindow(spreadsheetSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));

        var emailSingle = new MenuItem("Email Single Spreadsheet");
        emailSingle.setOnAction(e ->
            new SingleEmailWindow(emailSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));

        var actionsMenu = new Menu("Actions",
            null,
            modifyParams, showRuns, generateAll, emailAll, generateSingle, emailSingle);

        var menuBar = new MenuBar(actionsMenu);

        var root = new BorderPane();
        root.setTop(menuBar);

        primaryStage.setTitle("Fee Earner Report");
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();
    }
}
