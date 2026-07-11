# Single-Spreadsheet Progress Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the "Generate Single Spreadsheet" and "Generate Single Past Spreadsheet" windows a modal Cancel/Dismiss progress dialog so the user gets visible feedback during a long-running generate/regenerate, instead of the UI freezing silently; also fix the "Generate Single Past Spreadsheets" menu label to singular.

**Architecture:** A new reusable `GenerationProgressDialog` (JavaFX) opens a small `WINDOW_MODAL` `Stage` over the calling window, starts the actual generation on a background daemon `Thread`, and flips from an in-progress state (Cancel enabled, Dismiss disabled) to a terminal state (Cancel disabled, Dismiss enabled, message shows success or failure) when that thread finishes — driven by `Platform.runLater`. Cancel is UI-only: it just closes the dialog early; the background thread is never interrupted and always finishes and persists its result. Each calling window (`SingleGenerateWindow`, `GenerateSinglePastWindow`) tracks in-flight `usrID`s in a `Set<Integer>` field and disables that row's action button until the background work actually completes (via the dialog's `onWorkComplete` callback), preventing a second concurrent generation for the same fee earner from racing the first on the same DB `run_id`. This fully replaces the existing completion `Alert` in both windows; the pre-flight "No Prior Run" `Alert` in `SingleGenerateWindow` is unchanged.

**Tech Stack:** Java 25, JavaFX (`provided` scope, Liberica Full JDK required to *run* `FxApplication`, but `mvn compile` pulls `org.openjfx:*` from Maven Central regardless of the JDK in use), no TestFX/Mockito in this repo — UI code is compiled and manually verified, not unit tested.

## Global Constraints

- No changes to `SpreadsheetService` method signatures (`generateForFeeEarner`, `generateFromArchive` are called exactly as today).
- No changes to `GenerateAllWindow`/`GenerateAllPastWindow` or their `ProgressTracker`/`Timeline` pattern.
- Dialog modality: `Modality.WINDOW_MODAL` over the owning grid window (not `APPLICATION_MODAL`).
- Cancel is UI-only — never interrupts the background thread. This is a deliberate scope limit per the approved spec, not an oversight.
- Failure is shown in-dialog (message text + Dismiss), never as a separate `Alert`.
- Window-close button (X) behaves like Cancel while in progress, like Dismiss once complete.
- A row's action button stays disabled until the background work actually finishes, not just until the dialog closes.
- No new unit tests — `GenerationProgressDialog` is UI/threading glue and `SpreadsheetService` is unchanged, matching the approved spec's Testing section. Verification is `mvn compile` (syntax/type correctness) plus a manual GUI checklist.

---

### Task 1: Fix "Generate Single Past Spreadsheets" menu label

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/ui/MainWindow.java:59`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing consumed by later tasks — purely a string literal fix, independent of Tasks 2-4.

- [ ] **Step 1: Change the menu item label**

In `MainWindow.java`, line 59, change:

```java
        var generateSinglePast = new MenuItem("Generate Single Past Spreadsheets");
```

to:

```java
        var generateSinglePast = new MenuItem("Generate Single Past Spreadsheet");
```

(Only the string literal changes — the variable name, `setOnAction`, and everything else on the surrounding lines stay as-is.)

- [ ] **Step 2: Compile to verify**

Run: `mvn compile -q`
Expected: exits 0, no output (no compile errors).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/MainWindow.java
git commit -m "fix: correct Generate Single Past Spreadsheet menu label to singular"
```

---

### Task 2: Create `GenerationProgressDialog`

**Files:**
- Create: `src/main/java/net/javalover/feeearner/ui/GenerationProgressDialog.java`

**Interfaces:**
- Consumes: nothing new — only JavaFX (`javafx.application.Platform`, `javafx.scene.*`, `javafx.stage.*`) and `java.util.concurrent.atomic.AtomicBoolean`.
- Produces (consumed by Tasks 3 and 4):
  - `public enum GenerationProgressDialog.Mode { GENERATE, REGENERATE }`
  - `public interface GenerationProgressDialog.Work { void run() throws Exception; }`
  - `public static void GenerationProgressDialog.run(Window owner, Mode mode, String feeEarnerName, Work work, Runnable onWorkComplete)`

