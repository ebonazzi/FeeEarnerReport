# Design: "Test Sharepoint" connectivity-check window

**Date:** 2026-07-09
**Status:** Approved — ready for implementation planning

## Context

FeeEarnerReport already supports SharePoint deployment (`SharePointService`, `DeployService`,
`DeployAllWindow`, `SingleDeployWindow` — see
`docs/superpowers/specs/2026-06-11-sharepoint-deploy-and-past-regeneration-design.md`). The six
SharePoint parameters (`shrpnt_client_id`, `shrpnt_tenant_id`, `shrpnt_secret_id`, `shrpnt_host`,
`shrpnt_site_name`, `shrpnt_target_dir`) are stored in `report.report_param` and read via
`AppConfig`. When any of these is wrong, the failure currently only surfaces as a real deploy
failure (`SingleDeployWindow`/`DeployAllWindow`), mixed in with actual report-generation
concerns.

This change adds a standalone diagnostic: a new "Test Sharepoint" menu item that uploads a small
random test file to the configured SharePoint location and reports success or a full stack trace,
so SharePoint configuration can be validated (and corrected via the existing
`ParameterEditorWindow`) independently of running a real deploy.

## Goals

- Let the user verify SharePoint connectivity/configuration from the GUI without deploying a real
  spreadsheet.
- Show the six configured SharePoint parameters (in full, including the secret) so the user can
  visually cross-check them before testing.
- On failure, surface the full exception stack trace in the UI (copyable), since this is a
  diagnostic tool — unlike existing windows, which only show `getMessage()`.
- On success, clean up the uploaded test file automatically so test runs don't accumulate files in
  the target SharePoint directory.
- Reuse the existing `SharePointService`/`DeployService` layering; no new business logic in the UI
  controller.

## Non-goals

- No changes to the real deploy flow (`DeployAllWindow`, `SingleDeployWindow`,
  `DeployService.deployAll`/`deployForFeeEarner`).
- No new `report_param` rows — reuses the existing six SharePoint params.
- No retry/backoff logic — a single attempt per click of "Test".

---

## 1. `SharePointService` — new `delete` method

Add one method alongside the existing `acquireToken` / `resolveSiteId` / `resolveDriveId` /
`upload`:

```java
public void delete(String token, String driveId, String targetDir, String filename)
```

`DELETE /v1.0/drives/{driveId}/root:/{targetDir}/{filename}`, `Authorization: Bearer {token}`.
Uses the same `sendString(...)` plumbing as `upload`, so a non-2xx response, `IOException`, or
`InterruptedException` throws `SharePointException` exactly as the other methods do.

---

## 2. `DeployService` — new `testConnection` method

```java
public SharePointTestResult testConnection(AppConfig config)
```

`SharePointTestResult` is a new record in the `service` package:

```java
public record SharePointTestResult(String filename, String cleanupWarning) {}
```

`cleanupWarning` is `null` when the post-upload delete succeeded.

Steps:
1. Generate ~512 random alphanumeric characters (`SecureRandom`) as the file content
   (UTF-8 bytes).
2. Build the filename: `sharepoint_test_<yyyyMMdd_HHmmss>.txt` (system clock at call time).
3. `acquireToken(config)` → `resolveSiteId(token, config)` → `resolveDriveId(token, siteId)` →
   `upload(token, driveId, config.sharePointTargetDir(), filename, bytes)`. Any
   `SharePointException` here is **not caught** — it propagates to the caller, matching
   `deployForFeeEarner`'s propagate-on-error convention (single/synchronous operations let the UI
   catch and display the error; only the batch operations swallow-and-track).
4. On successful upload, attempt `delete(token, driveId, config.sharePointTargetDir(), filename)`.
   If this throws, catch it, log at WARN via SLF4J, and return
   `new SharePointTestResult(filename, ex.getMessage())` — the test as a whole is still a success
   (connectivity/config were validated by the successful upload), with the cleanup issue reported
   as a secondary note rather than failing the whole check.
5. On successful upload *and* successful cleanup, return
   `new SharePointTestResult(filename, null)`.

No `ProgressTracker` involvement — this is a single synchronous operation, not a batch.

---

## 3. Menu wiring (`MainWindow`)

Add a third item to the existing `sharepointMenu`, below "Deploy Single Spreadsheet":

