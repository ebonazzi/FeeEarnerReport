package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.sharepoint.SharePointService;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class DeployServiceTest {

    private AppConfig config() {
        return AppConfig.from(List.of(
            new AppParam(1, "shrpnt_client_id",  "c", true),
            new AppParam(2, "shrpnt_tenant_id",  "t", true),
            new AppParam(3, "shrpnt_secret_id",  "s", true),
            new AppParam(4, "shrpnt_host",       "h.sharepoint.com", true),
            new AppParam(5, "shrpnt_site_name",  "root", true),
            new AppParam(6, "shrpnt_target_dir", "Folder", true)
        ));
    }

    private FeeEarnerRun run(int usrID, String name) {
        return new FeeEarnerRun(7, LocalDate.of(2026, 6, 5), usrID, name,
            "e@law.com", "fe.xlsx", new byte[]{1, 2, 3}, null);
    }

    @Test
    void sharePointFileNameFormat() {
        assertEquals("Joseph Tran_VIC_Task_Report_20260605.xlsx",
            DeployService.sharePointFileName("Joseph Tran", LocalDate.of(2026, 6, 5)));
    }

    @Test
    void testFilenameFormat() {
        var clock = Clock.fixed(Instant.parse("2026-07-09T14:32:01Z"), ZoneOffset.UTC);
        assertEquals("sharepoint_test_20260709_143201.txt", DeployService.testFilename(clock));
    }

    @Test
    void randomContentHasRequestedLength() {
        byte[] bytes = DeployService.randomContent(512, new Random(42));
        assertEquals(512, bytes.length);
    }

    @Test
    void randomContentOnlyUsesAlphanumericAscii() {
        byte[] bytes = DeployService.randomContent(200, new Random(1));
        for (byte b : bytes) {
            char c = (char) b;
            assertTrue(Character.isLetterOrDigit(c) && c < 128, "unexpected char: " + c);
        }
    }

    /** Records each upload's filename; resolves token/site/drive once. */
    private SharePointService recordingSharePoint(CopyOnWriteArrayList<String> uploads, int[] tokenCalls) {
        return new SharePointService() {
            @Override public String acquireToken(AppConfig c) { tokenCalls[0]++; return "tok"; }
            @Override public String resolveSiteId(String t, AppConfig c) { return "site"; }
            @Override public String resolveDriveId(String t, String s) { return "drive"; }
            @Override public void upload(String t, String d, String dir, String file, byte[] b) {
                uploads.add(file);
            }
        };
    }

    @Test
    void deployAllUploadsEachWithSharePointNameAndResolvesTokenOnce() {
        var uploads = new CopyOnWriteArrayList<String>();
        var tokenCalls = new int[]{0};
        var svc = new DeployService(recordingSharePoint(uploads, tokenCalls), new RunRepository(null));
        var tracker = new ProgressTracker(0);
        svc.deployAll(List.of(run(1, "Joseph Tran"), run(2, "Mary Lee")), config(), tracker);

        assertEquals(2, tracker.completed().get());
        assertEquals(1, tokenCalls[0], "token/site/drive resolved once per batch");
        assertTrue(uploads.contains("Joseph Tran_VIC_Task_Report_20260605.xlsx"));
        assertTrue(uploads.contains("Mary Lee_VIC_Task_Report_20260605.xlsx"));
    }

    @Test
    void deployAllTracksFailures() {
        var sp = new SharePointService() {
            @Override public String acquireToken(AppConfig c) { return "tok"; }
            @Override public String resolveSiteId(String t, AppConfig c) { return "site"; }
            @Override public String resolveDriveId(String t, String s) { return "drive"; }
            @Override public void upload(String t, String d, String dir, String file, byte[] b) {
                throw new RuntimeException("graph down");
            }
        };
        var svc = new DeployService(sp, new RunRepository(null));
        var tracker = new ProgressTracker(0);
        svc.deployAll(List.of(run(1, "Joseph Tran")), config(), tracker);
        assertEquals(1, tracker.failed().get());
        assertEquals(1, tracker.failures().size());
    }

    @Test
    void deployForFeeEarnerUsesMostRecentRun() {
        var uploads = new CopyOnWriteArrayList<String>();
        var sp = recordingSharePoint(uploads, new int[]{0});
        var runRepo = new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int usrID) {
                return Optional.of(run(usrID, "Joseph Tran"));
            }
        };
        var svc = new DeployService(sp, runRepo);
        svc.deployForFeeEarner(35189, config());
        assertEquals(1, uploads.size());
        assertEquals("Joseph Tran_VIC_Task_Report_20260605.xlsx", uploads.get(0));
    }

    @Test
    void deployForFeeEarnerThrowsWhenNoRun() {
        var sp = recordingSharePoint(new CopyOnWriteArrayList<>(), new int[]{0});
        var runRepo = new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int usrID) { return Optional.empty(); }
        };
        var svc = new DeployService(sp, runRepo);
        assertThrows(IllegalStateException.class, () -> svc.deployForFeeEarner(1, config()));
    }

    @Test
    void testConnectionReturnsFilenameOnSuccess() {
        var deleted = new CopyOnWriteArrayList<String>();
        var sp = new SharePointService() {
            @Override public String acquireToken(AppConfig c) { return "tok"; }
            @Override public String resolveSiteId(String t, AppConfig c) { return "site"; }
            @Override public String resolveDriveId(String t, String s) { return "drive"; }
            @Override public void upload(String t, String d, String dir, String file, byte[] b) { }
            @Override public void delete(String t, String d, String dir, String file) { deleted.add(file); }
        };
        var svc = new DeployService(sp, new RunRepository(null));
        var result = svc.testConnection(config());

        assertNotNull(result.filename());
        assertTrue(result.filename().startsWith("sharepoint_test_"));
        assertNull(result.cleanupWarning());
        assertEquals(List.of(result.filename()), deleted);
    }

    @Test
    void testConnectionReportsCleanupWarningWhenDeleteFails() {
        var sp = new SharePointService() {
            @Override public String acquireToken(AppConfig c) { return "tok"; }
            @Override public String resolveSiteId(String t, AppConfig c) { return "site"; }
            @Override public String resolveDriveId(String t, String s) { return "drive"; }
            @Override public void upload(String t, String d, String dir, String file, byte[] b) { }
            @Override public void delete(String t, String d, String dir, String file) {
                throw new RuntimeException("cleanup boom");
            }
        };
        var svc = new DeployService(sp, new RunRepository(null));
        var result = svc.testConnection(config());

        assertNotNull(result.filename());
        assertEquals("cleanup boom", result.cleanupWarning());
    }

    @Test
    void testConnectionPropagatesUploadFailure() {
        var sp = new SharePointService() {
            @Override public String acquireToken(AppConfig c) { return "tok"; }
            @Override public String resolveSiteId(String t, AppConfig c) { return "site"; }
            @Override public String resolveDriveId(String t, String s) { return "drive"; }
            @Override public void upload(String t, String d, String dir, String file, byte[] b) {
                throw new RuntimeException("graph down");
            }
        };
        var svc = new DeployService(sp, new RunRepository(null));
        var ex = assertThrows(RuntimeException.class, () -> svc.testConnection(config()));
        assertEquals("graph down", ex.getMessage());
    }
}
