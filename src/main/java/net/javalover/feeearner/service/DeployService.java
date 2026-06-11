package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FailedEntry;
import net.javalover.feeearner.model.FeeEarnerRun;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.sharepoint.SharePointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Uploads stored spreadsheets to SharePoint. Mirrors {@link EmailService}: a per-fee-earner
 * loop that records failures in a {@link ProgressTracker} and never aborts the batch. The
 * SharePoint token, site id and drive id are resolved once per batch and reused.
 */
public class DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);

    private final SharePointService sharePoint;
    private final RunRepository runRepo;

    public DeployService(SharePointService sharePoint, RunRepository runRepo) {
        this.sharePoint = sharePoint;
        this.runRepo = runRepo;
    }

    /** SharePoint upload name, e.g. {@code Fee Earner_35189_Joseph Tran_VIC_task_report.xlsx}. */
    public static String sharePointFileName(int usrID, String feeEarnerName) {
        return "Fee Earner_" + usrID + "_" + feeEarnerName + "_VIC_task_report.xlsx";
    }

    public void deployAll(List<FeeEarnerRun> runs, AppConfig config, ProgressTracker tracker) {
        tracker.total().set(runs.size());
        String token   = sharePoint.acquireToken(config);
        String siteId  = sharePoint.resolveSiteId(token, config);
        String driveId = sharePoint.resolveDriveId(token, siteId);

        for (var run : runs) {
            try {
                uploadOne(token, driveId, config, run);
                tracker.completed().incrementAndGet();
            } catch (Exception e) {
                log.error("Failed to deploy to SharePoint for usrID={}", run.usrID(), e);
                tracker.failed().incrementAndGet();
                tracker.failures().add(
                    new FailedEntry(run.usrID(), run.feeEarner(), e.getMessage()));
            }
        }
    }

    public void deployForFeeEarner(int usrID, AppConfig config) {
        var run = runRepo.getMostRecent(usrID)
            .orElseThrow(() -> new IllegalStateException(
                "No FeeEarnerRun found for usrID=" + usrID));
        String token   = sharePoint.acquireToken(config);
        String siteId  = sharePoint.resolveSiteId(token, config);
        String driveId = sharePoint.resolveDriveId(token, siteId);
        uploadOne(token, driveId, config, run);
    }

    private void uploadOne(String token, String driveId, AppConfig config, FeeEarnerRun run) {
        if (run.excelSpreadsheet() == null) {
            throw new IllegalStateException(
                "No stored spreadsheet for usrID=" + run.usrID());
        }
        String filename = sharePointFileName(run.usrID(), run.feeEarner());
        sharePoint.upload(token, driveId, config.sharePointTargetDir(),
            filename, run.excelSpreadsheet());
    }
}
