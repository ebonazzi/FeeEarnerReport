# Test Sharepoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Test Sharepoint" GUI menu item that uploads a random test file to the configured SharePoint location, reports success (with cleanup) or a full stack trace, so SharePoint credentials/config can be validated independently of a real deploy.

**Architecture:** `SharePointService` gains a `delete` network method (mirrors `upload`'s simple-PUT path). `DeployService` gains a `testConnection(AppConfig)` orchestration method that uploads a small random file, attempts to delete it, and returns a `SharePointTestResult` record — propagating upload failures, swallowing cleanup failures into a warning field. A new modal `TestSharePointWindow` (JavaFX) displays the six SharePoint params in full plaintext, has a "Test" button that calls `testConnection` synchronously on the FX thread and renders either the success message or a full stack trace, and a "Dismiss" button. `MainWindow` gets one new `MenuItem` in the existing "Sharepoint Deployment" menu.

**Tech Stack:** Java 25, JavaFX (`provided` scope, Liberica Full JDK required to run), JUnit 5, existing `SharePointService`/`DeployService`/`AppConfig` classes.

## Global Constraints

- Follow existing repo conventions exactly (see `CLAUDE.md`): UI button handlers call a single service method directly, no business logic in controllers.
- `SharePointService`'s network methods are `public` and non-final so they can be overridden in unit tests (existing convention) — the new `delete` method must follow this.
- No new `report_param` DB rows — reuse the six existing `AppConfig.sharePoint*()` accessors.
- Secret is displayed in full plaintext in the UI (per approved spec) — do not mask it.
- Cleanup-delete failure after a successful upload must still report overall **success**, with a secondary warning note (per approved spec) — never fail the whole test on a cleanup-only error.
- No `ProgressTracker`/background-thread machinery for this feature — it's a single synchronous operation, following `SingleDeployWindow`'s direct-call-on-FX-thread convention, not `DeployAllWindow`'s batch/polling convention.

---

### Task 1: `SharePointService.delete`

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/sharepoint/SharePointService.java`
- Test: `src/test/java/net/javalover/feeearner/sharepoint/SharePointServiceTest.java`

**Interfaces:**
- Consumes: nothing new — uses the existing private `sendString(HttpRequest, String)` and `graphPath(String, String)` helpers already in this file.
- Produces: `public void delete(String token, String driveId, String targetDir, String filename)` and `static String deleteUrl(String driveId, String targetDir, String filename)`, both consumed by Task 2's `DeployService.testConnection`.

- [ ] **Step 1: Write the failing test for the pure URL helper**

Add to `SharePointServiceTest.java`, after `createSessionUrlEndsWithCreateUploadSession`:

```java
    @Test
    void deleteUrlOmitsContentSuffix() {
        var url = SharePointService.deleteUrl("drv1", "Folder", "f.xlsx");
        assertEquals("https://graph.microsoft.com/v1.0/drives/drv1/root:/Folder/f.xlsx", url);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SharePointServiceTest#deleteUrlOmitsContentSuffix`
Expected: FAIL — compile error, `deleteUrl` does not exist on `SharePointService`.

- [ ] **Step 3: Implement `deleteUrl` and `delete`**

In `SharePointService.java`, add `deleteUrl` next to the other pure URL helpers (after `createUploadSessionUrl`, before `contentRange`):

```java
    static String deleteUrl(String driveId, String targetDir, String filename) {
        return GRAPH + "/drives/" + driveId + "/root:/" + graphPath(targetDir, filename);
    }
```

Add `delete` next to `upload`, in the "Network operations (overridable for tests)" section:

```java
    public void delete(String token, String driveId, String targetDir, String filename) {
        var req = HttpRequest.newBuilder(URI.create(deleteUrl(driveId, targetDir, filename)))
            .header("Authorization", "Bearer " + token)
            .DELETE()
            .build();
        sendString(req, "delete of " + filename);
        log.info("Deleted '{}' from SharePoint.", filename);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SharePointServiceTest`
Expected: PASS (all tests in the class, including the new one).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/javalover/feeearner/sharepoint/SharePointService.java \
        src/test/java/net/javalover/feeearner/sharepoint/SharePointServiceTest.java
git commit -m "feat: add SharePointService.delete for test-file cleanup"
```

---

### Task 2: `DeployService.testConnection` + `SharePointTestResult`

**Files:**
- Create: `src/main/java/net/javalover/feeearner/service/SharePointTestResult.java`
- Modify: `src/main/java/net/javalover/feeearner/service/DeployService.java`
- Test: `src/test/java/net/javalover/feeearner/service/DeployServiceTest.java`

**Interfaces:**
- Consumes: `SharePointService.acquireToken(AppConfig)`, `.resolveSiteId(String, AppConfig)`, `.resolveDriveId(String, String)`, `.upload(String, String, String, String, byte[])`, `.delete(String, String, String, String)` (Task 1). `AppConfig.sharePointTargetDir()` (existing).
- Produces: `public record SharePointTestResult(String filename, String cleanupWarning)`, `public SharePointTestResult DeployService.testConnection(AppConfig config)`, `static String DeployService.testFilename(Clock clock)`, `static byte[] DeployService.randomContent(int length, Random random)` — all consumed by Task 3's `TestSharePointWindow`.

- [ ] **Step 1: Create the `SharePointTestResult` record**

Create `src/main/java/net/javalover/feeearner/service/SharePointTestResult.java`:

```java
package net.javalover.feeearner.service;

/**
 * Result of {@link DeployService#testConnection}. {@code cleanupWarning} is {@code null}
 * when the post-upload delete of the test file succeeded; otherwise it holds the cleanup
 * failure's message, while the test itself is still reported as a success.
 */
public record SharePointTestResult(String filename, String cleanupWarning) {
}
```

- [ ] **Step 2: Write failing tests for the pure helpers**

Add to `DeployServiceTest.java`. First add these imports at the top, alongside the existing ones:

```java
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
```

Then add these test methods, after `sharePointFileNameFormat`:

```java
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
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn test -Dtest=DeployServiceTest#testFilenameFormat+randomContentHasRequestedLength+randomContentOnlyUsesAlphanumericAscii`
Expected: FAIL — compile error, `testFilename`/`randomContent` do not exist on `DeployService`.

- [ ] **Step 4: Implement the pure helpers**

In `DeployService.java`, add these imports:

```java
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
```

Add a private static formatter constant near the top of the class, alongside `log`:

```java
    private static final DateTimeFormatter TEST_FILENAME_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String RANDOM_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
```

Add the two pure static helpers, after `sharePointFileName`:

```java
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -Dtest=DeployServiceTest`
Expected: PASS (all existing tests plus the three new ones).

- [ ] **Step 6: Write failing tests for `testConnection`**

Add these test methods to `DeployServiceTest.java`, after the helpers added in Step 2 (still before the `recordingSharePoint` helper or after it — place after `deployForFeeEarnerThrowsWhenNoRun`, at the end of the class):

```java
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
```

- [ ] **Step 7: Run tests to verify they fail**

Run: `mvn test -Dtest=DeployServiceTest#testConnectionReturnsFilenameOnSuccess+testConnectionReportsCleanupWarningWhenDeleteFails+testConnectionPropagatesUploadFailure`
Expected: FAIL — compile error, `testConnection` does not exist on `DeployService`.

- [ ] **Step 8: Implement `testConnection`**

Add to `DeployService.java`, after `deployForFeeEarner` and before the private `uploadOne`:

```java
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
```

- [ ] **Step 9: Run tests to verify they pass**

Run: `mvn test -Dtest=DeployServiceTest`
Expected: PASS (all tests in the class).

- [ ] **Step 10: Run the full unit test suite**

Run: `mvn test`
Expected: PASS (no regressions elsewhere).

- [ ] **Step 11: Commit**

```bash
git add src/main/java/net/javalover/feeearner/service/SharePointTestResult.java \
        src/main/java/net/javalover/feeearner/service/DeployService.java \
        src/test/java/net/javalover/feeearner/service/DeployServiceTest.java
git commit -m "feat: add DeployService.testConnection SharePoint connectivity check"
```

---

### Task 3: `TestSharePointWindow` + menu wiring

**Files:**
- Create: `src/main/java/net/javalover/feeearner/ui/TestSharePointWindow.java`
- Modify: `src/main/java/net/javalover/feeearner/ui/MainWindow.java:81-91`

**Interfaces:**
- Consumes: `DeployService.testConnection(AppConfig)` → `SharePointTestResult` (Task 2); `AppConfig.sharePointClientId()`, `.sharePointTenantId()`, `.sharePointSecretId()`, `.sharePointHost()`, `.sharePointSiteName()`, `.sharePointTargetDir()` (existing).
- Produces: `public TestSharePointWindow(DeployService deploySvc, AppConfig config)`, `public void show(Window owner)` — wired into `MainWindow`'s "Sharepoint Deployment" menu. Nothing downstream consumes this (terminal UI class).

No automated test for this task: the `ui` package has zero existing test coverage in this codebase (JavaFX windows are verified manually, per `SingleDeployWindow`/`DeployAllWindow`/etc. precedent) — this task ends with a manual verification step instead of a JUnit cycle.

- [ ] **Step 1: Create `TestSharePointWindow`**

Create `src/main/java/net/javalover/feeearner/ui/TestSharePointWindow.java`:

```java
package net.javalover.feeearner.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.service.DeployService;

import java.io.PrintWriter;
import java.io.StringWriter;

public class TestSharePointWindow {

    private final DeployService deploySvc;
    private final AppConfig config;

    public TestSharePointWindow(DeployService deploySvc, AppConfig config) {
        this.deploySvc = deploySvc;
        this.config    = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Test Sharepoint");
        stage.setWidth(600);
        stage.setHeight(450);

        var paramsArea = new TextArea(formatParams());
        paramsArea.setEditable(false);
        paramsArea.setWrapText(true);
        paramsArea.setPrefRowCount(6);

        var resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        VBox.setVgrow(resultArea, javafx.scene.layout.Priority.ALWAYS);

        var testBtn = new Button("Test");
        testBtn.setOnAction(e -> resultArea.setText(runTest()));

        var dismissBtn = new Button("Dismiss");
        dismissBtn.setOnAction(e -> stage.close());

        var footer = new HBox(10, testBtn, dismissBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8, 12, 8, 12));

        var center = new VBox(8, paramsArea, resultArea);
        center.setPadding(new Insets(12, 12, 0, 12));
        VBox.setVgrow(resultArea, javafx.scene.layout.Priority.ALWAYS);

        var root = new BorderPane();
        root.setCenter(center);
        root.setBottom(footer);

        stage.setScene(new Scene(root));
        stage.showAndWait();
    }

    private String formatParams() {
        return "Client ID:    " + config.sharePointClientId() + "\n"
            + "Tenant ID:    " + config.sharePointTenantId() + "\n"
            + "Secret:       " + config.sharePointSecretId() + "\n"
            + "Host:         " + config.sharePointHost() + "\n"
            + "Site Name:    " + config.sharePointSiteName() + "\n"
            + "Target Dir:   " + config.sharePointTargetDir();
    }

    private String runTest() {
        try {
            var result = deploySvc.testConnection(config);
            var sb = new StringBuilder("SUCCESS\nFile stored: ").append(result.filename());
            if (result.cleanupWarning() != null) {
                sb.append("\nNote: cleanup delete failed: ").append(result.cleanupWarning());
            }
            return sb.toString();
        } catch (Exception ex) {
            var sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            return sw.toString();
        }
    }
}
```

- [ ] **Step 2: Wire the menu item into `MainWindow`**

In `MainWindow.java`, replace the "Sharepoint Deployment" block (lines 81-88):

```java
        // ── Sharepoint Deployment ──
        var deployAll = new MenuItem("Deploy All Spreadsheets");
        deployAll.setOnAction(e ->
            new DeployAllWindow(deploySvc, runRepo, config).show(primaryStage));
        var deploySingle = new MenuItem("Deploy Single Spreadsheet");
        deploySingle.setOnAction(e ->
            new SingleDeployWindow(deploySvc, feeEarnerRepo, config).show(primaryStage));
        var sharepointMenu = new Menu("Sharepoint Deployment", null, deployAll, deploySingle);
```

with:

```java
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
        var sharepointMenu = new Menu("Sharepoint Deployment", null,
            deployAll, deploySingle, testSharepoint);
```

- [ ] **Step 3: Compile**

Run: `mvn compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Manual verification**

This requires a Liberica **Full** JDK (bundles `javafx.*`) per `CLAUDE.md` — verify with `java --list-modules | grep javafx` before running.

1. Run: `java -jar target/FeeEarnerReport-1.0-SNAPSHOT.jar /path/to/credentials.properties` (build first with `mvn package` if the jar is stale).
2. Open "Sharepoint Deployment" → "Test Sharepoint". Confirm the six parameters are displayed in full plaintext (client id, tenant id, secret, host, site name, target dir) matching what's in `report_param`.
3. Click "Test" against valid SharePoint credentials. Confirm the result area shows `SUCCESS` and a `sharepoint_test_<timestamp>.txt` filename, and that the file no longer exists in the SharePoint target directory afterward (cleanup worked).
4. Click "Dismiss" — window closes.
5. Reopen "Test Sharepoint", temporarily break one credential (e.g. via "Parameters" → "Modify Parameters", change `shrpnt_secret_id` to garbage), click "Test" again. Confirm the result area shows a full, readable stack trace (not just a one-line message). Restore the correct secret afterward via "Modify Parameters".

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/TestSharePointWindow.java \
        src/main/java/net/javalover/feeearner/ui/MainWindow.java
git commit -m "feat: add Test Sharepoint window and menu item"
```

---

## Self-Review Notes

- **Spec coverage:** §1 (`SharePointService.delete`) → Task 1. §2 (`DeployService.testConnection`/`SharePointTestResult`) → Task 2. §3 (menu wiring) and §4 (`TestSharePointWindow`) → Task 3. §5 (testing) — unit tests folded into Tasks 1-2's TDD cycles; manual GUI verification folded into Task 3 Step 4; the spec's optional `SharePointServiceIT` round-trip case for `delete` was left out of this plan as a non-blocking follow-up (existing `*IT` tests hit the real tenant and require local credentials not present in this environment) — flagging here rather than silently dropping it.
- **Placeholder scan:** none found — every step has literal, complete code.
- **Type consistency:** `SharePointTestResult(String filename, String cleanupWarning)` used identically in Task 2 (produced) and Task 3 (`result.filename()`, `result.cleanupWarning()`); `DeployService.testConnection(AppConfig)` signature matches between Task 2 and Task 3's `runTest()`; `SharePointService.delete(String, String, String, String)` signature matches between Task 1 and Task 2's usage.
