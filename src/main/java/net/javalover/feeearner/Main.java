package net.javalover.feeearner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.config.CredentialLoader;
import net.javalover.feeearner.excel.WorkbookBuilder;
import net.javalover.feeearner.logging.LoggingInitialiser;
import net.javalover.feeearner.model.FailedEntry;
import net.javalover.feeearner.model.FeeEarner;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.repository.ArchiveRepository;
import net.javalover.feeearner.repository.FeeEarnerRepository;
import net.javalover.feeearner.repository.ParamRepository;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.repository.WorksheetRepository;
import net.javalover.feeearner.service.RunService;
import net.javalover.feeearner.service.SpreadsheetService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: fee-earner-report <credentials-file>");
            System.exit(1);
        }

        HikariDataSource ds = null;
        try {
            var creds = CredentialLoader.load(args[0]);
            var hikari = new HikariConfig();
            hikari.setJdbcUrl(creds.jdbcUrl());
            hikari.setUsername(creds.username());
            hikari.setPassword(creds.password());
            ds = new HikariDataSource(hikari);

            var paramRepo = new ParamRepository(ds);
            var config = AppConfig.from(paramRepo.loadAll());
            LoggingInitialiser.init(config.logDir(), config.debugLevel());

            var feeEarnerRepo = new FeeEarnerRepository(ds);
            var runRepo = new RunRepository(ds);
            var worksheetRepo = new WorksheetRepository(ds);
            var archiveRepo = new ArchiveRepository(ds);

            var spreadsheetSvc = new SpreadsheetService(
                worksheetRepo, archiveRepo, runRepo, feeEarnerRepo, new WorkbookBuilder());
            var runSvc = new RunService(runRepo);

            var leadFEs = feeEarnerRepo.getLeadFeeEarners();
            var matterFEs = feeEarnerRepo.getMatterFeeEarners();
            var feeEarners = merge(leadFEs, matterFEs);

            int runId = runSvc.startRun();
            var tracker = new ProgressTracker(feeEarners.size());

            spreadsheetSvc.generateAll(feeEarners, runId, LocalDate.now(), config, tracker);
            runSvc.finishRun(runId);

            System.out.printf("Done. Completed: %d  Failed: %d%n",
                tracker.completed().get(), tracker.failed().get());
            for (FailedEntry f : tracker.failures()) {
                System.err.printf("  FAILED usrID=%d name='%s': %s%n",
                    f.usrID(), f.feeEarner(), f.errorMessage());
            }
            System.exit(tracker.failed().get() > 0 ? 2 : 0);

        } catch (Exception e) {
            System.err.println("Bootstrap failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        } finally {
            if (ds != null) ds.close();
        }
    }

    // Keep lead entry for intersect usrIDs (they appear in both lists)
    private static List<FeeEarner> merge(List<FeeEarner> lead, List<FeeEarner> matter) {
        var result = new ArrayList<>(lead);
        var leadIds = lead.stream().map(FeeEarner::usrID).collect(Collectors.toSet());
        matter.stream()
            .filter(fe -> !leadIds.contains(fe.usrID()))
            .forEach(result::add);
        return result;
    }
}
