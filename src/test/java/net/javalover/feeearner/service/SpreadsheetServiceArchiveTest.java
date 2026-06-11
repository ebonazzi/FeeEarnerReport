package net.javalover.feeearner.service;

import net.javalover.feeearner.FakeJdbc;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.excel.WorkbookBuilder;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class SpreadsheetServiceArchiveTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 5);

    private AppConfig config() {
        return AppConfig.from(List.of(
            new AppParam(1, "output_dir",       "/tmp", true),
            new AppParam(2, "thread_pool_size", "2",    true),
            new AppParam(3, "log_dir",          "/tmp", true)
        ));
    }

    private FullTaskRow fullTask() {
        return new FullTaskRow("Matter", DAY, "M-1", "Matter", "Dept", "PC", "Off",
            "VIC", "Joseph Tran", "LA", "Sup", "Desc", "TT", "Notes", "Owner",
            LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 30, 17, 0));
    }

    /** Stub repos shared by tests. `stored` records each blob update. */
    private RunRepository runRepoRecording(CopyOnWriteArrayList<String> stored, int mostRecentRunId) {
        return new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int usrID) {
                return Optional.of(new FeeEarnerRun(mostRecentRunId, DAY, usrID,
                    "Joseph Tran", "x@law.com", "old.xlsx", new byte[]{0}, null));
            }
            @Override public void updateMostRecentFeeEarnerBlob(int usrID, String filename, byte[] xlsx) {
                stored.add(usrID + ":" + filename + ":" + xlsx.length);
            }
        };
    }

    private ArchiveRepository archiveWithRows(List<Integer> ids) {
        return new ArchiveRepository() {
            @Override public List<Integer> getFeeEarnerIdsForRun(int runId) { return ids; }
            @Override public List<FullTaskRow> readFullTask(int r, int u) { return List.of(fullTask()); }
            @Override public List<LimitationRow> readLimitation(int r, int u) { return List.of(); }
            @Override public List<AgedRow> readAged(int r, int u) { return List.of(); }
            @Override public List<DuplicateRow> readDuplicate(int r, int u) { return List.of(); }
            @Override public List<HighVolumeRow> readHighVolume(int r, int u) { return List.of(); }
        };
    }

    private SpreadsheetService service(ArchiveRepository archive, RunRepository runRepo) {
        return new SpreadsheetService(new FakeJdbc().dataSource(),
            new WorksheetRepository(null), archive, runRepo,
            new FeeEarnerRepository(null), new WorkbookBuilder());
    }

    @Test
    void generateFromArchiveStoresIntoMostRecentRow() {
        var stored = new CopyOnWriteArrayList<String>();
        var svc = service(archiveWithRows(List.of(100)), runRepoRecording(stored, 7));
        svc.generateFromArchive(100, 3, config());     // source run = 3, most-recent run = 7
        assertEquals(1, stored.size());
        var entry = stored.get(0);                     // "usrID:filename:blobLen"
        assertTrue(entry.startsWith("100:fe_100_"), "uses standard DB filename with usrID");
        assertTrue(entry.contains("_7.xlsx:"),
            "filename uses the most-recent runId (7), not the source run (3)");
    }

    @Test
    void generateAllFromArchiveProcessesEveryFeeEarner() {
        var stored = new CopyOnWriteArrayList<String>();
        var svc = service(archiveWithRows(List.of(100, 200, 300)), runRepoRecording(stored, 7));
        var tracker = new ProgressTracker(0);
        svc.generateAllFromArchive(3, config(), tracker);
        assertEquals(3, tracker.completed().get());
        assertEquals(3, tracker.total().get());
        assertEquals(3, stored.size());
    }

    /** A fee earner with no most-recent row is recorded as a failure, batch continues. */
    @Test
    void generateAllFromArchiveTracksFailures() {
        var stored = new CopyOnWriteArrayList<String>();
        var runRepo = new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int usrID) {
                return usrID == 200 ? Optional.empty()
                    : Optional.of(new FeeEarnerRun(7, DAY, usrID, "N", "e", "o.xlsx", new byte[]{0}, null));
            }
            @Override public void updateMostRecentFeeEarnerBlob(int usrID, String f, byte[] x) {
                stored.add(String.valueOf(usrID));
            }
        };
        var svc = service(archiveWithRows(List.of(100, 200, 300)), runRepo);
        var tracker = new ProgressTracker(0);
        svc.generateAllFromArchive(3, config(), tracker);
        assertEquals(2, tracker.completed().get());
        assertEquals(1, tracker.failed().get());
        assertEquals(1, tracker.failures().size());
    }
}
