# Design: SharePoint deployment, past-run regeneration, menu restructuring, email recipient change

**Date:** 2026-06-11
**Status:** Approved — ready for implementation planning

## Context

FeeEarnerReport is a Java 25 (Liberica Full JDK) desktop app that runs as a headless CLI
(`Main`) and a JavaFX GUI (`FxApplication`) from one codebase. Per active fee earner it
queries SQL Server, builds a 5-sheet Excel workbook, archives the rows, stores the workbook
as a DB blob (`report.FeeEarnersRun`), and emails it. This change adds four capabilities and
restructures the GUI menu:

1. A five-menu top-level GUI structure replacing the single "Actions" menu.
2. **SharePoint deployment** — upload generated spreadsheets to a SharePoint folder via the
   Microsoft Graph REST API, with resumable upload for files over 4 MB.
3. **Past-run regeneration** — rebuild spreadsheets (all fee earners, or one) from the archive
   tables of a chosen historical `run_id`.
4. **Email recipient change** — spreadsheets go *only* to the `email_recipients` list; the fee
   earner is no longer emailed.

The authoritative design doc for existing behavior is
`docs/superpowers/specs/2026-06-04-fee-earner-report-design.md`. This document covers only the
deltas.

## Goals

- Restructure the GUI into five top-level menus without changing existing window behavior.
- Email spreadsheets only to the configured recipient list, never to the fee earner.
- Regenerate spreadsheets from archived run data and store them as the *current* spreadsheet.
- Upload stored spreadsheets to SharePoint, supporting files larger than 4 MB.
- Keep the CLI/GUI parity: every GUI handler calls the same service method the CLI would.

## Non-goals

- No change to live generation logic (Lead/Matter merge, intersect handling, workbook layout).
- No change to the archive table schema or the partitioning scheme.
- No SharePoint *download* / listing / deletion — upload only.
- Native-image (`-Pnative`) hardening of `HttpClient` TLS is verified opportunistically, not a
  release gate for the JVM build.

---

## 1. SharePoint client — chosen approach

**JDK `java.net.http.HttpClient` + Jackson.** This faithfully emulates the reference Python
(`requests`-based) uploader with no heavy SDK. The HTTP client is built into Java 25; the only
new Maven artifact is `jackson-databind` for JSON parsing. This gives full control over the
resumable chunk loop and the smallest uberjar.

Rejected alternatives:
- **Microsoft Graph SDK + Azure Identity** — provides `LargeFileUploadTask` but pulls a large
  dependency tree (Reactor, Azure Identity) into the shaded jar.
- **Apache HttpClient 5 + Jackson** — an extra HTTP dependency with no advantage over the
  built-in client for this use case.

---

## 2. Parameters (`report_param` table / `AppConfig`)

### New SharePoint params (six)

| Param | Example | Purpose |
|---|---|---|
| `shrpnt_client_id` | `xxxxxxxx-...-xxxx` | OAuth2 client (application) id |
| `shrpnt_tenant_id` | `xxxxxxxx...` | Azure AD tenant id |
| `shrpnt_secret_id` | `xxx~...` | OAuth2 client secret value |
| `shrpnt_host` | `eliobonazzigmail.sharepoint.com` | SharePoint hostname |
| `shrpnt_site_name` | `root` | `root` for the top-level site, else a named site |
| `shrpnt_target_dir` | `Shared Documents/Shared Documents/LSP/VIC_reports` | folder path inside the document library |

`AppConfig` gains six `require()`-backed accessors:
`sharePointClientId()`, `sharePointTenantId()`, `sharePointSecretId()`, `sharePointHost()`,
`sharePointSiteName()`, `sharePointTargetDir()`.

### Existing param reused

`email_recipients` (pipe-delimited) already exists and is accessed via
`AppConfig.emailRecipients()`.

All six new params are editable in the existing `ParameterEditorWindow` automatically, since it
is table-driven (no per-param UI code). They must be inserted into `report.report_param`
out of band (the app does not create param rows), consistent with current operations.

---

## 3. Menu restructuring (`MainWindow`)

Replace the single "Actions" menu with five top-level menus. Each item opens the existing or new
window; no business logic moves into controllers.

| Menu | Item | Window | Status |
|---|---|---|---|
| **Parameters** | Modify Parameters | `ParameterEditorWindow` | unchanged |
| **Runs** | Previous Runs | `PreviousRunsWindow` | unchanged |
| **Spreadsheet Generation** | Generate All Spreadsheets | `GenerateAllWindow` | unchanged |
| | Generate Single Spreadsheet | `SingleGenerateWindow` | unchanged |
| | Generate All Past Spreadsheets | `GenerateAllPastWindow` | **new** |
| | Generate Single Past Spreadsheets | `GenerateSinglePastWindow` | **new** |
| **Email Send** | Email All Spreadsheets | `EmailAllWindow` | recipient change (§4) |
| | Email Single Spreadsheet | `SingleEmailWindow` | recipient change (§4) |
| **Sharepoint Deployment** | Deploy All Spreadsheets | `DeployAllWindow` | **new** |
| | Deploy Single Spreadsheet | `SingleDeployWindow` | **new** |

