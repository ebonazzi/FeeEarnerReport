# Fee Earner Report — Design Specification
**Date:** 2026-06-04  
**Status:** Approved

---

## 1. Overview

A Java 25 (Liberica JDK) desktop application that generates per-fee-earner Excel spreadsheets from SQL Server data and emails them. The application runs either headless (for debugging in IntelliJ) or as a JavaFX GUI. GraalVM native images are produced for Linux and Windows via the gluonfx-maven-plugin.

---

## 2. Technology Stack

| Concern | Library / Tool |
|---|---|
| Language | Java 25, Liberica JDK |
| UI | JavaFX (bundled with Liberica) |
| Excel | Apache POI `poi-ooxml` |
| Email | Jakarta Mail API + Angus Mail |
| Database driver | `mssql-jdbc` |
| Connection pool | HikariCP |
| Logging | `io.jstach.rainbowgum:rainbowgum-jdk:0.8.0` |
| Build | Maven (shade plugin for uberjar; gluonfx-maven-plugin for native) |
| Database | SQL Server — schema `report` in database `MCMBLIVE` |

---

## 3. Project Structure & Build

### 3.1 Single Maven module, dual entry points

```
net.javalover.feeearner
├── Main.java                  ← CLI entry point (no JavaFX, for IntelliJ debugging)
├── FxApplication.java         ← JavaFX entry point
├── config/
│   └── AppConfig.java         ← immutable snapshot of all loaded parameters
├── model/                     ← Java records (immutable)
│   ├── FeeEarner.java
│   ├── RunInfo.java
│   ├── FeeEarnerRun.java
│   ├── AppParam.java
│   ├── FullTaskRow.java
│   ├── LimitationRow.java
│   ├── AgedRow.java
│   ├── DuplicateRow.java
│   └── HighVolumeRow.java
├── repository/
│   ├── ParamRepository.java
│   ├── RunRepository.java
│   ├── FeeEarnerRepository.java
│   ├── WorksheetRepository.java
│   └── ArchiveRepository.java
├── service/
│   ├── ParameterService.java
│   ├── RunService.java
│   ├── SpreadsheetService.java
│   └── EmailService.java
├── excel/
│   ├── WorkbookBuilder.java
│   ├── FullReportSheetBuilder.java
│   ├── LimitationSheetBuilder.java
│   ├── AgedSheetBuilder.java
│   ├── DuplicateSheetBuilder.java
│   └── HighVolumeSheetBuilder.java
├── email/
│   └── MailSender.java
├── logging/
│   └── LoggingInitialiser.java
└── ui/
    ├── MainWindow.java
    ├── ParameterEditorWindow.java
    ├── PreviousRunsWindow.java
    ├── FeeEarnerDetailWindow.java
    ├── SpreadsheetViewerWindow.java
    ├── GenerateAllWindow.java
    ├── EmailAllWindow.java
    ├── SingleGenerateWindow.java
    └── SingleEmailWindow.java
```

### 3.2 Maven build profiles

| Profile | Plugin | Entry point | Output |
|---|---|---|---|
| `default` | `maven-shade-plugin` | `FxApplication` (override with `-Dmain.class=net.javalover.feeearner.Main`) | `FeeEarnerReport-1.0-SNAPSHOT.jar` |
| `native` | `gluonfx-maven-plugin` | `FxApplication` | Native binary for host OS |

### 3.3 Key dependencies

```xml
org.apache.poi:poi-ooxml
com.microsoft.sqlserver:mssql-jdbc
com.zaxxer:HikariCP
io.jstach.rainbowgum:rainbowgum-jdk:0.8.0
jakarta.mail:jakarta.mail-api
org.eclipse.angus:angus-mail
org.openjfx:javafx-controls
org.openjfx:javafx-fxml
<!-- native profile only -->
com.gluonhq:gluonfx-maven-plugin
```

---

## 4. Bootstrap & Configuration

### 4.1 Credential file (command-line argument)

```
sqlserver_hostname=...
sqlserver_dbname=...
sqlserver_portnumber=1433
sqlserver_username=...
sqlserver_password=...
```

Parsed at startup. If missing or malformed, print to `stderr` and exit.

### 4.2 Bootstrap sequence

1. Parse credential file → build a single `DriverManager` connection to `MCMBLIVE`
2. `ParamRepository.loadAll()` → populate `AppConfig`
3. Close bootstrap connection
4. `LoggingInitialiser.init(logDir, level)` — programmatic Rainbow Gum setup
5. Build `HikariDataSource` using `thread_pool_size` / `max_thread_pool_size` from params
6. Inject `DataSource` into all repositories

