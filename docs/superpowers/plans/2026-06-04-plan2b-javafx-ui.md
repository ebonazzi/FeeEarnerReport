# Plan 2b — JavaFX UI Layer

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the JavaFX UI layer — nine window classes plus `FxApplication` entry point — giving users a full modal-window GUI that reuses all existing services and repositories without modification.

**Architecture:** Pure programmatic JavaFX (no FXML). Every window is `Modality.APPLICATION_MODAL`. No business logic in window classes — every button handler calls a service or repository method directly, identical to what `Main.java` calls. Background bulk operations run on daemon threads; a `Timeline` (500 ms cycle) polls `ProgressTracker` via `Platform.runLater()` to update labels. The shaded JAR main class is `FxApplication`; `Main.java` remains the CLI entry point.

**Tech Stack:** Java 22 (Corretto), Liberica Full JDK (JavaFX 22.0.2 bundled, `scope: provided`), Apache POI 5.3.0 (`poi-ooxml` — for `SpreadsheetViewerWindow`), JUnit 5.11.4 (existing 35 tests must still pass). Build: `mvn test` (unit), `mvn package` (shaded JAR with main-class `FxApplication`).

**No automated UI tests in this plan.** JavaFX UI tests require a display (TestFX/Monocle not in pom.xml). Verification is: `mvn test` (35 existing tests pass), `mvn package -DskipTests` (shaded JAR builds), manual smoke test.

---

## File Map

**New — repository addition:**
- `src/main/java/net/javalover/feeearner/repository/RunRepository.java` ← two new methods added

**New — entry point:**
- `src/main/java/net/javalover/feeearner/FxApplication.java`

**New — UI windows (all in package `net.javalover.feeearner.ui`):**
- `src/main/java/net/javalover/feeearner/ui/ParameterEditorWindow.java`
- `src/main/java/net/javalover/feeearner/ui/FeeEarnerDetailWindow.java`
- `src/main/java/net/javalover/feeearner/ui/SpreadsheetViewerWindow.java`
- `src/main/java/net/javalover/feeearner/ui/GenerateAllWindow.java`
- `src/main/java/net/javalover/feeearner/ui/EmailAllWindow.java`
- `src/main/java/net/javalover/feeearner/ui/SingleGenerateWindow.java`
- `src/main/java/net/javalover/feeearner/ui/SingleEmailWindow.java`
- `src/main/java/net/javalover/feeearner/ui/PreviousRunsWindow.java`
- `src/main/java/net/javalover/feeearner/ui/MainWindow.java`

**No new test files** (see above).

---

## Wave Ordering (compile-time dependency order)

| Wave | Files | Reason |
|------|-------|--------|
| Wave 0 | `RunRepository.java` (additions) | `PreviousRunsWindow` calls `getAllRuns()` and `getFeeEarnerRunsForRun()` — must exist before the window compiles |
| Wave 1 | `ParameterEditorWindow`, `FeeEarnerDetailWindow`, `SpreadsheetViewerWindow`, `GenerateAllWindow`, `EmailAllWindow`, `SingleGenerateWindow`, `SingleEmailWindow` | Leaf windows — no inter-window dependencies; can be written in parallel |
| Wave 2 | `PreviousRunsWindow` | Opens `FeeEarnerDetailWindow` and `SpreadsheetViewerWindow` — both must exist |
| Wave 2 | `MainWindow` | Opens all nine windows — all must exist |
| Wave 3 | `FxApplication` | Instantiates `MainWindow` with all services/repos — `MainWindow` must exist |

---

## Constructor Signatures Reference

```java
// Wave 1 — leaf windows
ParameterEditorWindow(ParameterService paramSvc)
FeeEarnerDetailWindow(FeeEarnerRun run)
SpreadsheetViewerWindow(FeeEarnerRun run)
GenerateAllWindow(SpreadsheetService spreadsheetSvc, RunService runService,
                  FeeEarnerRepository feeEarnerRepo, AppConfig config)
EmailAllWindow(EmailService emailSvc, RunRepository runRepo,
               FeeEarnerRepository feeEarnerRepo, AppConfig config)
SingleGenerateWindow(SpreadsheetService spreadsheetSvc, RunRepository runRepo,
                     FeeEarnerRepository feeEarnerRepo, AppConfig config)
SingleEmailWindow(EmailService emailSvc, RunRepository runRepo,
                  FeeEarnerRepository feeEarnerRepo, AppConfig config)

// Wave 2
PreviousRunsWindow(RunRepository runRepo)
MainWindow(ParameterService paramSvc, RunRepository runRepo,
           SpreadsheetService spreadsheetSvc, RunService runSvc,
           EmailService emailSvc, FeeEarnerRepository feeEarnerRepo, AppConfig config)

// Wave 3
FxApplication  // services/repos created internally from credential file path
```

---

## Task 0: RunRepository — add getAllRuns() and getFeeEarnerRunsForRun()

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/repository/RunRepository.java`

These two methods are required by `PreviousRunsWindow`. They are pure data-access methods; no service layer is needed.

**Also add `ParameterService.loadAll()`** — required by `ParameterEditorWindow`. `AppConfig.params()` returns `Map<String, String>` (no IDs), so `ParameterEditorWindow` cannot populate `TableView<AppParam>` from `AppConfig`. Add a one-line delegation method to `ParameterService`:

```java
public List<AppParam> loadAll() {
    return paramRepo.loadAll();
}
```

Add `import net.javalover.feeearner.model.AppParam;` and `import java.util.List;` to `ParameterService.java` if not already present.

`getAllRuns()` — reads from `report.spreadsheet_run`, ordered by `started_at DESC`.  
`getFeeEarnerRunsForRun(int runId)` — reads all rows from `report.FeeEarnersRun` for a given `run_id`, ordered by `[Fee Earner]`.

Neither method loads the `excel_spreadsheet` blob (binary column — `PreviousRunsWindow` only needs metadata for the bottom table). The blob is loaded lazily only when a user double-clicks a spreadsheet filename cell, which opens `SpreadsheetViewerWindow` using the already-loaded `FeeEarnerRun` row with its blob. Wait — the bottom `TableView<FeeEarnerRun>` in `PreviousRunsWindow` needs the blob for `SpreadsheetViewerWindow`. Therefore `getFeeEarnerRunsForRun` MUST load the blob. This is acceptable — the method is called once per run selection click.

- [ ] **Step 1: Read `RunRepository.java` fully** (it is already in context from planning — verify `mapRun()` helper is reusable for `getFeeEarnerRunsForRun`)

`mapRun(ResultSet rs)` maps: `run_id`, `day_run`, `usrID`, `[Fee Earner]`, `usrEmail`, `excel_filename`, `excel_spreadsheet`, `stored_at`. This is identical to what `getFeeEarnerRunsForRun` needs.

`getAllRuns()` maps to `RunInfo` record: `runId`, `dayRun`, `startedAt`, `finishedAt` — needs a separate `mapRunInfo(ResultSet rs)` helper.

- [ ] **Step 2: Add `getAllRuns()` to `RunRepository`**

Add after `getMostRecent`:

```java
public List<RunInfo> getAllRuns() {
    var sql = "SELECT run_id, day_run, started_at, finished_at " +
              "FROM report.spreadsheet_run ORDER BY started_at DESC";
    try (var conn = ds.getConnection();
         var stmt = conn.prepareStatement(sql);
         var rs   = stmt.executeQuery()) {
        var result = new ArrayList<RunInfo>();
        while (rs.next()) result.add(mapRunInfo(rs));
        return result;
    } catch (SQLException e) {
        throw new RuntimeException("Failed to load all runs", e);
    }
}
```

- [ ] **Step 3: Add `getFeeEarnerRunsForRun(int runId)` to `RunRepository`**

```java
public List<FeeEarnerRun> getFeeEarnerRunsForRun(int runId) {
    var sql = "SELECT run_id, day_run, usrID, [Fee Earner], usrEmail, " +
              "excel_filename, excel_spreadsheet, stored_at " +
              "FROM report.FeeEarnersRun WHERE run_id = ? " +
              "ORDER BY [Fee Earner]";
    try (var conn = ds.getConnection();
         var stmt = conn.prepareStatement(sql)) {
        stmt.setInt(1, runId);
        try (var rs = stmt.executeQuery()) {
            var result = new ArrayList<FeeEarnerRun>();
            while (rs.next()) result.add(mapRun(rs));
            return result;
        }
    } catch (SQLException e) {
        throw new RuntimeException("Failed to load fee earner runs for runId=" + runId, e);
    }
}
```

- [ ] **Step 4: Add `mapRunInfo(ResultSet rs)` private helper**

```java
private RunInfo mapRunInfo(ResultSet rs) throws SQLException {
    var finishedAt = rs.getTimestamp("finished_at");
    return new RunInfo(
        rs.getInt("run_id"),
        rs.getDate("day_run").toLocalDate(),
        rs.getTimestamp("started_at").toLocalDateTime(),
        finishedAt != null ? finishedAt.toLocalDateTime() : null
    );
}
```

- [ ] **Step 5: Add missing import for `RunInfo` if not already present**

Check imports at the top of `RunRepository.java`. `RunInfo` is in `net.javalover.feeearner.model` — add:
```java
import net.javalover.feeearner.model.RunInfo;
```

- [ ] **Step 6: Compile check + full test suite**

```bash
mvn test -q
```

Expected: BUILD SUCCESS, 35 tests pass, 0 failures. The two new methods have no unit tests (they require a live DB), but they must compile cleanly.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/net/javalover/feeearner/repository/RunRepository.java
git commit -m "feat: add getAllRuns() and getFeeEarnerRunsForRun() to RunRepository"
```