`MainWindow`'s constructor gains the new collaborators (`DeployService`, and access to
`SpreadsheetService`'s new methods); see §7.

---

## 4. Email change — recipients only, never the fee earner

In `MailSender.buildMessage`, the message recipients become **only** the `email_recipients`
list (pipe-delimited, split/trimmed as today, added as **To**). The fee earner's `usrEmail` is
**dropped entirely** — no longer a To or CC recipient.

Everything else is unchanged:
- One email per fee earner, single spreadsheet attachment each.
- `EmailService.sendAll` still iterates the latest run's `FeeEarnerRun`s with the
  `ProgressTracker` failure-collecting loop.
- `EmailAllWindow` / `SingleEmailWindow` UI unchanged.

Consequence (as intended): for N fee earners, each recipient in `email_recipients` receives N
separate emails, one attachment each.

If `email_recipients` is empty, sending is a no-op failure recorded per fee earner in the
tracker (no silent success) — implementation will treat a blank recipient list as an error for
that fee earner rather than sending a recipient-less message.

---

## 5. Past-run regeneration (from archive tables)

### 5.1 Archive reads

Add read methods to `ArchiveRepository` mirroring the existing inserts:

- `readFullTask(runId, usrID) -> List<FullTaskRow>`
- `readLimitation(runId, usrID) -> List<LimitationRow>`
- `readAged(runId, usrID) -> List<AgedRow>`
- `readDuplicate(runId, usrID) -> List<DuplicateRow>`
- `readHighVolume(runId, usrID) -> List<HighVolumeRow>`

