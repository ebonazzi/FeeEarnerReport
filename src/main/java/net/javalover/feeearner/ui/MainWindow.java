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
    private final DeployService deploySvc;
    private final FeeEarnerRepository feeEarnerRepo;
    // Config is loaded at startup; changes via ParameterEditorWindow take effect on next app start.
    private final AppConfig config;

    public MainWindow(ParameterService paramSvc, RunRepository runRepo,
                      SpreadsheetService spreadsheetSvc, RunService runSvc,
                      EmailService emailSvc, DeployService deploySvc,
                      FeeEarnerRepository feeEarnerRepo, AppConfig config) {
        this.paramSvc       = paramSvc;
        this.runRepo        = runRepo;
        this.spreadsheetSvc = spreadsheetSvc;
        this.runSvc         = runSvc;
        this.emailSvc       = emailSvc;
        this.deploySvc      = deploySvc;
        this.feeEarnerRepo  = feeEarnerRepo;
        this.config         = config;
    }

    public void show(Stage primaryStage) {
        // ── Parameters ──
        var modifyParams = new MenuItem("Modify Parameters");
        modifyParams.setOnAction(e ->
            new ParameterEditorWindow(paramSvc).show(primaryStage));
        var parametersMenu = new Menu("Parameters", null, modifyParams);

        // ── Runs ──
        var showRuns = new MenuItem("Previous Runs");
        showRuns.setOnAction(e ->
            new PreviousRunsWindow(runRepo).show(primaryStage));
        var runsMenu = new Menu("Runs", null, showRuns);

        // ── Spreadsheet Generation ──
        var generateAll = new MenuItem("Generate All Spreadsheets");
        generateAll.setOnAction(e ->
            new GenerateAllWindow(spreadsheetSvc, runSvc, feeEarnerRepo, config)
                .show(primaryStage));
        var generateSingle = new MenuItem("Generate Single Spreadsheet");
        generateSingle.setOnAction(e ->
            new SingleGenerateWindow(spreadsheetSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));
        var generateAllPast = new MenuItem("Generate All Past Spreadsheets");
        generateAllPast.setOnAction(e ->
            new GenerateAllPastWindow(spreadsheetSvc, runRepo, config)
                .show(primaryStage));
        var generateSinglePast = new MenuItem("Generate Single Past Spreadsheets");
        generateSinglePast.setOnAction(e ->
            new GenerateSinglePastWindow(spreadsheetSvc, runRepo, config)
                .show(primaryStage));
        var generationMenu = new Menu("Spreadsheet Generation", null,
            generateAll, generateSingle, generateAllPast, generateSinglePast);

        // ── Email Send ──
        var emailAll = new MenuItem("Email All Spreadsheets");
        emailAll.setOnAction(e ->
            new EmailAllWindow(emailSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));
        var emailSingle = new MenuItem("Email Single Spreadsheet");
        emailSingle.setOnAction(e ->
            new SingleEmailWindow(emailSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));
        var emailMenu = new Menu("Email Send", null, emailAll, emailSingle);

        // ── Sharepoint Deployment ──
        var deployAll = new MenuItem("Deploy All Spreadsheets");
        deployAll.setOnAction(e ->
            new DeployAllWindow(deploySvc, runRepo, config).show(primaryStage));
        var deploySingle = new MenuItem("Deploy Single Spreadsheet");
        deploySingle.setOnAction(e ->
            new SingleDeployWindow(deploySvc, feeEarnerRepo, config).show(primaryStage));
        var testSharepoint = new MenuItem("Test Sharepoint");
        testSharepoint.setOnAction(e ->
            new TestSharePointWindow(deploySvc, config).show(primaryStage));
        var sharepointMenu = new Menu("Sharepoint Deployment", null, deployAll, deploySingle, testSharepoint);

        var menuBar = new MenuBar(parametersMenu, runsMenu, generationMenu,
            emailMenu, sharepointMenu);

        var root = new BorderPane();
        root.setTop(menuBar);

        primaryStage.setTitle("Fee Earner Report");
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();
    }
}