---

## Task 1: Wave 1 — ParameterEditorWindow

**Files:**
- Create: `src/main/java/net/javalover/feeearner/ui/ParameterEditorWindow.java`

**Design (spec §11.2):**
- `Stage` with `Modality.APPLICATION_MODAL`
- `TableView<AppParam>` — two columns: `parameter_name` (non-editable) and `parameter_value` (editable `TextField` cell)
- Save button: for each edited row, calls `paramSvc.validate(name, value)` — if `ValidationResult.isValid()` is false, shows `Alert(Alert.AlertType.ERROR)` with the validation message and returns without saving; if all pass, calls `paramSvc.save(param)` for each edited row, then `paramSvc.reload()`, then closes
- Cancel button: calls `stage.close()` without any service calls
- Window title: "Edit Parameters"
- Window size: 600 × 400

**read_first:** `src/main/java/net/javalover/feeearner/service/ParameterService.java` — confirm method signatures for `validate(String name, String value)`, `save(AppParam param)`, `reload()`.

**Implementation notes:**
- `AppParam` is a record: `(int id, String name, String value, boolean active)`. The editable column binds to `value` via a `TextFieldTableCell`.
- Call `tableView.setEditable(true)` and `valueColumn.setEditable(true)`.
- Use `FXCollections.observableArrayList(paramSvc.load().params())` to populate the table. `AppConfig` exposes the param list — confirm via `AppConfig.java`.
- Track edits: use `valueColumn.setOnEditCommit(event -> ...)` to update a local mutable copy (use a `List<AppParam>` reconstructed from the table items on Save click — read `tableView.getItems()` directly).
- On Save: iterate `tableView.getItems()`, call validate+save for each. Stop on first validation failure (show Alert, do not save any).

- [ ] **Step 1: Implement `ParameterEditorWindow`**

```java
package net.javalover.feeearner.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.*;
import net.javalover.feeearner.model.AppParam;
import net.javalover.feeearner.service.ParameterService;

public class ParameterEditorWindow {

    private final ParameterService paramSvc;

    public ParameterEditorWindow(ParameterService paramSvc) {
        this.paramSvc = paramSvc;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Edit Parameters");

        var table = new TableView<AppParam>();
        table.setEditable(true);

        var nameCol = new TableColumn<AppParam, String>("Parameter Name");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(250);
        nameCol.setEditable(false);

        var valueCol = new TableColumn<AppParam, String>("Value");
        valueCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().value()));
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setPrefWidth(300);
        valueCol.setEditable(true);
        valueCol.setOnEditCommit(event -> {
            var old = event.getRowValue();
            var updated = new AppParam(old.id(), old.name(), event.getNewValue(), old.active());
            table.getItems().set(event.getTablePosition().getRow(), updated);
        });

        table.getColumns().addAll(nameCol, valueCol);
        table.setItems(FXCollections.observableArrayList(paramSvc.loadAll()));

        var saveBtn = new Button("Save");
        var cancelBtn = new Button("Cancel");

        saveBtn.setOnAction(e -> {
            for (var param : table.getItems()) {
                var result = paramSvc.validate(param.name(), param.value());
                if (!result.valid()) {
                    var alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Validation Error");
                    alert.setHeaderText("Invalid value for: " + param.name());
                    alert.setContentText(result.message());
                    alert.showAndWait();
                    return;
                }
            }
            table.getItems().forEach(paramSvc::save);
            paramSvc.reload();
            stage.close();
        });

        cancelBtn.setOnAction(e -> stage.close());

        var buttons = new HBox(10, saveBtn, cancelBtn);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        var root = new VBox(10, table, buttons);
        root.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);

        stage.setScene(new Scene(root, 600, 400));
        stage.showAndWait();
    }
}
```

- [ ] **Step 2: Compile check**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS. Fix any import/method-signature errors before continuing.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/ParameterEditorWindow.java
git commit -m "feat: add ParameterEditorWindow (modal, editable TableView, validate+save)"
```

---

## Task 2: Wave 1 — FeeEarnerDetailWindow and SpreadsheetViewerWindow

**Files:**
- Create: `src/main/java/net/javalover/feeearner/ui/FeeEarnerDetailWindow.java`
- Create: `src/main/java/net/javalover/feeearner/ui/SpreadsheetViewerWindow.java`

### FeeEarnerDetailWindow

**Design (spec §11.2):** Modal window displaying all fields of a `FeeEarnerRun` as read-only label pairs.

Fields to display: Run ID, Day Run, usrID, Fee Earner, Email, Excel Filename, Stored At. Do NOT display the binary blob.

Window title: "Fee Earner Run Detail". Size: 450 × 320.

**Implementation notes:**
- Use a `GridPane` with two columns: bold `Label` (field name) and regular `Label` (value).
- `storedAt` may be null — display empty string if null.
- Close button at the bottom.

- [ ] **Step 1: Implement `FeeEarnerDetailWindow`**

```java
package net.javalover.feeearner.ui;

import javafx.geometry.Insets;
import javafx.geometry.HPos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Font;
import javafx.stage.*;
import net.javalover.feeearner.model.FeeEarnerRun;

public class FeeEarnerDetailWindow {

    private final FeeEarnerRun run;