```java
var testSharepoint = new MenuItem("Test Sharepoint");
testSharepoint.setOnAction(e -> new TestSharePointWindow(deploySvc, config).show(primaryStage));
var sharepointMenu = new Menu("Sharepoint Deployment", null, deployAll, deploySingle, testSharepoint);
```

No new constructor parameters needed on `MainWindow` — `deploySvc` and `config` are already
available there.

---

## 4. New window: `TestSharePointWindow`

New class in `ui/`, following the modal conventions already used by `SingleDeployWindow`
(`Modality.APPLICATION_MODAL`, `VBox` root with `Insets` padding, synchronous call on the FX
thread — this is a handful of quick HTTP calls, not a batch, so no background-`Thread` /
`Platform.runLater` / `Timeline` polling is needed).

Constructor: `TestSharePointWindow(DeployService deploySvc, AppConfig config)`.
Public method: `show(Window owner)`.

Layout, top to bottom:

1. **Read-only, non-editable `TextArea`** (wrapped, sized to content), populated on window open
   with the six SharePoint parameters read from `config`, in full plaintext (including the
   secret):
   ```
   Client ID:    <config.sharePointClientId()>
   Tenant ID:    <config.sharePointTenantId()>
   Secret:       <config.sharePointSecretId()>
   Host:         <config.sharePointHost()>
   Site Name:    <config.sharePointSiteName()>
   Target Dir:   <config.sharePointTargetDir()>
   ```
2. **"Test" button.** On click, calls `deploySvc.testConnection(config)` directly on the FX
   thread, then writes into a second read-only `TextArea` below:
   - **Success:** `"SUCCESS\nFile stored: <filename>"`, plus an appended
     `"\nNote: cleanup delete failed: <cleanupWarning>"` line when `cleanupWarning != null`.
   - **Failure:** the full stack trace of the thrown exception, formatted via
     `StringWriter`/`PrintWriter` + `ex.printStackTrace(pw)`, so it can be copy-pasted for
     diagnosis. This is a new UI pattern in this codebase (existing windows only ever show
     `ex.getMessage()` in an `Alert`); it's intentional here since this window exists specifically
     to help pinpoint SharePoint config errors.
3. **"Dismiss" button.** Closes the stage. No confirmation needed — this window makes no
   persistent changes to app state (any test file it creates is cleaned up per §2 step 4/5, or, in
   the rare case cleanup also failed, is a `sharepoint_test_*.txt` file the user can identify and
   remove manually — its stack-trace-diagnosable name is visible in the result text area).

The intended user flow (per the original request): open "Sharepoint Deployment" → "Test
Sharepoint", review the printed parameters, click "Test". On success, click "Dismiss". On failure,
copy the stack trace out of the result text area, click "Dismiss", then go to "Parameters" →
"Modify Parameters" to fix the SharePoint values, and re-open "Test Sharepoint" to re-verify.

---

## 5. Testing

Unit tests (`mvn test`):
- `SharePointTestResult` is a plain record — no dedicated test needed beyond what exercises
  `DeployService.testConnection`.
- If `DeployService.testConnection`'s filename-generation and random-content logic are factored
  into small pure helper methods (e.g. a static `testFilename(Clock)` / `randomContent(int)`),
  they can be unit tested directly, consistent with the existing "pure, unit-testable helpers"
  convention used for SharePoint URL building (§6.1 of the 2026-06-11 design).

Integration tests (`*IT.java`, `@Tag("integration")`, excluded from default `mvn test`):
- `SharePointServiceIT` (if one does not already cover `upload`) gains a round-trip case for the
  new `delete` method: upload then delete a small test blob against the real tenant, consistent
  with the existing `SharePointService` integration-test convention.

Manual verification (GUI, not automatable): launch `FxApplication`, open "Sharepoint Deployment" →
"Test Sharepoint", confirm the six parameters display correctly, click "Test" against valid
credentials and confirm success + filename, then temporarily corrupt one credential via "Modify
Parameters" and re-test to confirm the stack trace renders usefully.

---

## Open questions

None — all grey areas resolved during brainstorming:
- Test file naming: timestamped (`sharepoint_test_<yyyyMMdd_HHmmss>.txt`), not fixed/overwritten. ✔
- Post-success cleanup: auto-delete the test file. ✔
- Cleanup-failure handling: still report overall success, with a secondary warning note. ✔
- Secret display in the parameter text area: full plaintext, unmasked. ✔
