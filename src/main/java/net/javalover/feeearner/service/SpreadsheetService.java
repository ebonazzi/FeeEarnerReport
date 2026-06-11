package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.excel.WorkbookBuilder;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class SpreadsheetService {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DataSource ds;
    private final WorksheetRepository worksheetRepo;
    private final ArchiveRepository archiveRepo;
    private final RunRepository runRepo;
    private final FeeEarnerRepository feeEarnerRepo;
    private final WorkbookBuilder workbookBuilder;

    public SpreadsheetService(DataSource ds,
                              WorksheetRepository worksheetRepo,
                              ArchiveRepository archiveRepo,
                              RunRepository runRepo,
                              FeeEarnerRepository feeEarnerRepo,
                              WorkbookBuilder workbookBuilder) {
        this.ds = ds;
        this.worksheetRepo = worksheetRepo;
        this.archiveRepo = archiveRepo;
        this.runRepo = runRepo;
        this.feeEarnerRepo = feeEarnerRepo;
        this.workbookBuilder = workbookBuilder;
    }

    public void generateForFeeEarner(FeeEarner fe, int runId, LocalDate dayRun,
                                     AppConfig config, Consumer<ProgressEvent> onProgress)
            throws IOException {
        var intersect = feeEarnerRepo.getIntersectUserIds();
        doGenerate(fe, intersect, runId, dayRun, config, false);
        onProgress.accept(new ProgressEvent(1, 1, 0));
    }

    public void generateAll(List<FeeEarner> feeEarners, int runId, LocalDate dayRun,
                            AppConfig config, ProgressTracker tracker) {
        var intersect = feeEarnerRepo.getIntersectUserIds();
        var pool = Executors.newFixedThreadPool(config.threadPoolSize());
        var completion = new ExecutorCompletionService<Void>(pool);

        for (var fe : feeEarners) {
            completion.submit(() -> {
                try {
                    doGenerate(fe, intersect, runId, dayRun, config, true);
                    tracker.completed().incrementAndGet();
                } catch (Exception e) {
                    log.error("Failed to generate for usrID={} name='{}'",
                        fe.usrID(), fe.feeEarner(), e);
                    tracker.failed().incrementAndGet();
                    tracker.failures().add(new FailedEntry(fe.usrID(), fe.feeEarner(), e.getMessage()));
                }
                return null;
            });
        }

        int total = feeEarners.size();
        try {
            for (int i = 0; i < total; i++) {
                try {
                    completion.take().get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException ignored) {
                    // exceptions are caught inside the Callable and recorded in tracker
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Rebuilds one fee earner's spreadsheet from the archive tables of {@code runId} and
     * stores it as that fee earner's MOST RECENT spreadsheet (blob replaced, stored_at=now).
     * The archive tables are the read-only source and are not modified. Requires an existing
     * FeeEarnersRun row for the fee earner.
     */
    public void generateFromArchive(int usrID, int runId, AppConfig config) {
        var fullTask   = archiveRepo.readFullTask(runId, usrID);
        var limitation = archiveRepo.readLimitation(runId, usrID);
        var aged       = archiveRepo.readAged(runId, usrID);
        var duplicate  = archiveRepo.readDuplicate(runId, usrID);
        var highVolume = archiveRepo.readHighVolume(runId, usrID);

        byte[] xlsx;
        try {
            xlsx = workbookBuilder.build(fullTask, limitation, aged, duplicate, highVolume);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build workbook for usrID=" + usrID, e);
        }

        var recent = runRepo.getMostRecent(usrID)
            .orElseThrow(() -> new IllegalStateException(
                "No FeeEarnersRun row for usrID=" + usrID + "; cannot regenerate"));
        String filename = "fe_" + usrID + "_" + LocalDate.now().format(DATE_FMT)
                          + "_" + recent.runId() + ".xlsx";
        runRepo.updateMostRecentFeeEarnerBlob(usrID, filename, xlsx);
    }

    /**
     * Regenerates every fee earner present in {@code runId}'s archive, recording per-fee-earner
     * failures in the tracker and continuing the batch (one bad fee earner never aborts it).
     */
    public void generateAllFromArchive(int runId, AppConfig config, ProgressTracker tracker) {
        var ids = archiveRepo.getFeeEarnerIdsForRun(runId);
        tracker.total().set(ids.size());
        var pool = Executors.newFixedThreadPool(config.threadPoolSize());
        var completion = new ExecutorCompletionService<Void>(pool);

        for (var usrID : ids) {
            completion.submit(() -> {
                try {
                    generateFromArchive(usrID, runId, config);
                    tracker.completed().incrementAndGet();
                } catch (Exception e) {
                    log.error("Failed to regenerate from archive for usrID={} run={}", usrID, runId, e);
                    tracker.failed().incrementAndGet();
                    tracker.failures().add(new FailedEntry(usrID, String.valueOf(usrID), e.getMessage()));
                }
                return null;
            });
        }
        try {
            for (int i = 0; i < ids.size(); i++) {
                try {
                    completion.take().get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException ignored) {
                    // handled inside the Callable
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    private void doGenerate(FeeEarner fe, Set<Integer> intersectIds,
                            int runId, LocalDate dayRun, AppConfig config,
                            boolean isNewRun) throws IOException {
        // fn_VIC_Lead_Active_FeeEarners returns [Type]='Enquiry' (not 'Lead'); matter side returns 'Matter'
        boolean isLead = fe.type().equalsIgnoreCase("Enquiry");
        boolean isMatter = fe.type().equalsIgnoreCase("Matter");
        boolean inIntersect = intersectIds.contains(fe.usrID());

        List<FullTaskRow>   fullTask   = new ArrayList<>();
        List<LimitationRow> limitation = new ArrayList<>();
        List<AgedRow>       aged       = new ArrayList<>();
        List<DuplicateRow>  duplicate  = new ArrayList<>();
        List<HighVolumeRow> highVolume = new ArrayList<>();

        if (isLead || (isMatter && inIntersect)) {
            fullTask.addAll(worksheetRepo.getLeadFullTaskData(fe.usrID()));
            limitation.addAll(worksheetRepo.getLeadLimitation(fe.usrID()));
            aged.addAll(worksheetRepo.getLeadAged(fe.usrID()));
            duplicate.addAll(worksheetRepo.getLeadDuplicate(fe.usrID()));
            highVolume.addAll(worksheetRepo.getLeadHighVolume(fe.usrID()));
        }
        if (isMatter || (isLead && inIntersect)) {
            fullTask.addAll(worksheetRepo.getMatterFullTaskData(fe.usrID()));
            limitation.addAll(worksheetRepo.getMatterLimitation(fe.usrID()));
            aged.addAll(worksheetRepo.getMatterAged(fe.usrID()));
            duplicate.addAll(worksheetRepo.getMatterDuplicate(fe.usrID()));
            highVolume.addAll(worksheetRepo.getMatterHighVolume(fe.usrID()));
        }

        byte[] xlsx = workbookBuilder.build(fullTask, limitation, aged, duplicate, highVolume);

        String filename = "fe_" + fe.usrID() + "_" + dayRun.format(DATE_FMT) + "_" + runId + ".xlsx";
        Path file = Path.of(config.outputDir()).resolve(filename);
        Files.write(file, xlsx);
        try {
            byte[] stored = Files.readAllBytes(file);
            persist(fe, runId, dayRun, isNewRun, filename, stored,
                fullTask, limitation, aged, duplicate, highVolume);
        } finally {
            Files.delete(file);
        }
    }

    /**
     * Persists one fee earner's archive rows and stored spreadsheet in a single transaction
     * so they commit together — a mid-write failure rolls back everything, leaving no
     * partial archive and no mismatch between the archive and the stored blob.
     *
     * <p>Single-spreadsheet regeneration ({@code isNewRun=false}) reuses the existing
     * {@code run_id}, so it passes {@code replaceExisting=true} to clear prior archive rows
     * (within this transaction) before re-insert; a fresh bulk run passes {@code false}.
     */
    private void persist(FeeEarner fe, int runId, LocalDate dayRun, boolean isNewRun,
                         String filename, byte[] stored,
                         List<FullTaskRow> fullTask, List<LimitationRow> limitation,
                         List<AgedRow> aged, List<DuplicateRow> duplicate,
                         List<HighVolumeRow> highVolume) {
        try (Connection conn = ds.getConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                archiveRepo.writeForFeeEarner(conn, runId, dayRun, fe.usrID(), !isNewRun,
                    fullTask, limitation, aged, duplicate, highVolume);
                if (isNewRun) {
                    runRepo.insertFeeEarnerRun(conn,
                        new FeeEarnerRun(runId, dayRun, fe.usrID(), fe.feeEarner(),
                                         fe.usrEmail(), filename, stored, null));
                } else {
                    runRepo.updateFeeEarnerRun(conn, runId, fe.usrID(), filename, stored);
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                "Failed to persist run for usrID=" + fe.usrID() + " run=" + runId, e);
        }
    }
}