Each is `SELECT … FROM report.<table> WHERE run_id = ? AND usrID = ? ORDER BY row_number`,
mapping columns back into the row records. The `[Task Complete]` column (full_task only, always
`0`) is ignored — it has no field in `FullTaskRow`. Column names match the insert SQL exactly
(`[Report Date]`, `[Matter Number]`, … `[Type]`, plus each table's extra column).

Add `getFeeEarnerIdsForRun(runId) -> List<Integer>` — distinct `usrID` across the five archive
tables for a run — to enumerate which fee earners have data in a given run.

These reads may use the injected `DataSource` directly (read-only, no transaction needed), or
take a `Connection` for symmetry; reads are not part of the persistence transaction.

### 5.2 Regeneration is a pure rebuild

Archive rows already contain the merged Lead+Matter data per worksheet (that is how they were
written). Therefore regeneration is simply: read the five row lists for `(runId, usrID)` →
`WorkbookBuilder.build(...)`. **No Lead/Matter selection, no intersect logic, no live SQL
function calls.** This is the central simplification versus live generation.

### 5.3 Storage target (decision: "most recent entry")

The regenerated `.xlsx` is written to the **most recent `FeeEarnersRun` row for that fee
earner**, regardless of which `run_id` it belongs to:
- `excel_spreadsheet` blob replaced with the newly built workbook bytes,
- `excel_filename` set to the standard `fe_<usrID>_<YYYYMMDD>_<runId>.xlsx` name (the most
  recent row's `run_id` and today's date),
- `stored_at = GETDATE()`.

**The archive tables are not modified** — they are the read-only source. This is a deliberate
consequence of the chosen behavior: the latest run's stored blob now reflects the *selected
historical run's* data, and that blob is what Email Send and SharePoint Deployment subsequently
pick up (both operate on the latest run).

New `RunRepository` method:
`updateMostRecentFeeEarnerBlob(int usrID, String filename, byte[] xlsx)` — `UPDATE … SET
excel_spreadsheet=?, excel_filename=?, stored_at=GETDATE()` for the `TOP 1` row of that `usrID`
ordered by `run_id DESC`. If the fee earner has no existing `FeeEarnersRun` row, that fee earner
is recorded as a failure in the tracker (cannot regenerate-into-most-recent without one).

### 5.4 Service methods

`SpreadsheetService` gains:
- `generateFromArchive(int usrID, int runId, AppConfig config)` — single fee earner.
- `generateAllFromArchive(int runId, AppConfig config, ProgressTracker tracker)` — every fee
  earner returned by `getFeeEarnerIdsForRun(runId)`, with the existing per-item
  catch/log/record-failure/continue loop and `ExecutorService` pattern.

### 5.5 New windows

- **`GenerateAllPastWindow`** — a grid of past runs (`run_id`, `day_run`, `started_at`,
  `finished_at`, fee-earner count) from `RunRepository.getAllRuns()`. User selects one run and
  clicks Generate; regenerates all fee earners in that run. Progress UI mirrors
  `GenerateAllWindow` (`ProgressTracker` + `Timeline` poll + `Platform.runLater`).
- **`GenerateSinglePastWindow`** — the same run grid on top; selecting a run loads its fee
  earners (`RunRepository.getFeeEarnerRunsForRun(runId)`) into a lower grid. User selects a
  run *and* a fee earner, then Generate; regenerates just that fee earner. Mirrors
  `SingleGenerateWindow`'s direct-call style.

---

## 6. SharePoint deployment

### 6.1 `SharePointService` (new `sharepoint` package)

Stateless emulation of the reference Python uploader, built on `HttpClient` + Jackson.

- `acquireToken(AppConfig) -> String` — `POST
  https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token`, form body
  `grant_type=client_credentials`, `client_id`, `client_secret`, `scope=
  https://graph.microsoft.com/.default`; returns `access_token`.
- `resolveSiteId(token, AppConfig) -> String` — `root` ⇒ `GET /v1.0/sites/{host}`; named site
  ⇒ `GET /v1.0/sites/{host}:/sites/{siteName}`; returns the `id`.
- `resolveDriveId(token, siteId) -> String` — `GET /v1.0/sites/{siteId}/drive`; returns `id`
  (the default document library).
- `upload(token, driveId, targetDir, filename, byte[] bytes)`:
  - **≤ 4 MB:** simple `PUT /v1.0/drives/{driveId}/root:/{targetDir}/{filename}:/content`,
    `Content-Type: application/octet-stream`.
  - **> 4 MB:** `POST …/root:/{targetDir}/{filename}:/createUploadSession` with body
    `{ item: { "@microsoft.graph.conflictBehavior": "replace", name } }`; then upload the bytes
    in 10 MB chunks (a 320 KB multiple) via `PUT {uploadUrl}` with headers `Content-Length` and
    `Content-Range: bytes {start}-{end}/{total}`. The `uploadUrl` is pre-authenticated (no
    `Authorization` header). The final chunk returns 200/201 with the driveItem JSON.

Uploads are sourced from the in-memory blob (`byte[]`) read from `FeeEarnersRun`; no temp file
is written. URL building and `Content-Range` computation are factored into pure, unit-testable
helpers.

### 6.2 `DeployService` (new, mirrors `EmailService`)

- Resolves token + siteId + driveId **once per batch** and reuses them across all uploads.
- For each fee earner: read the latest `FeeEarnerRun` (blob + `[Fee Earner]` name), build the
  SharePoint filename (§6.3), and call `SharePointService.upload`.
- `deployAll(List<FeeEarnerRun> runs, AppConfig, ProgressTracker)` and
  `deployForFeeEarner(int usrID, AppConfig)` — same `ProgressTracker` / failure-collection
  contract as `EmailService` so one failed upload never aborts the batch.

### 6.3 SharePoint filename

`Fee Earner_{usrID}_{name}_VIC_task_report.xlsx`
e.g. `Fee Earner_35189_Joseph Tran_VIC_task_report.xlsx`.

- `{usrID}` and `{name}` come from the `FeeEarnerRun` row (`usrID`, `[Fee Earner]`).
- `VIC` is a literal constant.
- This name is used **only** for the SharePoint upload. The email attachment and the DB
  `excel_filename` keep the existing `fe_<usrID>_<YYYYMMDD>_<runId>.xlsx`.

### 6.4 New windows

- **`DeployAllWindow`** — clone of `EmailAllWindow`: latest run's `FeeEarnerRun`s, progress
  tracker, failure list.
- **`SingleDeployWindow`** — clone of `SingleEmailWindow`: a TableView with a per-row Deploy
  button calling `deployForFeeEarner`.

---

## 7. pom.xml & bootstrap

- Add `com.fasterxml.jackson.core:jackson-databind` (compile scope). It is shaded into the
  uberjar; the existing `ServicesResourceTransformer` already preserves `META-INF/services`.
  No JavaFX or logging dependency changes.
- Bootstrap (identical edits in `Main` and `FxApplication`): construct `SharePointService` and
  `DeployService` after the existing services, and pass `DeployService` (and the new
  `SpreadsheetService` past-regen capability, already on the existing instance) into
  `MainWindow`.
- Native build note: under `-Pnative` (gluonfx), `HttpClient` TLS may require a GraalVM
  reflection/resource hint. Verify during implementation; not a blocker for the JVM uberjar.

---

## 8. Testing

Unit tests (run under `mvn test`):
- SharePoint filename builder.
- Graph URL construction and `Content-Range` / chunk-boundary math (pure functions).
- Archive `ResultSet` → row-record mappers (via a fake/mocked `ResultSet`).
- `MailSender.buildMessage` recipient logic: To = `email_recipients` only, fee earner absent;
  extend the existing `buildMessage` test.

Integration tests (`*IT.java`, `@Tag("integration")`, excluded from `mvn test`, run via
`-Pintegration`):
- `ArchiveRepository` reads against a real SQL Server (round-trip: write then read).
- `SharePointService` token/site/drive/upload against the real tenant (kept tagged so it never
  runs in the default build). Consistent with the existing `*IT` convention and
  `TestDataSourceFactory`.

---

## Open questions

None — all grey areas resolved during brainstorming:
- Email shape: one email per fee earner, recipients-only. ✔
- Past-regen storage: most-recent `FeeEarnersRun` row, archive untouched. ✔
- SharePoint destination: host + site_name + target_dir params (six total). ✔
- SharePoint filename: SharePoint-only. ✔
- HTTP/JSON stack: JDK `HttpClient` + Jackson. ✔