    public FeeEarnerDetailWindow(FeeEarnerRun run) {
        this.run = run;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Fee Earner Run Detail");

        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));

        String[][] rows = {
            {"Run ID",        String.valueOf(run.runId())},
            {"Day Run",       run.dayRun().toString()},
            {"User ID",       String.valueOf(run.usrID())},
            {"Fee Earner",    run.feeEarner()},
            {"Email",         run.usrEmail()},
            {"Excel Filename",run.excelFilename()},
            {"Stored At",     run.storedAt() != null ? run.storedAt().toString() : ""}
        };

        for (int i = 0; i < rows.length; i++) {
            var keyLabel = new Label(rows[i][0] + ":");
            keyLabel.setFont(Font.font(null, FontWeight.BOLD, 13));
            var valLabel = new Label(rows[i][1]);
            grid.add(keyLabel, 0, i);
            grid.add(valLabel, 1, i);
        }

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setPrefWidth(140);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col0, col1);

        var closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());

        var root = new VBox(16, grid, closeBtn);
        root.setPadding(new Insets(0, 16, 16, 16));

        stage.setScene(new Scene(root, 450, 320));
        stage.showAndWait();
    }
}
```

### SpreadsheetViewerWindow

**Design (spec §11.2):** Reads the `excel_spreadsheet` blob via POI, creates a `TabPane` with one `Tab` per sheet. Each tab contains a `TableView<ObservableList<String>>` — row 0 is the header (creates columns), rows 1+ are data.

Window title: "Spreadsheet Viewer — {excelFilename}". Size: 900 × 600.

**Implementation notes:**
- `new XSSFWorkbook(new ByteArrayInputStream(run.excelSpreadsheet()))` — wrap in try-with-resources.
- For each sheet: read row 0 to get column count and header strings; create a `TableColumn<ObservableList<String>, String>` per column with `cellValueFactory` using the column index captured in a `final int idx` local variable.
- Data rows: for each row index 1..lastRowNum, create an `FXCollections.observableArrayList` of string cell values (use `cell.toString()` or `cell.getStringCellValue()` safely — use `DataFormatter` to handle all cell types uniformly).
- If `run.excelSpreadsheet()` is null or empty, show a `Label("No spreadsheet data available")` instead of the `TabPane`.
- Wrap POI exception in a `RuntimeException` so callers do not need to handle checked IO.

- [ ] **Step 2: Implement `SpreadsheetViewerWindow`**

```java
package net.javalover.feeearner.ui;

import javafx.collections.*;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import net.javalover.feeearner.model.FeeEarnerRun;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class SpreadsheetViewerWindow {

    private final FeeEarnerRun run;

    public SpreadsheetViewerWindow(FeeEarnerRun run) {
        this.run = run;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Spreadsheet Viewer — " + run.excelFilename());

        var content = buildContent();

        var root = new BorderPane(content);
        root.setPadding(new Insets(8));

        stage.setScene(new Scene(root, 900, 600));
        stage.showAndWait();
    }

    private javafx.scene.Node buildContent() {
        if (run.excelSpreadsheet() == null || run.excelSpreadsheet().length == 0) {
            return new Label("No spreadsheet data available");
        }
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(run.excelSpreadsheet()))) {
            var fmt = new DataFormatter();
            var tabPane = new TabPane();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                var sheet = wb.getSheetAt(s);
                var table = new TableView<ObservableList<String>>();
                table.setEditable(false);

                var headerRow = sheet.getRow(0);
                int colCount = headerRow != null ? headerRow.getLastCellNum() : 0;
                for (int c = 0; c < colCount; c++) {
                    final int colIdx = c;
                    String header = headerRow.getCell(c) != null
                        ? fmt.formatCellValue(headerRow.getCell(c)) : "";
                    var col = new TableColumn<ObservableList<String>, String>(header);
                    col.setCellValueFactory(cellData -> {
                        var list = cellData.getValue();
                        return new javafx.beans.property.SimpleStringProperty(
                            colIdx < list.size() ? list.get(colIdx) : "");
                    });
                    col.setPrefWidth(120);
                    table.getColumns().add(col);
                }

                var items = FXCollections.<ObservableList<String>>observableArrayList();
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    var row = sheet.getRow(r);
                    ObservableList<String> rowData = FXCollections.observableArrayList();
                    for (int c = 0; c < colCount; c++) {
                        rowData.add(row != null && row.getCell(c) != null
                            ? fmt.formatCellValue(row.getCell(c)) : "");
                    }
                    items.add(rowData);
                }
                table.setItems(items);

                var tab = new Tab(wb.getSheetName(s), table);
                tab.setClosable(false);
                tabPane.getTabs().add(tab);
            }
            return tabPane;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read spreadsheet", e);
        }
    }
}
```

- [ ] **Step 3: Compile check**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit both windows**

```bash
git add src/main/java/net/javalover/feeearner/ui/FeeEarnerDetailWindow.java \
        src/main/java/net/javalover/feeearner/ui/SpreadsheetViewerWindow.java
git commit -m "feat: add FeeEarnerDetailWindow and SpreadsheetViewerWindow"
```

---

## Task 3: Wave 1 — GenerateAllWindow and EmailAllWindow

**Files:**
- Create: `src/main/java/net/javalover/feeearner/ui/GenerateAllWindow.java`
- Create: `src/main/java/net/javalover/feeearner/ui/EmailAllWindow.java`

**Design (spec §11.2):**
- Three `Label` widgets: "Total: N", "Completed: N", "Remaining: N"
- `ListView<String>` for failures — each entry is `"usrID | Fee Earner | Error"`
- Action button ("Generate All" or "Email All"): starts the operation
- Exit button: `stage.close()` (does not cancel in-progress operation)
- On completion: shows `Alert(Alert.AlertType.INFORMATION)` with summary
- Background thread: `new Thread(...).setDaemon(true)` — fetches fee earners and runs bulk operation
- Polling: `new Timeline(new KeyFrame(Duration.millis(500), e -> updateLabels(tracker, ...)))` — calls `timeline.play()` when operation starts, `timeline.stop()` in the on-completion callback (which itself runs on the FX thread via `Platform.runLater()`)
- Window title: "Generate All Spreadsheets" / "Email All Spreadsheets". Size: 600 × 450.

**Threading pattern (identical for both windows):**

```
[Action button clicked]
  → disable button
  → create ProgressTracker(0)  // total set after fee earner fetch
  → start timeline.play()
  → new Thread(() -> {
        leadFEs = feeEarnerRepo.getLeadFeeEarners()
        matterFEs = feeEarnerRepo.getMatterFeeEarners()
        feeEarners = merge(leadFEs, matterFEs)  // same merge as Main.java
        tracker = new ProgressTracker(feeEarners.size())
        Platform.runLater(() -> totalLabel.setText("Total: " + feeEarners.size()))
        // GenerateAll: runId = runService.startRun(); spreadsheetSvc.generateAll(...)
        // EmailAll: runRepo.getFeeEarnerRunsForRun(most recent runId) → emailSvc.sendAll(...)
        Platform.runLater(() -> {
            timeline.stop()
            updateLabels(tracker, ...)
            showCompletionAlert(tracker)
        })
    }).setDaemon(true); thread.start()