### 4.3 Parameter table entries

| Parameter name | Purpose |
|---|---|
| `log_dir` | Directory for `fee_earner_report.log` |
| `output_dir` | Temp directory for xlsx files during generation |
| `debug_level` | One of: TRACE, DEBUG, INFO, WARN, ERROR |
| `thread_pool_size` | Core thread count for bulk operations |
| `max_thread_pool_size` | Max thread count |
| `email_recipients` | Pipe-separated CC addresses |
| `email_sender` | From address |
| `email_subject` | Email subject line |
| `email_body` | Email body text |
| `smtp_server` | SMTP hostname |
| `smtp_port` | SMTP port |

---

## 5. Data Model

All model classes are Java records (immutable).

### 5.1 FeeEarner
```java
record FeeEarner(int usrID, String feeEarner, String usrEmail,
                 boolean usrActive, String type)
```

### 5.2 RunInfo
```java
record RunInfo(int runId, LocalDate dayRun,
               LocalDateTime startedAt, LocalDateTime finishedAt)
```

### 5.3 FeeEarnerRun
```java
record FeeEarnerRun(int runId, LocalDate dayRun, int usrID,
                    String feeEarner, String usrEmail,
                    String excelFilename, byte[] excelSpreadsheet,
                    LocalDateTime storedAt)
```

### 5.4 Worksheet rows
Each is a record. All share the base columns from `fn_VIC_*_Full_Task_Data` plus:
- `LimitationRow` adds `keyWords`
- `DuplicateRow` adds `duplicate` (always "Yes")
- `HighVolumeRow` adds `matterRowCount`

The `type` field ("Matter" or "Enquiry") is present on all rows.

A sealed interface `BaseRow` is the common supertype of all five row records, carrying the columns shared across all worksheets: `type`, `reportDate`, `matterNumber`, `matterNameDescription`, `department`, `practiceCode`, `officeName`, `jurisdiction`, `feeEarner`, `legalAssistant`, `supervisingFeeEarner`, `taskDescription`, `taskType`, `taskNotes`, `taskOwner`, `taskCreatedDate`, `taskDueDate`. Each concrete record `permits` only the five worksheet row types.

---

## 6. Repository Layer

All repositories receive a `DataSource` at construction. They use try-with-resources for connections.

### 6.1 FeeEarnerRepository
- `List<FeeEarner> getLeadFeeEarners()` — calls `report.fn_VIC_Lead_Active_FeeEarners()`
- `List<FeeEarner> getMatterFeeEarners()` — calls `report.fn_VIC_Matter_Active_FeeEarners()`
- `Set<Integer> getIntersectUserIds()` — INTERSECT query to find fee earners managing both

### 6.2 WorksheetRepository
Ten methods — one per SQL function (Lead × 5 + Matter × 5), each accepting `int usrID`:
```
getLeadFullTaskData(usrID)       getMatterFullTaskData(usrID)
getLeadLimitation(usrID)         getMatterLimitation(usrID)
getLeadAged(usrID)               getMatterAged(usrID)
getLeadDuplicate(usrID)          getMatterDuplicate(usrID)
getLeadHighVolume(usrID)         getMatterHighVolume(usrID)
```

### 6.3 RunRepository
- `int createRun()` — inserts into `spreadsheet_run`, returns generated `run_id`
- `void closeRun(int runId)` — sets `finished_at`
- `void insertFeeEarnerRun(FeeEarnerRun run)` — inserts row with xlsx blob
- `void updateFeeEarnerRun(FeeEarnerRun run)` — updates most-recent run for single-generate scenario
- `Optional<FeeEarnerRun> getMostRecent(int usrID)` — used to validate a prior run exists before single-generate

### 6.4 ArchiveRepository
Five bulk-insert methods, one per archive table. Each accepts `int runId`, `LocalDate dayRun`, `int usrID`, and a `List<? extends BaseRow>` with 1-based `rowNumber` derived from list index.

### 6.5 ParamRepository
- `List<AppParam> loadAll()` — reads all active params
- `void save(AppParam param)` — updates a single parameter value

---

## 7. Service Layer

### 7.1 ParameterService
- `AppConfig load()` — delegates to `ParamRepository.loadAll()`
- `ValidationResult validate(String name, String value)` — for `log_dir` and `output_dir`: checks `Files.isDirectory()` and `Files.isWritable()`; other params pass through
- `void save(AppParam param)` — validates first, then delegates to `ParamRepository.save()`
- `AppConfig reload()` — re-loads all params and re-applies log level