No automated test for this task: it is pure UI/threading glue with no branchable pure logic to isolate (per the approved spec's Testing section) — the task ends with a compile check instead of a JUnit cycle. Manual behavior verification happens once it's wired into a real window in Tasks 3 and 4.

- [ ] **Step 1: Create `GenerationProgressDialog.java`**

Create `src/main/java/net/javalover/feeearner/ui/GenerationProgressDialog.java`:

```java
package net.javalover.feeearner.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modal Cancel/Dismiss progress dialog for a single-fee-earner generate/regenerate. Cancel is
 * UI-only: it closes the dialog immediately but never interrupts the background work, which
 * always runs to completion and persists its result. {@code onWorkComplete} always fires when
 * the background work finishes, regardless of whether the dialog was already cancelled/closed,
 * so callers can reliably clear per-row "in flight" state.
 */
public final class GenerationProgressDialog {

    public enum Mode {
        GENERATE("Generating", "generate", "generated"),
        REGENERATE("Regenerating", "regenerate", "regenerated");

        final String gerund;
        final String base;
        final String past;

        Mode(String gerund, String base, String past) {
            this.gerund = gerund;
            this.base = base;
            this.past = past;
        }
    }

    @FunctionalInterface
    public interface Work {
        void run() throws Exception;
    }

    private GenerationProgressDialog() {
    }

    public static void run(Window owner, Mode mode, String feeEarnerName,
                            Work work, Runnable onWorkComplete) {
        var stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        stage.setResizable(false);
        stage.setTitle(mode.gerund + " Spreadsheet");

        var message = new Label(mode.gerund + " Spreadsheet");
        message.setWrapText(true);
        message.setMaxWidth(320);

        var cancelBtn = new Button("Cancel");
        var dismissBtn = new Button("Dismiss");
        dismissBtn.setDisable(true);

        var buttons = new HBox(8, cancelBtn, dismissBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        var root = new VBox(12, message, buttons);
        root.setPadding(new Insets(16));
        root.setPrefWidth(360);

        var cancelled = new AtomicBoolean(false);

        cancelBtn.setOnAction(e -> {
            cancelled.set(true);
            stage.close();
        });
        dismissBtn.setOnAction(e -> stage.close());

        stage.setOnCloseRequest(e -> {
            if (dismissBtn.isDisabled()) {
                // Still in progress: X acts like Cancel.
                cancelled.set(true);
            }
            // Already complete: X acts like Dismiss — default close behavior is correct as-is.
        });

        stage.setOnShown(e -> {
            stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
            stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
        });

        stage.setScene(new Scene(root));

        var thread = new Thread(() -> {
            Exception failure = null;
            try {
                work.run();
            } catch (Exception ex) {
                failure = ex;
            }
            var finalFailure = failure;
            Platform.runLater(() -> {
                onWorkComplete.run();
                if (cancelled.get()) {
                    return;
                }
                if (finalFailure == null) {
                    message.setText("Spreadsheet " + mode.past + " for " + feeEarnerName);
                } else {
                    var errorText = finalFailure.getMessage() != null
                            ? finalFailure.getMessage()
                            : finalFailure.getClass().getSimpleName();
                    message.setText("Failed to " + mode.base + " spreadsheet for "
                            + feeEarnerName + ": " + errorText);
                }
                cancelBtn.setDisable(true);
                dismissBtn.setDisable(false);
            });
        }, "generation-progress-" + mode.name().toLowerCase());
        thread.setDaemon(true);
        thread.start();

        stage.showAndWait();
    }
}
```

- [ ] **Step 2: Compile to verify**

Run: `mvn compile -q`
Expected: exits 0, no output.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/GenerationProgressDialog.java
git commit -m "feat: add GenerationProgressDialog for single-spreadsheet generate/regenerate"
```

---

### Task 3: Wire `GenerationProgressDialog` into `SingleGenerateWindow`

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/ui/SingleGenerateWindow.java`

**Interfaces:**
- Consumes: `GenerationProgressDialog.run(Window, Mode, String, Work, Runnable)` and `GenerationProgressDialog.Mode.GENERATE` (Task 2). `FeeEarner.usrID()`, `FeeEarner.feeEarner()` (existing). `SpreadsheetService.generateForFeeEarner(FeeEarner, long, LocalDate, AppConfig, ...)` (existing, unchanged signature).
- Produces: nothing consumed by later tasks — this window is a leaf.

- [ ] **Step 1: Add imports and instance fields**

In `SingleGenerateWindow.java`, add two imports after the existing `java.util.stream.Collectors` import:

```java
import java.util.HashSet;
import java.util.Set;
```

Add two fields after the existing `config` field (before the constructor):

```java
    private final AppConfig config;
    private TableView<FeeEarner> table;
    private final Set<Integer> inFlightUsrIds = new HashSet<>();
```

(Only `table` and `inFlightUsrIds` are new; `config` already exists — this shows where the new fields go relative to it.)

- [ ] **Step 2: Make `table` an instance field instead of a local variable**

In `show(Window owner)`, change:

```java
        var table = buildTable(stage);
```

to:

```java
        table = buildTable(stage);
```

- [ ] **Step 3: Disable the row button while its fee earner is in-flight**

In `buildTable(Stage stage)`, replace the action column's cell factory:

```java
        var actionCol = new TableColumn<FeeEarner, Void>("Action");
        actionCol.setPrefWidth(120);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Generate");
            {
                btn.setOnAction(event -> {
                    var fe = getTableView().getItems().get(getIndex());
                    handleGenerate(fe, getScene().getWindow());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
```

with:

```java
        var actionCol = new TableColumn<FeeEarner, Void>("Action");
        actionCol.setPrefWidth(120);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Generate");
            {
                btn.setOnAction(event -> {
                    var fe = getTableView().getItems().get(getIndex());
                    handleGenerate(fe, getScene().getWindow());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    var fe = getTableView().getItems().get(getIndex());
                    btn.setDisable(inFlightUsrIds.contains(fe.usrID()));
                    setGraphic(btn);
                }
            }
        });
```

- [ ] **Step 4: Replace `handleGenerate` to use the progress dialog**

Replace the entire `handleGenerate` method:

```java
    private void handleGenerate(FeeEarner fe, Window owner) {
        var mostRecent = runRepo.getMostRecent(fe.usrID());
        if (mostRecent.isEmpty()) {
            showAlert(owner, Alert.AlertType.WARNING,
                      "No Prior Run",
                      "A bulk run must be completed first.");
            return;
        }
        try {
            spreadsheetSvc.generateForFeeEarner(
                    fe,
                    mostRecent.get().runId(),
                    LocalDate.now(),
                    config,
                    evt -> {});
            showAlert(owner, Alert.AlertType.INFORMATION,
                      "Success",
                      "Spreadsheet generated for " + fe.feeEarner());
        } catch (Exception ex) {
            showAlert(owner, Alert.AlertType.ERROR,
                      "Error",
                      ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }
```

with:

```java
    private void handleGenerate(FeeEarner fe, Window owner) {
        var mostRecent = runRepo.getMostRecent(fe.usrID());
        if (mostRecent.isEmpty()) {
            showAlert(owner, Alert.AlertType.WARNING,
                      "No Prior Run",
                      "A bulk run must be completed first.");
            return;
        }
        inFlightUsrIds.add(fe.usrID());
        table.refresh();
        GenerationProgressDialog.run(owner, GenerationProgressDialog.Mode.GENERATE, fe.feeEarner(),
            () -> spreadsheetSvc.generateForFeeEarner(
                    fe, mostRecent.get().runId(), LocalDate.now(), config, evt -> {}),
            () -> {
                inFlightUsrIds.remove(fe.usrID());
                table.refresh();
            });
    }
```

`showAlert` stays in the file unchanged — it's still used by the "No Prior Run" warning above.

- [ ] **Step 5: Compile to verify**

Run: `mvn compile -q`
Expected: exits 0, no output.

- [ ] **Step 6: Manual verification**

Requires a running SQL Server the app can reach and a completed prior bulk run for at least one fee earner (see `CLAUDE.md` for how to run `FxApplication` with a Liberica Full JDK). Launch `FxApplication`, open "Spreadsheet Generation" → "Generate Single Spreadsheet":

1. Click "Generate" on a fee earner row that has a prior run. Confirm the dialog appears centered over the main window, titled/messaged "Generating Spreadsheet", Cancel enabled, Dismiss disabled — and that row's own button is now disabled.
2. Wait for completion. Confirm the message changes to "Spreadsheet generated for <name>", Cancel disables, Dismiss enables.
3. Click Dismiss. Confirm the dialog closes and that row's button re-enables.
4. Click Generate again, then immediately click Cancel. Confirm the dialog closes right away, the row's button stays disabled, and (after the background work actually finishes, observable via the app log) the row's button re-enables on its own with no further clicks.
5. Click Generate again, then click the dialog's window-close (X) button while still in progress. Confirm this behaves identically to Cancel (step 4).
6. Click Generate on a fee earner with **no** prior run. Confirm the existing "No Prior Run" `Alert` still appears and no progress dialog opens.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/SingleGenerateWindow.java
git commit -m "feat: show progress dialog when generating a single spreadsheet"
```

---

### Task 4: Wire `GenerationProgressDialog` into `GenerateSinglePastWindow`

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/ui/GenerateSinglePastWindow.java`

**Interfaces:**
- Consumes: `GenerationProgressDialog.run(Window, Mode, String, Work, Runnable)` and `GenerationProgressDialog.Mode.REGENERATE` (Task 2). `FeeEarnerRun.usrID()`, `FeeEarnerRun.runId()`, `FeeEarnerRun.feeEarner()` (existing). `SpreadsheetService.generateFromArchive(int, long, AppConfig)` (existing, unchanged signature).
- Produces: nothing consumed by later tasks — this window is a leaf.

- [ ] **Step 1: Add imports and instance fields**

In `GenerateSinglePastWindow.java`, add two imports after the existing `net.javalover.feeearner.service.SpreadsheetService` import:

```java
import java.util.HashSet;
import java.util.Set;
```

Add two fields after the existing `config` field (before the constructor):

```java
    private final AppConfig config;
    private TableView<FeeEarnerRun> feTable;
    private final Set<Integer> inFlightUsrIds = new HashSet<>();
```

(Only `feTable` and `inFlightUsrIds` are new; `config` already exists — this shows where the new fields go relative to it.)

- [ ] **Step 2: Make `feTable` an instance field instead of a local variable**

In `show(Window owner)`, change:

```java
        var feTable = buildFeeEarnerTable(stage);
```

to:

```java
        feTable = buildFeeEarnerTable(stage);
```

Everything else in `show(...)` that already references `feTable` (the `runTable` selection listener, the root `VBox`, `VBox.setVgrow(feTable, ...)`) keeps working unchanged since it now resolves to the field instead of the local variable.

- [ ] **Step 3: Disable the row button while its fee earner is in-flight**

In `buildFeeEarnerTable(Stage stage)`, replace the action column's cell factory:

```java
        var actionCol = new TableColumn<FeeEarnerRun, Void>("Action");
        actionCol.setPrefWidth(140);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Regenerate");
            {
                btn.setOnAction(event -> {
                    var fer = getTableView().getItems().get(getIndex());
                    handleRegenerate(fer, getScene().getWindow());
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
```

with:

```java
        var actionCol = new TableColumn<FeeEarnerRun, Void>("Action");
        actionCol.setPrefWidth(140);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Regenerate");
            {
                btn.setOnAction(event -> {
                    var fer = getTableView().getItems().get(getIndex());
                    handleRegenerate(fer, getScene().getWindow());
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    var fer = getTableView().getItems().get(getIndex());
                    btn.setDisable(inFlightUsrIds.contains(fer.usrID()));
                    setGraphic(btn);
                }
            }
        });
```

- [ ] **Step 4: Replace `handleRegenerate` to use the progress dialog, and remove now-dead `showAlert`**

Replace the entire `handleRegenerate` method and the `showAlert` method below it:

```java
    private void handleRegenerate(FeeEarnerRun fer, Window owner) {
        try {
            spreadsheetSvc.generateFromArchive(fer.usrID(), fer.runId(), config);
            showAlert(owner, Alert.AlertType.INFORMATION, "Success",
                "Regenerated spreadsheet for " + fer.feeEarner());
        } catch (Exception ex) {
            showAlert(owner, Alert.AlertType.ERROR, "Error",
                ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private static void showAlert(Window owner, Alert.AlertType type, String title, String msg) {
        var alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
```

with:

```java
    private void handleRegenerate(FeeEarnerRun fer, Window owner) {
        inFlightUsrIds.add(fer.usrID());
        feTable.refresh();
        GenerationProgressDialog.run(owner, GenerationProgressDialog.Mode.REGENERATE, fer.feeEarner(),
            () -> spreadsheetSvc.generateFromArchive(fer.usrID(), fer.runId(), config),
            () -> {
                inFlightUsrIds.remove(fer.usrID());
                feTable.refresh();
            });
    }
```

(`showAlert` had exactly these two call sites in this class, both now replaced, so it is deleted rather than left as dead code.)

- [ ] **Step 5: Compile to verify**

Run: `mvn compile -q`
Expected: exits 0, no output.

- [ ] **Step 6: Manual verification**

Requires the same SQL Server access as Task 3, plus at least one past run visible under "Previous Runs". Launch `FxApplication`, open "Spreadsheet Generation" → "Generate Single Past Spreadsheet":

1. Select a past run in the top table, then a fee earner row in the bottom table. Click "Regenerate". Confirm the dialog appears centered over the main window, titled/messaged "Regenerating Spreadsheet", Cancel enabled, Dismiss disabled — and that row's own button is now disabled.
2. Wait for completion. Confirm the message changes to "Spreadsheet regenerated for <name>", Cancel disables, Dismiss enables.
3. Click Dismiss. Confirm the dialog closes and that row's button re-enables.
4. Click Regenerate again, then immediately click Cancel. Confirm the dialog closes right away, the row's button stays disabled, and (after the background work actually finishes, observable via the app log) the row's button re-enables on its own with no further clicks.
5. Click Regenerate again, then click the dialog's window-close (X) button while still in progress. Confirm this behaves identically to Cancel (step 4).
6. Select a different past run in the top table while a regeneration for a fee earner in the *previous* run is still in-flight, then select back to the original run. Confirm that fee earner's row still shows a disabled button (in-flight tracking survives `feTable`'s item list being replaced).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/GenerateSinglePastWindow.java
git commit -m "feat: show progress dialog when regenerating a single past spreadsheet"
```

---

## Self-Review Notes

- **Spec coverage:** §1 (menu label fix) → Task 1. §2 (`GenerationProgressDialog` component, layout/lifecycle, button/close behavior, completion transitions) → Task 2. §3 (`SingleGenerateWindow` call-site changes) → Task 3. §3 (`GenerateSinglePastWindow` call-site changes, including `showAlert` removal) → Task 4. §4 (why Cancel is UI-only) is realized by Task 2's `cancelled` flag never touching the background thread, and by Tasks 3-4's `inFlightUsrIds` row-disable logic — no separate task needed, it's the mechanism itself. §5 (testing) — no unit tests per spec; manual checklists folded into Task 3 Step 6 and Task 4 Step 6, covering all six spec checklist items (menu label, full generate cycle, full regenerate cycle, cancel-then-real-completion, X-button-as-cancel, plus Task 3's no-prior-run check and Task 4's run-switch check as this plan's concrete stand-ins for the spec's generic "confirm X behaves like Cancel" step).
- **Placeholder scan:** none found — every step has literal, complete code; no "TBD"/"similar to Task N"/prose-only steps.
- **Type consistency:** `GenerationProgressDialog.run(Window owner, Mode mode, String feeEarnerName, Work work, Runnable onWorkComplete)` defined in Task 2 is called identically (positionally and by type) in Task 3 and Task 4. `Mode.GENERATE`/`Mode.REGENERATE` match the two call sites exactly. `inFlightUsrIds` is `Set<Integer>` in both Task 3 and Task 4, matching `FeeEarner.usrID()`/`FeeEarnerRun.usrID()` both returning `int` (autoboxed on `.add`/`.contains`, consistent with existing usage elsewhere in the codebase).