```

**EmailAllWindow extra note:** `EmailAllWindow` does not call `runService`. It calls `runRepo.getAllRuns()` to get the most recent `run_id` (first element in the list, since `getAllRuns()` returns DESC order), then calls `runRepo.getFeeEarnerRunsForRun(latestRunId)` to get the `FeeEarnerRun` list, then calls `emailSvc.sendAll(runs, config, tracker)`. If `getAllRuns()` returns empty, show `Alert.WARNING` "No runs found. Generate spreadsheets first."

**Merge helper:** Both windows use the same merge logic as `Main.java`. Extract it to a package-private static helper in the `ui` package, or duplicate it inline (acceptable — two occurrences only). Inline duplication is simpler; use it.

```java
// Inline merge in both GenerateAllWindow and EmailAllWindow:
private static List<FeeEarner> merge(List<FeeEarner> lead, List<FeeEarner> matter) {
    var result = new ArrayList<>(lead);
    var leadIds = lead.stream().map(FeeEarner::usrID).collect(java.util.stream.Collectors.toSet());
    matter.stream()
        .filter(fe -> !leadIds.contains(fe.usrID()))
        .forEach(result::add);
    return result;
}
```

- [ ] **Step 1: Implement `GenerateAllWindow`**

```java
package net.javalover.feeearner.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.FeeEarnerRepository;
import net.javalover.feeearner.service.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class GenerateAllWindow {

    private final SpreadsheetService spreadsheetSvc;
    private final RunService runService;
    private final FeeEarnerRepository feeEarnerRepo;
    private final AppConfig config;

    public GenerateAllWindow(SpreadsheetService spreadsheetSvc, RunService runService,
                             FeeEarnerRepository feeEarnerRepo, AppConfig config) {
        this.spreadsheetSvc = spreadsheetSvc;
        this.runService      = runService;
        this.feeEarnerRepo   = feeEarnerRepo;
        this.config          = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Generate All Spreadsheets");

        var totalLabel     = new Label("Total: —");
        var completedLabel = new Label("Completed: 0");
        var remainingLabel = new Label("Remaining: —");

        var failureList = new ListView<String>();
        failureList.setItems(FXCollections.observableArrayList());

        var generateBtn = new Button("Generate All");
        var exitBtn     = new Button("Exit");

        exitBtn.setOnAction(e -> stage.close());

        // Mutable reference to tracker so the timeline closure can read it
        final ProgressTracker[] trackerRef = { null };

        var timeline = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            var t = trackerRef[0];
            if (t == null) return;
            int completed = t.completed().get();
            int total     = t.total().get();
            int failed    = t.failed().get();
            completedLabel.setText("Completed: " + (completed + failed));
            remainingLabel.setText("Remaining: " + Math.max(0, total - completed - failed));
            t.failures().forEach(f -> {
                String entry = f.usrID() + " | " + f.feeEarner() + " | " + f.errorMessage();
                if (!failureList.getItems().contains(entry))
                    failureList.getItems().add(entry);
            });
        }));
        timeline.setCycleCount(Animation.INDEFINITE);

        generateBtn.setOnAction(e -> {
            generateBtn.setDisable(true);
            var tracker = new ProgressTracker(0);
            trackerRef[0] = tracker;
            timeline.play();

            var thread = new Thread(() -> {
                try {
                    var leadFEs   = feeEarnerRepo.getLeadFeeEarners();
                    var matterFEs = feeEarnerRepo.getMatterFeeEarners();
                    var feeEarners = merge(leadFEs, matterFEs);

                    tracker.total().set(feeEarners.size());
                    Platform.runLater(() -> totalLabel.setText("Total: " + feeEarners.size()));

                    int runId = runService.startRun();
                    spreadsheetSvc.generateAll(feeEarners, runId, LocalDate.now(), config, tracker);
                    runService.finishRun(runId);
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        var alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setContentText("Generation failed: " + ex.getMessage());
                        alert.showAndWait();
                    });
                } finally {
                    Platform.runLater(() -> {
                        timeline.stop();
                        var t = trackerRef[0];
                        if (t != null) {
                            int completed = t.completed().get();
                            int failed    = t.failed().get();
                            completedLabel.setText("Completed: " + (completed + failed));
                            remainingLabel.setText("Remaining: 0");
                        }
                        var alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Complete");
                        alert.setContentText("Generation complete. Completed: " +
                            (trackerRef[0] != null ? trackerRef[0].completed().get() : 0) +
                            "  Failed: " +
                            (trackerRef[0] != null ? trackerRef[0].failed().get() : 0));
                        alert.showAndWait();
                        generateBtn.setDisable(false);
                    });
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        var statsBox = new HBox(20, totalLabel, completedLabel, remainingLabel);
        var buttons  = new HBox(10, generateBtn, exitBtn);
        var root = new VBox(10, statsBox,
                            new Label("Failures:"), failureList, buttons);
        root.setPadding(new Insets(12));
        VBox.setVgrow(failureList, Priority.ALWAYS);

        stage.setScene(new Scene(root, 600, 450));
        stage.showAndWait();
    }

    private static List<FeeEarner> merge(List<FeeEarner> lead, List<FeeEarner> matter) {
        var result  = new ArrayList<>(lead);
        var leadIds = lead.stream().map(FeeEarner::usrID).collect(Collectors.toSet());
        matter.stream().filter(fe -> !leadIds.contains(fe.usrID())).forEach(result::add);
        return result;
    }
}
```

- [ ] **Step 2: Implement `EmailAllWindow`**

`EmailAllWindow` follows the same layout. Key differences:
- Button label: "Email All"
- Background thread fetches `runRepo.getAllRuns()` — if empty shows `Alert.WARNING` and returns
- Gets `latestRunId = runs.get(0).runId()`, then `runRepo.getFeeEarnerRunsForRun(latestRunId)` → `feeEarnerRuns`
- Sets `tracker.total().set(feeEarnerRuns.size())`
- Calls `emailSvc.sendAll(feeEarnerRuns, config, tracker)`
- Does NOT call `runService` at all

```java
package net.javalover.feeearner.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.repository.FeeEarnerRepository;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.service.EmailService;

public class EmailAllWindow {

    private final EmailService emailSvc;
    private final RunRepository runRepo;
    private final FeeEarnerRepository feeEarnerRepo;
    private final AppConfig config;

    public EmailAllWindow(EmailService emailSvc, RunRepository runRepo,
                          FeeEarnerRepository feeEarnerRepo, AppConfig config) {
        this.emailSvc      = emailSvc;
        this.runRepo       = runRepo;
        this.feeEarnerRepo = feeEarnerRepo;
        this.config        = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Email All Spreadsheets");

        var totalLabel     = new Label("Total: —");
        var completedLabel = new Label("Completed: 0");
        var remainingLabel = new Label("Remaining: —");

        var failureList = new ListView<String>();
        failureList.setItems(FXCollections.observableArrayList());

        var emailBtn = new Button("Email All");
        var exitBtn  = new Button("Exit");
        exitBtn.setOnAction(e -> stage.close());

        final ProgressTracker[] trackerRef = { null };

        var timeline = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            var t = trackerRef[0];
            if (t == null) return;
            int completed = t.completed().get();
            int total     = t.total().get();
            int failed    = t.failed().get();
            completedLabel.setText("Completed: " + (completed + failed));
            remainingLabel.setText("Remaining: " + Math.max(0, total - completed - failed));
            t.failures().forEach(f -> {
                String entry = f.usrID() + " | " + f.feeEarner() + " | " + f.errorMessage();
                if (!failureList.getItems().contains(entry))
                    failureList.getItems().add(entry);
            });
        }));
        timeline.setCycleCount(Animation.INDEFINITE);

        emailBtn.setOnAction(e -> {
            emailBtn.setDisable(true);
            timeline.play();

            var thread = new Thread(() -> {
                try {
                    var allRuns = runRepo.getAllRuns();
                    if (allRuns.isEmpty()) {
                        Platform.runLater(() -> {
                            timeline.stop();
                            var alert = new Alert(Alert.AlertType.WARNING);
                            alert.setTitle("No Runs");
                            alert.setContentText("No runs found. Generate spreadsheets first.");
                            alert.showAndWait();
                            emailBtn.setDisable(false);
                        });
                        return;
                    }
                    int latestRunId   = allRuns.get(0).runId();
                    var feeEarnerRuns = runRepo.getFeeEarnerRunsForRun(latestRunId);
                    var tracker = new ProgressTracker(feeEarnerRuns.size());
                    trackerRef[0] = tracker;
                    Platform.runLater(() -> totalLabel.setText("Total: " + feeEarnerRuns.size()));

                    emailSvc.sendAll(feeEarnerRuns, config, tracker);
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        var alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setContentText("Email sending failed: " + ex.getMessage());
                        alert.showAndWait();
                    });
                } finally {
                    Platform.runLater(() -> {
                        timeline.stop();
                        var t = trackerRef[0];
                        if (t != null) {
                            completedLabel.setText("Completed: " +
                                (t.completed().get() + t.failed().get()));
                            remainingLabel.setText("Remaining: 0");
                        }
                        var alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Complete");
                        alert.setContentText("Email sending complete. Completed: " +
                            (trackerRef[0] != null ? trackerRef[0].completed().get() : 0) +
                            "  Failed: " +
                            (trackerRef[0] != null ? trackerRef[0].failed().get() : 0));
                        alert.showAndWait();
                        emailBtn.setDisable(false);
                    });
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        var statsBox = new HBox(20, totalLabel, completedLabel, remainingLabel);
        var buttons  = new HBox(10, emailBtn, exitBtn);
        var root = new VBox(10, statsBox,
                            new Label("Failures:"), failureList, buttons);
        root.setPadding(new Insets(12));
        VBox.setVgrow(failureList, Priority.ALWAYS);

        stage.setScene(new Scene(root, 600, 450));
        stage.showAndWait();
    }
}
```

- [ ] **Step 3: Compile check**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/GenerateAllWindow.java \
        src/main/java/net/javalover/feeearner/ui/EmailAllWindow.java
git commit -m "feat: add GenerateAllWindow and EmailAllWindow (daemon thread + Timeline polling)"
```

---

## Task 4: Wave 1 — SingleGenerateWindow and SingleEmailWindow

**Files:**
- Create: `src/main/java/net/javalover/feeearner/ui/SingleGenerateWindow.java`
- Create: `src/main/java/net/javalover/feeearner/ui/SingleEmailWindow.java`

**Design (spec §11.2):**
- `TableView<FeeEarner>` with columns: usrID, Fee Earner, Email, and a button column
- Button column label: "Generate" (for `SingleGenerateWindow`) / "Send Email" (for `SingleEmailWindow`)
- Button is a `Button` rendered per row via a custom `TableCell`
- Window title: "Generate Single Spreadsheet" / "Email Single Spreadsheet". Size: 700 × 500.

**SingleGenerateWindow button action:**
1. Get the `FeeEarner` for this row
2. Call `runRepo.getMostRecent(fe.usrID())`
3. If `Optional.isEmpty()`: show `Alert(Alert.AlertType.WARNING)` "A bulk run must be completed first"
4. Otherwise: call `spreadsheetSvc.generateForFeeEarner(fe, mostRecent.runId(), LocalDate.now(), config, event -> {})` — the progress consumer does nothing (single-FE operation is fast)
5. On success: show `Alert(Alert.AlertType.INFORMATION)` "Spreadsheet generated for {feeEarner.feeEarner()}"
6. On exception: show `Alert(Alert.AlertType.ERROR)` with `ex.getMessage()`

**SingleEmailWindow button action:**
1. Get the `FeeEarner` for this row
2. Call `emailSvc.sendForFeeEarner(fe.usrID(), 0, config)` — `runId` is unused in `EmailService.sendForFeeEarner` (it fetches `getMostRecent(usrID)` directly); pass `0` as a sentinel
3. On success: show `Alert(Alert.AlertType.INFORMATION)` "Email sent for {feeEarner.feeEarner()}"
4. On exception: show `Alert(Alert.AlertType.ERROR)` with `ex.getMessage()`

**Populating the table:** Both windows call `feeEarnerRepo.getLeadFeeEarners()` and `feeEarnerRepo.getMatterFeeEarners()` in `show()` before building the scene, then merge (same `merge()` helper, inline).

**Button cell pattern:**

```java
// Reusable pattern for button column
actionCol.setCellFactory(col -> new TableCell<>() {
    private final Button btn = new Button("Generate"); // or "Send Email"
    {
        btn.setOnAction(event -> {
            var fe = getTableView().getItems().get(getIndex());
            handleAction(fe, getScene().getWindow());
        });
    }
    @Override
    protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty ? null : btn);
    }
});
```

- [ ] **Step 1: Implement `SingleGenerateWindow`**

```java
package net.javalover.feeearner.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FeeEarner;
import net.javalover.feeearner.repository.FeeEarnerRepository;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.service.SpreadsheetService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SingleGenerateWindow {

    private final SpreadsheetService spreadsheetSvc;
    private final RunRepository runRepo;
    private final FeeEarnerRepository feeEarnerRepo;
    private final AppConfig config;

    public SingleGenerateWindow(SpreadsheetService spreadsheetSvc, RunRepository runRepo,
                                FeeEarnerRepository feeEarnerRepo, AppConfig config) {
        this.spreadsheetSvc = spreadsheetSvc;
        this.runRepo        = runRepo;
        this.feeEarnerRepo  = feeEarnerRepo;
        this.config         = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Generate Single Spreadsheet");

        var feeEarners = merge(feeEarnerRepo.getLeadFeeEarners(),
                               feeEarnerRepo.getMatterFeeEarners());

        var table = new TableView<FeeEarner>();
        table.setItems(FXCollections.observableArrayList(feeEarners));

        var idCol = new TableColumn<FeeEarner, String>("User ID");
        idCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                String.valueOf(c.getValue().usrID())));
        idCol.setPrefWidth(80);

        var nameCol = new TableColumn<FeeEarner, String>("Fee Earner");
        nameCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().feeEarner()));
        nameCol.setPrefWidth(220);

        var emailCol = new TableColumn<FeeEarner, String>("Email");
        emailCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().usrEmail()));
        emailCol.setPrefWidth(220);

        var actionCol = new TableColumn<FeeEarner, Void>("Action");
        actionCol.setPrefWidth(120);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Generate");
            {
                btn.setOnAction(event -> {
                    var fe = getTableView().getItems().get(getIndex());
                    handleGenerate(fe, stage);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(idCol, nameCol, emailCol, actionCol);

        var closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());

        var root = new VBox(10, table, closeBtn);
        root.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);

        stage.setScene(new Scene(root, 700, 500));
        stage.showAndWait();
    }

    private void handleGenerate(FeeEarner fe, Window owner) {
        var mostRecent = runRepo.getMostRecent(fe.usrID());
        if (mostRecent.isEmpty()) {
            var alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(owner);
            alert.setTitle("No Prior Run");
            alert.setContentText("A bulk run must be completed first.");
            alert.showAndWait();
            return;
        }
        try {
            spreadsheetSvc.generateForFeeEarner(
                fe, mostRecent.get().runId(), LocalDate.now(), config, evt -> {});
            var alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(owner);
            alert.setTitle("Done");
            alert.setContentText("Spreadsheet generated for " + fe.feeEarner());
            alert.showAndWait();
        } catch (Exception ex) {
            var alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(owner);
            alert.setTitle("Error");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    private static List<FeeEarner> merge(List<FeeEarner> lead, List<FeeEarner> matter) {
        var result  = new ArrayList<>(lead);
        var leadIds = lead.stream().map(FeeEarner::usrID).collect(Collectors.toSet());
        matter.stream().filter(fe -> !leadIds.contains(fe.usrID())).forEach(result::add);
        return result;
    }
}
```

- [ ] **Step 2: Implement `SingleEmailWindow`**

Same layout. Key differences: button label "Send Email", action calls `emailSvc.sendForFeeEarner(fe.usrID(), 0, config)`, success message "Email sent for {name}". No `SpreadsheetService`, `runRepo` used only to read (not write). Wait — `SingleEmailWindow` does NOT need `runRepo` for its action (`EmailService.sendForFeeEarner` fetches `getMostRecent` internally). However, the constructor signature specified in the plan prompt includes `RunRepository` — keep it for symmetry and potential future use, but the button action calls only `emailSvc.sendForFeeEarner(...)`.

```java
package net.javalover.feeearner.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FeeEarner;
import net.javalover.feeearner.repository.FeeEarnerRepository;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.service.EmailService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SingleEmailWindow {

    private final EmailService emailSvc;
    private final RunRepository runRepo;
    private final FeeEarnerRepository feeEarnerRepo;
    private final AppConfig config;

    public SingleEmailWindow(EmailService emailSvc, RunRepository runRepo,
                             FeeEarnerRepository feeEarnerRepo, AppConfig config) {
        this.emailSvc      = emailSvc;
        this.runRepo       = runRepo;
        this.feeEarnerRepo = feeEarnerRepo;
        this.config        = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Email Single Spreadsheet");

        var feeEarners = merge(feeEarnerRepo.getLeadFeeEarners(),
                               feeEarnerRepo.getMatterFeeEarners());

        var table = new TableView<FeeEarner>();
        table.setItems(FXCollections.observableArrayList(feeEarners));

        var idCol = new TableColumn<FeeEarner, String>("User ID");
        idCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                String.valueOf(c.getValue().usrID())));
        idCol.setPrefWidth(80);

        var nameCol = new TableColumn<FeeEarner, String>("Fee Earner");
        nameCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().feeEarner()));
        nameCol.setPrefWidth(220);

        var emailCol = new TableColumn<FeeEarner, String>("Email");
        emailCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().usrEmail()));
        emailCol.setPrefWidth(220);

        var actionCol = new TableColumn<FeeEarner, Void>("Action");
        actionCol.setPrefWidth(120);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Send Email");
            {
                btn.setOnAction(event -> {
                    var fe = getTableView().getItems().get(getIndex());
                    handleEmail(fe, stage);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(idCol, nameCol, emailCol, actionCol);

        var closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());

        var root = new VBox(10, table, closeBtn);
        root.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);

        stage.setScene(new Scene(root, 700, 500));
        stage.showAndWait();
    }

    private void handleEmail(FeeEarner fe, Window owner) {
        try {
            emailSvc.sendForFeeEarner(fe.usrID(), 0, config);
            var alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(owner);
            alert.setTitle("Done");
            alert.setContentText("Email sent for " + fe.feeEarner());
            alert.showAndWait();
        } catch (Exception ex) {
            var alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(owner);
            alert.setTitle("Error");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    private static List<FeeEarner> merge(List<FeeEarner> lead, List<FeeEarner> matter) {
        var result  = new ArrayList<>(lead);
        var leadIds = lead.stream().map(FeeEarner::usrID).collect(Collectors.toSet());
        matter.stream().filter(fe -> !leadIds.contains(fe.usrID())).forEach(result::add);
        return result;
    }
}
```

- [ ] **Step 3: Compile check + full test suite**

```bash
mvn test -q
```

Expected: BUILD SUCCESS, 35 tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/SingleGenerateWindow.java \
        src/main/java/net/javalover/feeearner/ui/SingleEmailWindow.java
git commit -m "feat: add SingleGenerateWindow and SingleEmailWindow"
```

---

## Task 5: Wave 2 — PreviousRunsWindow

**Files:**
- Create: `src/main/java/net/javalover/feeearner/ui/PreviousRunsWindow.java`

**Design (spec §11.2):**
- Top `TableView<RunInfo>` — columns: Run ID, Day Run, Started At, Finished At
- Bottom `TableView<FeeEarnerRun>` — columns: usrID, Fee Earner, Email, Excel Filename, Stored At
- Selecting a row in the top table triggers `runRepo.getFeeEarnerRunsForRun(runId)` and populates the bottom table
- Double-clicking a row in the bottom table opens `FeeEarnerDetailWindow`
- Double-clicking the "Excel Filename" cell in the bottom table opens `SpreadsheetViewerWindow`
- Window title: "Previous Runs". Size: 900 × 600.

**Selection listener pattern:**
```java
topTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
    if (newVal != null) {
        var runs = runRepo.getFeeEarnerRunsForRun(newVal.runId());
        bottomItems.setAll(runs);
    }
});
```

**Double-click pattern for bottom table:**
```java
bottomTable.setRowFactory(tv -> {
    var row = new TableRow<FeeEarnerRun>();
    row.setOnMouseClicked(event -> {
        if (event.getClickCount() == 2 && !row.isEmpty()) {
            new FeeEarnerDetailWindow(row.getItem()).show(stage);
        }
    });
    return row;
});
```

**Double-click on filename cell:** Use a custom `TableCell` for the filename column that overrides `setOnMouseClicked` — if `clickCount == 2` and `!isEmpty()` and `getItem() != null`, open `SpreadsheetViewerWindow` with `getTableRow().getItem()` (the `FeeEarnerRun`).

**Initialisation:** Call `runRepo.getAllRuns()` in `show()` before building the scene; populate `topTable` immediately.

- [ ] **Step 1: Implement `PreviousRunsWindow`**

```java
package net.javalover.feeearner.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.RunRepository;

