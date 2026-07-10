package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FailedEntry;
import net.javalover.feeearner.model.FeeEarnerRun;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.sharepoint.SharePointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * Uploads stored spreadsheets to SharePoint. Mirrors {@link EmailService}: a per-fee-earner
 * loop that records failures in a {@link ProgressTracker} and never aborts the batch. The
 * SharePoint token, site id and drive id are resolved once per batch and reused.
 */
public class DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TEST_FILENAME_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String RANDOM_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SharePointService sharePoint;
    private final RunRepository runRepo;

    public DeployService(SharePointService sharePoint, RunRepository runRepo) {
        this.sharePoint = sharePoint;
        this.runRepo = runRepo;
    }

    /** SharePoint upload name, e.g. {@code Jaz Goddard_VIC_Task_Report_20260710.xlsx}. */
    public static String sharePointFileName(String feeEarnerName, LocalDate dayRun) {
        return feeEarnerName + "_VIC_Task_Report_" + dayRun.format(DATE_FMT) + ".xlsx";
    }

    static String testFilename(Clock clock) {
        return "sharepoint_test_" + LocalDateTime.now(clock).format(TEST_FILENAME_FMT) + ".txt";
    }

    static byte[] randomContent(int length, Random random) {
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM_CHARS.charAt(random.nextInt(RANDOM_CHARS.length())));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
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

    public SharePointTestResult testConnection(AppConfig config) {
        String filename = testFilename(Clock.systemDefaultZone());
        byte[] content  = randomContent(512, new SecureRandom());

        String token   = sharePoint.acquireToken(config);
        String siteId  = sharePoint.resolveSiteId(token, config);
        String driveId = sharePoint.resolveDriveId(token, siteId);
        sharePoint.upload(token, driveId, config.sharePointTargetDir(), filename, content);

        try {
            sharePoint.delete(token, driveId, config.sharePointTargetDir(), filename);
            return new SharePointTestResult(filename, null);
        } catch (Exception e) {
            log.warn("Test file '{}' uploaded but cleanup delete failed", filename, e);
            return new SharePointTestResult(filename, e.getMessage());
        }
    }

    private void uploadOne(String token, String driveId, AppConfig config, FeeEarnerRun run) {
        if (run.excelSpreadsheet() == null) {
            throw new IllegalStateException(
                "No stored spreadsheet for usrID=" + run.usrID());
        }
        String filename = sharePointFileName(run.feeEarner(), run.dayRun());
        sharePoint.upload(token, driveId, config.sharePointTargetDir(),
            filename, run.excelSpreadsheet());
    }
}