### 7.2 RunService
- `RunInfo startRun()` — calls `RunRepository.createRun()`
- `void finishRun(int runId)` — calls `RunRepository.closeRun(runId)`

### 7.3 SpreadsheetService
`generateForFeeEarner(FeeEarner fe, int runId, LocalDate dayRun, AppConfig config, Consumer<ProgressEvent> progress)`:
1. Determine which functions to call: always call the function set matching the fee earner's source list; if `usrID` is in the intersect set also call the other set
2. Merge Lead + Matter rows for each of the 5 worksheet types
3. `WorkbookBuilder.build(...)` → write to `output_dir/fe_<usrID>_<YYYYMMDD>_<runId>.xlsx`
4. `ArchiveRepository` bulk-insert for each worksheet
5. Read file bytes → `RunRepository.insertFeeEarnerRun(...)` (or `updateFeeEarnerRun` for single-generate)
6. Delete temp file

`generateAll(List<FeeEarner> feeEarners, int runId, LocalDate dayRun, AppConfig config, ProgressTracker tracker)`:
- Submits each fee earner as a `Callable` to a fixed `ExecutorService`
- Uses `CompletionService` to collect results
- On exception: logs error, adds to `tracker.failures`, increments `tracker.failed`; continues loop

### 7.4 EmailService
`sendForFeeEarner(int usrID, int runId, AppConfig config)`:
1. Fetch `FeeEarnerRun` from `RunRepository`
2. Build `MimeMessage`: To=fee earner, CC=pipe-split recipients, attachment=xlsx blob
3. `MailSender.send(message)`

`sendAll(List<FeeEarnerRun> runs, AppConfig config, ProgressTracker tracker)`:
- Same loop-and-continue pattern as `generateAll`

### 7.5 ProgressTracker
```java
class ProgressTracker {
    AtomicInteger total, completed, failed;
    CopyOnWriteArrayList<FailedEntry> failures;
}
record FailedEntry(int usrID, String feeEarner, String errorMessage)
```
`Consumer<ProgressEvent>` passed to CLI prints to stdout; passed to FX windows drives `Platform.runLater()`.

---

## 8. Excel Generation

### 8.1 WorkbookBuilder
Creates one `XSSFWorkbook` with five sheets in order:
1. "Matters/Leads Full Report"
2. "Limitation"
3. "Aged"
4. "Duplicate"
5. "High Volume"

Delegates each to its `SheetBuilder`. Calls `sheet.autoSizeColumn(i)` after all rows written.

### 8.2 Sheet builders
Each builder:
- Writes a bold header row (row 0); first row frozen via `sheet.createFreezePane(0, 1)` (Excel "Freeze Top Row")
- Applies auto-filter across all columns via `sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, lastColIndex))`
- Writes data rows (1-based index = archive `row_number`)
- Uses a shared `CellStyle` for date columns (`dd/MM/yyyy`)
- **`[Type]` column is always the first (leftmost) column** — index 0; remaining columns follow archive table order

### 8.3 File naming
`fe_<usrID>_<YYYYMMDD>_<runId>.xlsx`  
Example: `fe_33800_20260605_1.xlsx`  
Month and day zero-padded via `DateTimeFormatter.ofPattern("yyyyMMdd")`.

### 8.4 Row merge logic
When a fee earner appears in both Lead and Matter lists, rows from both `fn_VIC_Lead_*` and `fn_VIC_Matter_*` are fetched and concatenated. No de-duplication is performed — the `[Type]` column (column 0) distinguishes source.

---

## 9. Email Layer

`MailSender` wraps Jakarta Mail / Angus Mail:
- No SMTP authentication
- `mail.smtp.auth = false`, `mail.smtp.starttls.enable = false`
- Host and port from `AppConfig`
- `To`: fee earner's `usrEmail`
- `CC`: all addresses from pipe-split `email_recipients`
- `From`: `email_sender`
- `Subject`: `email_subject`
- Body: `email_body` (plain text)
- Attachment: xlsx blob, filename = stored `excel_filename`

---

## 10. Logging

### 10.1 Rainbow Gum setup (`rainbowgum-jdk:0.8.0`)

Initialised programmatically in `LoggingInitialiser.init(String logDir, String level)`.

Pattern equivalent to `%d{yyyy-MM-dd HH:mm:ss.SSS}|%-5level|%t|%logger{36}|%msg|%ex%n`:
```
{timestamp}|{level}|{threadName}|{name}|{message}|{throwable}{newline}
```

Appenders:
- **File appender**: `<logDir>/fee_earner_report.log`, rotating at 100 MB, old files compressed (`.gz`)
- **Console appender**: active during startup before log dir is confirmed; disabled after successful file appender init