public class PreviousRunsWindow {

    private final RunRepository runRepo;

    public PreviousRunsWindow(RunRepository runRepo) {
        this.runRepo = runRepo;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Previous Runs");

        // --- Top table (RunInfo) ---
        var topTable = new TableView<RunInfo>();
        var runs = FXCollections.observableArrayList(runRepo.getAllRuns());
        topTable.setItems(runs);

        var runIdCol = new TableColumn<RunInfo, String>("Run ID");
        runIdCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                String.valueOf(c.getValue().runId())));
        runIdCol.setPrefWidth(70);

        var dayRunCol = new TableColumn<RunInfo, String>("Day Run");
        dayRunCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().dayRun().toString()));
        dayRunCol.setPrefWidth(100);

        var startedCol = new TableColumn<RunInfo, String>("Started At");
        startedCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().startedAt().toString()));
        startedCol.setPrefWidth(160);

        var finishedCol = new TableColumn<RunInfo, String>("Finished At");
        finishedCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().finishedAt() != null
                    ? c.getValue().finishedAt().toString() : ""));
        finishedCol.setPrefWidth(160);

        topTable.getColumns().addAll(runIdCol, dayRunCol, startedCol, finishedCol);

        // --- Bottom table (FeeEarnerRun) ---
        ObservableList<FeeEarnerRun> bottomItems = FXCollections.observableArrayList();
        var bottomTable = new TableView<FeeEarnerRun>();
        bottomTable.setItems(bottomItems);

        var feIdCol = new TableColumn<FeeEarnerRun, String>("User ID");
        feIdCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                String.valueOf(c.getValue().usrID())));
        feIdCol.setPrefWidth(70);

        var feNameCol = new TableColumn<FeeEarnerRun, String>("Fee Earner");
        feNameCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().feeEarner()));
        feNameCol.setPrefWidth(180);

        var feEmailCol = new TableColumn<FeeEarnerRun, String>("Email");
        feEmailCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().usrEmail()));
        feEmailCol.setPrefWidth(180);

        // Filename column — double-click opens SpreadsheetViewerWindow
        var filenameCol = new TableColumn<FeeEarnerRun, String>("Excel Filename");
        filenameCol.setPrefWidth(200);
        filenameCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().excelFilename()));
        filenameCol.setCellFactory(col -> new TableCell<>() {
            {
                setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !isEmpty() && getTableRow() != null
                            && getTableRow().getItem() != null) {
                        new SpreadsheetViewerWindow(getTableRow().getItem()).show(stage);
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });

        var storedAtCol = new TableColumn<FeeEarnerRun, String>("Stored At");
        storedAtCol.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().storedAt() != null
                    ? c.getValue().storedAt().toString() : ""));
        storedAtCol.setPrefWidth(160);

        bottomTable.getColumns().addAll(feIdCol, feNameCol, feEmailCol, filenameCol, storedAtCol);

        // Double-click on a bottom row → FeeEarnerDetailWindow
        bottomTable.setRowFactory(tv -> {
            var row = new TableRow<FeeEarnerRun>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    new FeeEarnerDetailWindow(row.getItem()).show(stage);
                }
            });
            return row;
        });

        // Top table selection → populate bottom table
        topTable.getSelectionModel().selectedItemProperty()
            .addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    bottomItems.setAll(runRepo.getFeeEarnerRunsForRun(newVal.runId()));
                }
            });

        var closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());

        var topLabel    = new Label("Runs:");
        var bottomLabel = new Label("Fee Earner Spreadsheets (select a run above):");

        var root = new VBox(6,
            topLabel, topTable,
            bottomLabel, bottomTable,
            closeBtn);
        root.setPadding(new Insets(12));
        VBox.setVgrow(topTable, Priority.ALWAYS);
        VBox.setVgrow(bottomTable, Priority.ALWAYS);

        stage.setScene(new Scene(root, 900, 600));
        stage.showAndWait();
    }
}
```

- [ ] **Step 2: Compile check**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/PreviousRunsWindow.java
git commit -m "feat: add PreviousRunsWindow (dual TableView, detail + spreadsheet viewer on double-click)"
```

