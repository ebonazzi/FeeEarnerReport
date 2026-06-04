package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.excel.WorkbookBuilder;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class SpreadsheetService {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WorksheetRepository worksheetRepo;
    private final ArchiveRepository archiveRepo;
    private final RunRepository runRepo;
    private final FeeEarnerRepository feeEarnerRepo;
    private final WorkbookBuilder workbookBuilder;

    public SpreadsheetService(WorksheetRepository worksheetRepo,
                              ArchiveRepository archiveRepo,
                              RunRepository runRepo,
                              FeeEarnerRepository feeEarnerRepo,
                              WorkbookBuilder workbookBuilder) {
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
            archiveRepo.insertFullTaskRows(runId, dayRun, fe.usrID(), fullTask);
            archiveRepo.insertLimitationRows(runId, dayRun, fe.usrID(), limitation);
            archiveRepo.insertAgedRows(runId, dayRun, fe.usrID(), aged);
            archiveRepo.insertDuplicateRows(runId, dayRun, fe.usrID(), duplicate);
            archiveRepo.insertHighVolumeRows(runId, dayRun, fe.usrID(), highVolume);

            byte[] stored = Files.readAllBytes(file);
            if (isNewRun) {
                runRepo.insertFeeEarnerRun(
                    new FeeEarnerRun(runId, dayRun, fe.usrID(), fe.feeEarner(),
                                     fe.usrEmail(), filename, stored, null));
            } else {
                runRepo.updateFeeEarnerRun(runId, fe.usrID(), filename, stored);
            }
        } finally {
            Files.delete(file);
        }
    }
}
