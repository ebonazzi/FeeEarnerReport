package net.javalover.feeearner.service;

import net.javalover.feeearner.FakeJdbc;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.excel.WorkbookBuilder;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class SpreadsheetServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 5);
    private static final FeeEarner LEAD_FE =
        new FeeEarner(100, "Alice Smith", "alice@law.com", true, "Enquiry");
    private static final FeeEarner MATTER_FE =
        new FeeEarner(200, "Bob Jones", "bob@law.com", true, "Matter");
    private static final FeeEarner INTERSECT_FE =
        new FeeEarner(300, "Carol Lee", "carol@law.com", true, "Enquiry");

    /** A working DataSource whose connection records but no-ops commit/rollback/close. */
    private DataSource okDs() { return new FakeJdbc().dataSource(); }

    private AppConfig configFor(Path dir) {
        return AppConfig.from(List.of(
            new AppParam(1, "output_dir",       dir.toString(),  true),
            new AppParam(2, "thread_pool_size", "2",             true),
            new AppParam(3, "log_dir",          dir.toString(),  true),
            new AppParam(4, "email_sender",     "x@law.com",     true),
            new AppParam(5, "email_subject",    "Report",        true),
            new AppParam(6, "email_body",       "See attached",  true),
            new AppParam(7, "smtp_server",      "localhost",     true),
            new AppParam(8, "smtp_port",        "25",            true)
        ));
    }

    /** Lead-only FE: only Lead worksheet functions must be called */
    @Test
    void leadFunctionsCalledForLeadFeeEarner(@TempDir Path dir) throws IOException {
        var leadCalled = new AtomicBoolean(false);
        var matterCalled = new AtomicBoolean(false);
        var worksheetRepo = new WorksheetRepository(null) {
            @Override public List<FullTaskRow> getLeadFullTaskData(int id) {
                leadCalled.set(true); return List.of();
            }
            @Override public List<LimitationRow> getLeadLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getLeadAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getLeadDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getLeadHighVolume(int id) { return List.of(); }
            @Override public List<FullTaskRow> getMatterFullTaskData(int id) {
                matterCalled.set(true); return List.of();
            }
            @Override public List<LimitationRow> getMatterLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getMatterAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getMatterDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getMatterHighVolume(int id) { return List.of(); }
        };
        var service = new SpreadsheetService(okDs(), worksheetRepo, stubArchiveRepo(), stubRunRepo(),
            stubFeeEarnerRepo(Set.of()), new WorkbookBuilder());
        service.generateForFeeEarner(LEAD_FE, 1, DAY, configFor(dir), e -> {});
        assertTrue(leadCalled.get(), "Lead functions must be called for Lead fee earner");
        assertFalse(matterCalled.get(), "Matter functions must NOT be called for non-intersect Lead");
    }

    /** Intersect FE (Lead type, but in intersect set): both Lead and Matter must be called */
    @Test
    void intersectFeeEarnerCallsBothFunctions(@TempDir Path dir) throws IOException {
        var leadCalled = new AtomicBoolean(false);
        var matterCalled = new AtomicBoolean(false);
        var worksheetRepo = new WorksheetRepository(null) {
            @Override public List<FullTaskRow> getLeadFullTaskData(int id) {
                leadCalled.set(true); return List.of();
            }
            @Override public List<LimitationRow> getLeadLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getLeadAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getLeadDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getLeadHighVolume(int id) { return List.of(); }
            @Override public List<FullTaskRow> getMatterFullTaskData(int id) {
                matterCalled.set(true); return List.of();
            }
            @Override public List<LimitationRow> getMatterLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getMatterAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getMatterDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getMatterHighVolume(int id) { return List.of(); }
        };
        var feeEarnerRepo = new FeeEarnerRepository(null) {
            @Override public Set<Integer> getIntersectUserIds() { return Set.of(300); }
        };
        var service = new SpreadsheetService(okDs(), worksheetRepo, stubArchiveRepo(), stubRunRepo(),
            feeEarnerRepo, new WorkbookBuilder());
        service.generateForFeeEarner(INTERSECT_FE, 1, DAY, configFor(dir), e -> {});
        assertTrue(leadCalled.get(), "Lead functions must be called for intersect fee earner");
        assertTrue(matterCalled.get(), "Matter functions must also be called for intersect fee earner");
    }

    /** Temp xlsx file must be deleted after generate */
    @Test
    void generateForFeeEarnerDeletesTempFile(@TempDir Path dir) throws IOException {
        var service = new SpreadsheetService(okDs(),
            stubWorksheetRepo(), stubArchiveRepo(), stubRunRepo(),
            stubFeeEarnerRepo(Set.of()), new WorkbookBuilder());
        service.generateForFeeEarner(LEAD_FE, 1, DAY, configFor(dir), e -> {});
        var remaining = Files.list(dir).toList();
        assertTrue(remaining.isEmpty(), "Temp xlsx file must be deleted after insertFeeEarnerRun");
    }

    /** generateAll must track failures per-FE without aborting the batch */
    @Test
    void generateAllTracksFailures(@TempDir Path dir) {
        var failingRepo = new WorksheetRepository(null) {
            @Override public List<FullTaskRow> getLeadFullTaskData(int id) { throw new RuntimeException("db error"); }
            @Override public List<LimitationRow> getLeadLimitation(int id) { throw new RuntimeException("db error"); }
            @Override public List<AgedRow> getLeadAged(int id) { throw new RuntimeException("db error"); }
            @Override public List<DuplicateRow> getLeadDuplicate(int id) { throw new RuntimeException("db error"); }
            @Override public List<HighVolumeRow> getLeadHighVolume(int id) { throw new RuntimeException("db error"); }
            @Override public List<FullTaskRow> getMatterFullTaskData(int id) { throw new RuntimeException("db error"); }
            @Override public List<LimitationRow> getMatterLimitation(int id) { throw new RuntimeException("db error"); }
            @Override public List<AgedRow> getMatterAged(int id) { throw new RuntimeException("db error"); }
            @Override public List<DuplicateRow> getMatterDuplicate(int id) { throw new RuntimeException("db error"); }
            @Override public List<HighVolumeRow> getMatterHighVolume(int id) { throw new RuntimeException("db error"); }
        };
        var service = new SpreadsheetService(okDs(), failingRepo, stubArchiveRepo(), stubRunRepo(),
            stubFeeEarnerRepo(Set.of()), new WorkbookBuilder());
        var tracker = new ProgressTracker(2);
        service.generateAll(List.of(LEAD_FE, MATTER_FE), 1, DAY, configFor(dir), tracker);
        assertEquals(2, tracker.failed().get());
        assertEquals(2, tracker.failures().size());
    }

    /** Single-generate reuses the run_id, so it must replace existing archive rows */
    @Test
    void singleGenerateReplacesExistingArchive(@TempDir Path dir) throws IOException {
        var replaceFlag = new ArrayList<Boolean>();
        var service = new SpreadsheetService(okDs(), stubWorksheetRepo(),
            recordingArchive(replaceFlag), stubRunRepo(), stubFeeEarnerRepo(Set.of()), new WorkbookBuilder());
        service.generateForFeeEarner(LEAD_FE, 1, DAY, configFor(dir), e -> {});
        assertEquals(List.of(true), replaceFlag, "Single-generate must replace existing archive rows");
    }

    /** Bulk generate gets a fresh run_id, so it must NOT replace (delete) archive rows */
    @Test
    void bulkGenerateDoesNotReplaceArchive(@TempDir Path dir) {
        var replaceFlag = new ArrayList<Boolean>();
        var service = new SpreadsheetService(okDs(), stubWorksheetRepo(),
            recordingArchive(replaceFlag), stubRunRepo(), stubFeeEarnerRepo(Set.of()), new WorkbookBuilder());
        var tracker = new ProgressTracker(1);
        service.generateAll(List.of(LEAD_FE), 1, DAY, configFor(dir), tracker);
        assertEquals(1, tracker.completed().get());
        assertEquals(List.of(false), replaceFlag, "Bulk generate (fresh run_id) must not replace archive rows");
    }

    /** A successful generate commits the archive + blob write exactly once and restores autoCommit */
    @Test
    void successCommitsTheTransaction(@TempDir Path dir) throws IOException {
        var fake = new FakeJdbc();
        var service = new SpreadsheetService(fake.dataSource(), stubWorksheetRepo(), stubArchiveRepo(),
            stubRunRepo(), stubFeeEarnerRepo(Set.of()), new WorkbookBuilder());
        service.generateForFeeEarner(LEAD_FE, 1, DAY, configFor(dir), e -> {});
        assertTrue(fake.committed, "successful generate must commit");
        assertFalse(fake.rolledBack, "successful generate must not roll back");
        assertEquals(1, fake.commitCount, "commit exactly once");
        assertTrue(fake.autoCommit, "autoCommit must be restored before the connection is returned to the pool");
    }

    /** A blob-write failure must roll back the archive write too — the unit of work is atomic */
    @Test
    void blobWriteFailureRollsBackArchiveWrite(@TempDir Path dir) {
        var fake = new FakeJdbc();
        var archiveWritten = new AtomicBoolean(false);
        var archive = new ArchiveRepository() {
            @Override public void writeForFeeEarner(Connection c, int r, LocalDate d, int u, boolean rep,
                    List<FullTaskRow> ft, List<LimitationRow> lim, List<AgedRow> ag,
                    List<DuplicateRow> dup, List<HighVolumeRow> hv) { archiveWritten.set(true); }
        };
        var run = new RunRepository(null) {
            @Override public void updateFeeEarnerRun(Connection c, int runId, int usrID, String f, byte[] x) {
                throw new RuntimeException("blob write boom");
            }
        };
        var service = new SpreadsheetService(fake.dataSource(), stubWorksheetRepo(), archive, run,
            stubFeeEarnerRepo(Set.of()), new WorkbookBuilder());
        assertThrows(RuntimeException.class, () ->
            service.generateForFeeEarner(LEAD_FE, 1, DAY, configFor(dir), e -> {}));
        assertTrue(archiveWritten.get(), "archive write should run before the failing blob write");
        assertTrue(fake.rolledBack, "a blob-write failure must roll back the whole transaction");
        assertFalse(fake.committed, "must not commit when the blob write fails");
        assertTrue(fake.autoCommit, "autoCommit must be restored even on the failure path");
    }

    // ── helper stubs ──────────────────────────────────────────────────────────

    private WorksheetRepository stubWorksheetRepo() {
        return new WorksheetRepository(null) {
            @Override public List<FullTaskRow> getLeadFullTaskData(int id) { return List.of(); }
            @Override public List<LimitationRow> getLeadLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getLeadAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getLeadDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getLeadHighVolume(int id) { return List.of(); }
            @Override public List<FullTaskRow> getMatterFullTaskData(int id) { return List.of(); }
            @Override public List<LimitationRow> getMatterLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getMatterAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getMatterDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getMatterHighVolume(int id) { return List.of(); }
        };
    }

    private ArchiveRepository stubArchiveRepo() {
        return new ArchiveRepository() {
            @Override public void writeForFeeEarner(Connection c, int r, LocalDate d, int u, boolean replace,
                    List<FullTaskRow> ft, List<LimitationRow> lim, List<AgedRow> ag,
                    List<DuplicateRow> dup, List<HighVolumeRow> hv) {}
        };
    }

    /** Archive stub that records the replaceExisting flag of each call. */
    private ArchiveRepository recordingArchive(List<Boolean> replaceFlag) {
        return new ArchiveRepository() {
            @Override public void writeForFeeEarner(Connection c, int r, LocalDate d, int u, boolean replace,
                    List<FullTaskRow> ft, List<LimitationRow> lim, List<AgedRow> ag,
                    List<DuplicateRow> dup, List<HighVolumeRow> hv) { replaceFlag.add(replace); }
        };
    }

    private RunRepository stubRunRepo() {
        return new RunRepository(null) {
            @Override public int createRun() { return 0; }
            @Override public void closeRun(int id) {}
            @Override public void insertFeeEarnerRun(Connection c, FeeEarnerRun run) {}
            @Override public void updateFeeEarnerRun(Connection c, int runId, int usrID, String filename, byte[] xlsx) {}
        };
    }

    private FeeEarnerRepository stubFeeEarnerRepo(Set<Integer> intersect) {
        return new FeeEarnerRepository(null) {
            @Override public Set<Integer> getIntersectUserIds() { return intersect; }
        };
    }
}