---

## Task 6: Wave 2 — MainWindow

**Files:**
- Create: `src/main/java/net/javalover/feeearner/ui/MainWindow.java`

**Design (spec §11.1):**
- Empty stage with a top `MenuBar` containing one `Menu` ("Actions") with six `MenuItem`s:
  1. Modify Parameters → `new ParameterEditorWindow(paramSvc).show(primaryStage)`
  2. Show Previous Runs → `new PreviousRunsWindow(runRepo).show(primaryStage)`
  3. Generate All Spreadsheets → `new GenerateAllWindow(spreadsheetSvc, runSvc, feeEarnerRepo, config).show(primaryStage)`
  4. Email All Spreadsheets → `new EmailAllWindow(emailSvc, runRepo, feeEarnerRepo, config).show(primaryStage)`
  5. Generate Single Spreadsheet → `new SingleGenerateWindow(spreadsheetSvc, runRepo, feeEarnerRepo, config).show(primaryStage)`
  6. Email Single Spreadsheet → `new SingleEmailWindow(emailSvc, runRepo, feeEarnerRepo, config).show(primaryStage)`
- Window title: "Fee Earner Report". Size: 600 × 400.
- The `primaryStage` is passed into `show(Stage)`, not constructed here.

**Note on config:** `config` is an `AppConfig` snapshot. After `ParameterEditorWindow` saves and reloads, the `ParameterService.reload()` updates the live `AppConfig` internally. If `MainWindow` stores `config` as a field, it will be stale after a reload. To avoid this issue: pass `paramSvc` and call `paramSvc.load()` inside each menu handler that needs the current config, OR accept that the window config is good for the lifetime of the window (acceptable for this application). The simpler approach: store `config` as a `final` field; it is loaded at FxApplication startup; a user who changes params and wants them applied should restart the app. Document this in code with a comment.

