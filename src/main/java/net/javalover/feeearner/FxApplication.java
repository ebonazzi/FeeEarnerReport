package net.javalover.feeearner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.config.CredentialLoader;
import net.javalover.feeearner.email.MailSender;
import net.javalover.feeearner.excel.WorkbookBuilder;
import net.javalover.feeearner.logging.LoggingInitialiser;
import net.javalover.feeearner.repository.*;
import net.javalover.feeearner.service.*;
import net.javalover.feeearner.ui.MainWindow;
import java.util.Objects;

public class FxApplication extends Application {

    private HikariDataSource ds;

    @Override
    public void start(Stage primaryStage) {
        var rawArgs = getParameters().getRaw();
        if (rawArgs.isEmpty()) {
            showError("Usage: FxApplication <credential-file>");
            Platform.exit();
            return;
        }

        try {
            var creds = CredentialLoader.load(rawArgs.get(0));

            var hikariCfg = new HikariConfig();
            hikariCfg.setJdbcUrl(creds.jdbcUrl());
            hikariCfg.setUsername(creds.username());
            hikariCfg.setPassword(creds.password());
            hikariCfg.setMaximumPoolSize(10);
            ds = new HikariDataSource(hikariCfg);

            var paramRepo      = new ParamRepository(ds);
            var config         = AppConfig.from(paramRepo.loadAll());

            LoggingInitialiser.init(config.logDir(), config.debugLevel());

            var feeEarnerRepo  = new FeeEarnerRepository(ds);
            var worksheetRepo  = new WorksheetRepository(ds);
            var archiveRepo    = new ArchiveRepository(ds);
            var runRepo        = new RunRepository(ds);

            var runSvc         = new RunService(runRepo);
            var spreadsheetSvc = new SpreadsheetService(
                worksheetRepo, archiveRepo, runRepo, feeEarnerRepo, new WorkbookBuilder());
            var mailSender     = new MailSender(config);
            var emailSvc       = new EmailService(mailSender, runRepo);
            var paramSvc       = new ParameterService(paramRepo);

            new MainWindow(paramSvc, runRepo, spreadsheetSvc, runSvc,
                           emailSvc, feeEarnerRepo, config)
                .show(primaryStage);

        } catch (Exception e) {
            var cause = e.getCause() != null ? "\n\nCause: " + e.getCause().getMessage() : "";
            showError("Bootstrap failed: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()) + cause);
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        if (ds != null) ds.close();
    }

    private void showError(String message) {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