### 10.2 Dynamic level change
`LoggingInitialiser.applyLevel(String level)` called by `ParameterService.reload()` after a param save.

---

## 11. JavaFX UI Layer

### 11.1 Main window
Empty stage with a top `MenuBar`:
- Modify Parameters
- Show Previous Runs
- Generate All Spreadsheets
- Email All Spreadsheets
- Generate Single Spreadsheet
- Email Single Spreadsheet

### 11.2 Modal windows (`Modality.APPLICATION_MODAL`)

**ParameterEditorWindow**  
- `TableView<AppParam>` with editable `parameter_value` column
- Save button: calls `ParameterService.validate()` → on failure shows `Alert.ERROR` popup, blocks save; on success calls `ParameterService.save()` then `ParameterService.reload()`
- Cancel button: closes without action

**PreviousRunsWindow**  
- Top `TableView<RunInfo>` — selection drives bottom table filter
- Bottom `TableView<FeeEarnerRun>` — filtered by selected `run_id`
- Double-click row → `FeeEarnerDetailWindow` (all fields displayed)
- Double-click spreadsheet filename cell → `SpreadsheetViewerWindow` (POI reads blob → `TableView` per sheet)

**GenerateAllWindow / EmailAllWindow**  
- Three `Label` widgets: Total / Completed / Remaining
- `ListView<String>` at bottom for failures: `"usrID | Fee Earner | Error"`
- Generate (or Email All) button: starts operation on background thread, `ScheduledService` polls `ProgressTracker` every 500 ms, `Platform.runLater()` updates labels and failure list
- On completion: `Alert.INFORMATION` popup
- Exit button: closes window (does not cancel in-progress operation)

**SingleGenerateWindow**  
- `TableView<FeeEarner>` with columns: usrID, Fee Earner, Email, + "Generate" `TableColumn` with `Button` per row
- Button click: validates a prior run exists (`RunRepository.getMostRecent`); if none shows `Alert.WARNING` "A bulk run must be completed first"; otherwise calls `SpreadsheetService.generateForFeeEarner(...)` then shows success `Alert`

**SingleEmailWindow**  
- Same layout as `SingleGenerateWindow` with "Send Email" button per row
- Button click: calls `EmailService.sendForFeeEarner(...)` then shows success `Alert`

### 11.3 UI/service contract
Every button handler calls a service method directly — the service method is identical to what `CliMain` calls. No logic lives in controllers.

---

## 12. Error Handling

- **Bulk loops**: exceptions caught inside each `Callable`; logged at ERROR level; fee earner added to `ProgressTracker.failures`; loop continues
- **Single operations**: exceptions propagate to the UI handler; shown as `Alert.ERROR` popup
- **Bootstrap failures** (cannot connect, cannot load params, invalid log dir): print to `stderr` and `System.exit(1)`
- **Parameter validation**: `ParameterService.validate()` returns a `ValidationResult`; UI checks before writing to DB

---

## 13. Database Scripts

Stored in `src/main/resources/sql/`:

| File | Contents |
|---|---|
| `01_tables.sql` | `report_param`, `spreadsheet_run`, `FeeEarnersRun`, `full_task_archive`, `limitation_archive`, `aged_archive`, `duplicate_task_archive`, `high_volume_archive` |
| `02_partition_functions.sql` | `pf_full_task_archive_weekly`, `pf_limitation_archive_weekly`, `pf_aged_archive_weekly`, `pf_duplicate_task_archive_weekly`, `pf_high_volume_archive_weekly` — each `RANGE LEFT` on `day_run DATE` with weekly boundaries 2026-06-04 through 2028-06-08 |
| `03_partition_schemes.sql` | One scheme per archive table, all partitions on `[PRIMARY]` |
| `04_apply_partitioning.sql` | Clustered index on `day_run` per archive table to activate partitioning |

---

## 14. CLI Entry Point (`Main.java`)

```
args[0] = path to credential file
→ parse credentials
→ bootstrap DB connection
→ ParamRepository.loadAll() → AppConfig
→ LoggingInitialiser.init(logDir, level)
→ build HikariDataSource
→ RunService.startRun() → runId
→ FeeEarnerRepository.getLeadFeeEarners() + getMatterFeeEarners()
→ SpreadsheetService.generateAll(feeEarners, runId, today, config, stdout-progress)
→ RunService.finishRun(runId)
```

Failures printed to stdout via `ProgressTracker`. Exit code 0 on success, 1 on bootstrap failure.