- [ ] **Step 1: Implement `MainWindow`**

```java
package net.javalover.feeearner.ui;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.repository.*;
import net.javalover.feeearner.service.*;

public class MainWindow {

    private final ParameterService paramSvc;
    private final RunRepository runRepo;
    private final SpreadsheetService spreadsheetSvc;
    private final RunService runSvc;
    private final EmailService emailSvc;
    private final FeeEarnerRepository feeEarnerRepo;
    // Config is loaded at startup; changes via ParameterEditorWindow take effect on next app start.
    private final AppConfig config;

    public MainWindow(ParameterService paramSvc, RunRepository runRepo,
                      SpreadsheetService spreadsheetSvc, RunService runSvc,
                      EmailService emailSvc, FeeEarnerRepository feeEarnerRepo,
                      AppConfig config) {
        this.paramSvc       = paramSvc;
        this.runRepo        = runRepo;
        this.spreadsheetSvc = spreadsheetSvc;
        this.runSvc         = runSvc;
        this.emailSvc       = emailSvc;
        this.feeEarnerRepo  = feeEarnerRepo;
        this.config         = config;
    }

    public void show(Stage primaryStage) {
        var modifyParams = new MenuItem("Modify Parameters");
        modifyParams.setOnAction(e ->
            new ParameterEditorWindow(paramSvc).show(primaryStage));

        var showRuns = new MenuItem("Show Previous Runs");
        showRuns.setOnAction(e ->
            new PreviousRunsWindow(runRepo).show(primaryStage));

        var generateAll = new MenuItem("Generate All Spreadsheets");
        generateAll.setOnAction(e ->
            new GenerateAllWindow(spreadsheetSvc, runSvc, feeEarnerRepo, config)
                .show(primaryStage));

        var emailAll = new MenuItem("Email All Spreadsheets");
        emailAll.setOnAction(e ->
            new EmailAllWindow(emailSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));

        var generateSingle = new MenuItem("Generate Single Spreadsheet");
        generateSingle.setOnAction(e ->
            new SingleGenerateWindow(spreadsheetSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));

        var emailSingle = new MenuItem("Email Single Spreadsheet");
        emailSingle.setOnAction(e ->
            new SingleEmailWindow(emailSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));

        var actionsMenu = new Menu("Actions",
            null,
            modifyParams, showRuns, generateAll, emailAll, generateSingle, emailSingle);

        var menuBar = new MenuBar(actionsMenu);

        var root = new BorderPane();
        root.setTop(menuBar);

        primaryStage.setTitle("Fee Earner Report");
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();
    }
}
```

- [ ] **Step 2: Compile check**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/MainWindow.java
git commit -m "feat: add MainWindow with MenuBar (6 menu items, all modal windows)"
```

---

## Task 7: Wave 3 — FxApplication entry point

**Files:**
- Create: `src/main/java/net/javalover/feeearner/FxApplication.java`

**Design (spec §3.1, §4.2, §11):**
FxApplication extends `javafx.application.Application`. The bootstrap sequence is identical to `Main.java` — with one difference: after building all services and loading `AppConfig`, it calls `new MainWindow(...).show(primaryStage)` instead of running the CLI pipeline.

**Credential file argument:** JavaFX applications receive command-line args via `getParameters().getRaw()` (returns `List<String>`). If the list is empty (no args provided), show `Alert(Alert.AlertType.ERROR)` "Usage: FxApplication <credential-file>" and call `Platform.exit()`.

**Bootstrap sequence:**

```
1. getParameters().getRaw().get(0) → credentialFilePath
   → if empty: Alert.ERROR + Platform.exit()
2. CredentialLoader.load(credentialFilePath) → DbCredentials
3. Single DriverManager connection → ParamRepository.loadAll() → AppConfig
4. Connection closed
5. LoggingInitialiser.init(config.logDir(), config.debugLevel())
6. HikariDataSource built from config.threadPoolSize()
7. All repositories instantiated with ds
8. All services instantiated with repositories
9. new MainWindow(paramSvc, runRepo, spreadsheetSvc, runSvc, emailSvc, feeEarnerRepo, config)
       .show(primaryStage)
```

**Bootstrap connection:** Use `DriverManager.getConnection(jdbcUrl, username, password)` for the initial single-use connection (same as `Main.java` would use — note: looking at `Main.java`, it uses HikariCP directly with `trustServerCertificate=true`). For `FxApplication`, use the same HikariCP approach: build a minimal `HikariDataSource` for param loading, then keep it open for all repositories (same as `Main.java` — no separate bootstrap connection needed; build HikariCP once, load params from it, then pass the same `ds` to all repos).

**Exception handling in `start()`:** Wrap the entire bootstrap in a try-catch. On exception, show `Alert(Alert.AlertType.ERROR)` with the message and call `Platform.exit()`.

**`main()` method:** Include `public static void main(String[] args) { launch(args); }` so the shaded JAR's manifest `Main-Class: net.javalover.feeearner.FxApplication` works.

- [ ] **Step 1: Implement `FxApplication`**

```java
package net.javalover.feeearner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.config.CredentialLoader;
import net.javalover.feeearner.email.MailSender;
import net.javalover.feeearner.excel.WorkbookBuilder;
import net.javalover.feeearner.logging.LoggingInitialiser;
import net.javalover.feeearner.repository.*;
import net.javalover.feeearner.service.*;
import net.javalover.feeearner.ui.MainWindow;

public class FxApplication extends Application {

    private HikariDataSource ds;

    @Override
    public void start(Stage primaryStage) {
        var rawArgs = getParameters().getRaw();
        if (rawArgs.isEmpty()) {
            showError("Usage: FxApplication <credential-file>");
            Platform.exit();
            return;
        }

        try {
            var creds = CredentialLoader.load(rawArgs.get(0));

            var hikariCfg = new HikariConfig();
            hikariCfg.setJdbcUrl("jdbc:sqlserver://" + creds.host() + ":" + creds.port()
                + ";databaseName=" + creds.database()
                + ";encrypt=true;trustServerCertificate=true");
            hikariCfg.setUsername(creds.username());
            hikariCfg.setPassword(creds.password());
            hikariCfg.setMaximumPoolSize(10);
            ds = new HikariDataSource(hikariCfg);

            var paramRepo   = new ParamRepository(ds);
            var config      = AppConfig.from(paramRepo.loadAll());

            LoggingInitialiser.init(config.logDir(), config.debugLevel());

            var feeEarnerRepo  = new FeeEarnerRepository(ds);
            var worksheetRepo  = new WorksheetRepository(ds);
            var archiveRepo    = new ArchiveRepository(ds);
            var runRepo        = new RunRepository(ds);

            var runSvc         = new RunService(runRepo);
            var spreadsheetSvc = new SpreadsheetService(
                worksheetRepo, archiveRepo, runRepo, feeEarnerRepo, new WorkbookBuilder());
            var mailSender     = new MailSender(config);
            var emailSvc       = new EmailService(mailSender, runRepo);
            var paramSvc       = new ParameterService(paramRepo);

            new MainWindow(paramSvc, runRepo, spreadsheetSvc, runSvc,
                           emailSvc, feeEarnerRepo, config)
                .show(primaryStage);

        } catch (Exception e) {
            showError("Bootstrap failed: " + e.getMessage());
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        if (ds != null) ds.close();
    }

    private void showError(String message) {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

- [ ] **Step 2: Full test suite + package**

```bash
mvn test -q
```

Expected: BUILD SUCCESS, 35 tests pass, 0 failures.

```bash
mvn package -DskipTests -q
```

Expected: BUILD SUCCESS, shaded JAR produced at `target/FeeEarnerReport-1.0-SNAPSHOT.jar`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/javalover/feeearner/FxApplication.java
git commit -m "feat: add FxApplication (JavaFX entry point, bootstraps all services, launches MainWindow)"
```

---

## Manual Smoke Test

After `mvn package -DskipTests`, run (requires Liberica Full JDK on PATH and a valid credential file):

```bash
java -jar target/FeeEarnerReport-1.0-SNAPSHOT.jar /path/to/credentials.properties
```

Checklist:
- [ ] Main window opens with "Actions" menu
- [ ] "Modify Parameters" → modal editor opens, table shows params, Save validates, Cancel closes
- [ ] "Show Previous Runs" → top table loads runs; clicking a run populates bottom table; double-click row opens detail; double-click filename opens spreadsheet viewer
- [ ] "Generate All Spreadsheets" → progress labels update every 500ms; failure list populates on errors; completion alert shows
- [ ] "Email All Spreadsheets" → same pattern; warns if no prior run exists
- [ ] "Generate Single Spreadsheet" → table shows fee earners; "Generate" button warns if no prior run; generates and shows success
- [ ] "Email Single Spreadsheet" → "Send Email" button sends and shows success or error
- [ ] No args → Alert.ERROR "Usage: FxApplication <credential-file>"
- [ ] Closing main window calls `FxApplication.stop()`, HikariCP pool shuts down cleanly

---

## Plan 2b Completion Checklist

- [ ] `mvn test -q` → BUILD SUCCESS, 35 tests pass, 0 failures
- [ ] `mvn package -DskipTests -q` → shaded JAR builds, `Main-Class` is `FxApplication`
- [ ] All 9 window classes compiled and in `net.javalover.feeearner.ui`
- [ ] `FxApplication.java` in root `net.javalover.feeearner` package
- [ ] `RunRepository` has `getAllRuns()` and `getFeeEarnerRunsForRun(int runId)` — both compile
- [ ] All windows use `Modality.APPLICATION_MODAL`
- [ ] No business logic in window classes — every action delegates to a service or repository method
- [ ] `GenerateAllWindow` and `EmailAllWindow` use daemon thread + `Timeline(500ms)`
- [ ] `ProgressTracker` polling uses `Platform.runLater()`
- [ ] `SpreadsheetViewerWindow` uses `DataFormatter` for all cell types (handles dates, numbers, strings uniformly)
- [ ] `ParameterEditorWindow` validates before saving; blocks on first validation failure
- [ ] `SingleGenerateWindow` warns if no prior run exists (guards via `getMostRecent`)
- [ ] `FxApplication.stop()` closes `HikariDataSource`
- [ ] Existing 35 tests still pass (no regression)

---

## Next: Plan 3 (if applicable)

Integration testing, native image build via gluonfx-maven-plugin, or production deployment packaging.
