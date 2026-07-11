# Design: progress dialog for single spreadsheet Generate/Regenerate

**Date:** 2026-07-11
**Status:** Approved — ready for implementation planning

## Context

Under "Spreadsheet Generation", two windows let the user act on a single fee earner:
`SingleGenerateWindow` ("Generate Single Spreadsheet") and `GenerateSinglePastWindow`
("Generate Single Past Spreadsheets" — mislabeled plural, see §1). Each shows a
`TableView` with an "Action" column containing a `Generate` / `Regenerate` button per row.

Today, `SingleGenerateWindow.handleGenerate` and `GenerateSinglePastWindow.handleRegenerate`
call `spreadsheetSvc.generateForFeeEarner(...)` / `generateFromArchive(...)` **synchronously on
the JavaFX Application Thread**, directly inside the button's `onAction` handler, then show a
plain `Alert` on success/error. Because generation involves SQL Server reads, workbook
construction, and an archive-table transaction, this freezes the UI for the duration with no
indication that anything is happening.

Neither `generateForFeeEarner` nor `generateFromArchive` supports cancellation — there are no
interrupt checks, cancellable statements, or timeouts anywhere in `SpreadsheetService.doGenerate`/
`persist`, `WorksheetRepository`, or `ArchiveRepository`. This is confirmed unchanged by this
design; "Cancel" in the new UI is UI-only (see §4).

## Goals

- Give visible feedback the moment Generate/Regenerate is clicked, and again when the work
  finishes, without freezing the UI in between.
- Let the user back out of watching a long-running generation (Cancel) without corrupting or
  duplicating the underlying work.
- Fix the "Generate Single Past Spreadsheets" menu label to match its already-singular window
  title.
- Reuse one component across both windows rather than duplicating dialog/threading logic a third
  and fourth time (the existing bulk windows already duplicate a `Thread` + `Timeline` pattern
  twice).

## Non-goals

- No real cancellation of in-flight SQL/Excel work — out of scope; would require plumbing
  interrupt/cancellation support into `WorksheetRepository`/`ArchiveRepository`/`doGenerate`,
  which does not exist today.
- No change to `GenerateAllWindow`/`GenerateAllPastWindow` (bulk flows) or their
  `ProgressTracker`/`Timeline` pattern.
- No change to `SpreadsheetService` method signatures.

---

## 1. Menu label fix

`MainWindow.java:59` — `"Generate Single Past Spreadsheets"` → `"Generate Single Past Spreadsheet"`.
This is a text-only change; `GenerateSinglePastWindow`'s own `stage.setTitle(...)` (line 38) is
already singular and unchanged.

---

## 2. New component: `GenerationProgressDialog`

New class `ui/GenerationProgressDialog.java`. Encapsulates the modal dialog, the background
thread, and the state-machine transition between "in progress" and "done" — used identically by
both windows.

```java
public final class GenerationProgressDialog {

    public enum Mode {
        GENERATE("Generating", "generate", "generated"),
        REGENERATE("Regenerating", "regenerate", "regenerated");

        final String gerund;   // "Generating" / "Regenerating" — dialog title + in-progress message
        final String base;     // "generate" / "regenerate" — used in the failure message
        final String past;     // "generated" / "regenerated" — used in the success message
    }

    @FunctionalInterface
    public interface Work {
        void run() throws Exception;
    }

    private GenerationProgressDialog() {}

    /**
     * Opens the modal dialog, runs {@code work} on a background daemon thread, and updates the
     * dialog in place when it finishes. {@code onWorkComplete} always fires on the FX thread when
     * the background work finishes, regardless of whether the user already cancelled/dismissed
     * the dialog — callers use it to clear per-row in-flight tracking.
     */
    public static void run(Window owner, Mode mode, String feeEarnerName,
                            Work work, Runnable onWorkComplete) { ... }
}
```

### Layout and lifecycle

- `Stage`, `initModality(Modality.WINDOW_MODAL)`, `initOwner(owner)`, not resizable. Title and
  initial message both read `mode.gerund + " Spreadsheet"` (e.g. "Generating Spreadsheet").
- `VBox` root: a wrapped `Label` for the message, below it an `HBox` with `Cancel` (enabled) and
  `Dismiss` (disabled) buttons, right-aligned.
- Centered over the owner window: on `setOnShown`, `stage.setX(owner.getX() + (owner.getWidth() -
  stage.getWidth()) / 2)` and the equivalent for Y (owner size/position are already known at this
  point; dialog size is fixed, not content-dependent).
- A background daemon `Thread` starts immediately, running `work.run()` inside a try/catch that
  captures any `Exception`. This mirrors the existing bulk-window pattern (background `Thread` +
  `Platform.runLater`), minus the `Timeline` polling, since there's a single item with only a
  start/end state, not incremental progress.
- An `AtomicBoolean cancelled` flag, initially `false`.

### Button and close behavior

- **Cancel** (enabled only while in progress): sets `cancelled = true`, closes the stage
  immediately. The background thread is *not* interrupted — it keeps running to completion; its
  eventual outcome (success or exception) is not shown anywhere and relies on whatever logging
  `SpreadsheetService`/callers already do (no new logging added by this dialog).
- **Dismiss** (enabled only after completion): closes the stage. No other effect — by the time
  it's enabled, `onWorkComplete` has already fired.
- **Window close button (X)**: `setOnCloseRequest` — if not yet completed, behaves exactly like
  Cancel (`cancelled = true`, allow close); if already completed, behaves like Dismiss (just
  closes; no-op since the terminal state was already reached).
- **On background completion** (`Platform.runLater`, always runs regardless of `cancelled`):
  1. Call `onWorkComplete.run()` unconditionally — this is how the parent window clears its
     per-row "in flight" state even if the dialog was already cancelled/closed.
  2. If `cancelled` is `true`, stop here — the dialog is already closed, nothing left to update.
  3. Otherwise transition to the terminal state: on success, message becomes
     `"Spreadsheet " + mode.past + " for " + feeEarnerName"`; on failure, message becomes
     `"Failed to " + mode.base + " spreadsheet for " + feeEarnerName + ": " + errorText"` (where
     `errorText` is `ex.getMessage()` if non-null, else `ex.getClass().getSimpleName()`, matching
     the existing `showAlert` error-text convention). Either way, disable `Cancel`, enable
     `Dismiss`.

This dialog fully replaces the existing success/error `Alert` for these two flows — no separate
`Alert` popup on completion.

---

## 3. Call-site changes

### `SingleGenerateWindow`

- `table` becomes an instance field (was a local var in `show()`), so `handleGenerate` and the
  cell factory can both reach it.
- New field: `private final Set<Integer> inFlightUsrIds = new HashSet<>();`
- Action column's `updateItem` additionally disables the button when
  `inFlightUsrIds.contains(fe.usrID())`:
  ```java
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
  ```
- `handleGenerate` — the "No Prior Run" pre-flight check stays exactly as today (plain `Alert`,
  synchronous, no work started yet). Once past that check:
  ```java
  inFlightUsrIds.add(fe.usrID());
  table.refresh();
  GenerationProgressDialog.run(owner, GenerationProgressDialog.Mode.GENERATE, fe.feeEarner(),
      () -> spreadsheetSvc.generateForFeeEarner(
              fe, mostRecent.get().runId(), LocalDate.now(), config, evt -> {}),
      () -> {
          inFlightUsrIds.remove(fe.usrID());
          table.refresh();
      });
  ```
- `showAlert` is retained (still used by the "No Prior Run" warning).

### `GenerateSinglePastWindow`

Same shape: `feTable` becomes an instance field, `inFlightUsrIds` field added, `updateItem`
disables the row button when in-flight, and `handleRegenerate` becomes:

```java
inFlightUsrIds.add(fer.usrID());
feTable.refresh();
GenerationProgressDialog.run(owner, GenerationProgressDialog.Mode.REGENERATE, fer.feeEarner(),
    () -> spreadsheetSvc.generateFromArchive(fer.usrID(), fer.runId(), config),
    () -> {
        inFlightUsrIds.remove(fer.usrID());
        feTable.refresh();
    });
```

`inFlightUsrIds` is keyed by `usrID` only (not by `runId`), and is a field of the window instance
— so it stays correct even as `feTable`'s contents are replaced when the user selects a different
row in `runTable` above it (switching back to a run with an in-flight `usrID` still shows that
row's button disabled).

`showAlert` is removed from this class — with the dialog replacing its only two call sites, it
becomes dead code.

---

## 4. Why "Cancel" is UI-only

Confirmed during brainstorming: clicking Cancel closes the dialog immediately and abandons
interest in the result, but the background thread keeps running to completion and still persists
its result (archive rows + blob) exactly as if the dialog were never cancelled. This matches the
current lack of any cancellation hook in `SpreadsheetService`, and avoids a much larger change
(interrupt-aware JDBC/POI code) for a low-frequency UI convenience. The one safeguard added is
row-level in-flight tracking (§3): even after Cancel, that fee earner's button stays disabled
until the background thread actually finishes, preventing a second concurrent generation for the
same fee earner from racing the first (both would target the same `run_id`/archive rows per
`SpreadsheetService.persist`'s single-spreadsheet-reuse-existing-run_id behavior).

---

## 5. Testing

Unit tests (`mvn test`):
- No new pure logic to unit test — `GenerationProgressDialog` is UI/threading glue, and
  `SpreadsheetService` is unchanged. `Mode`'s three string fields need no dedicated test.

Manual verification (GUI, not automatable — project has no TestFX/UI test harness):
1. Launch `FxApplication` → "Spreadsheet Generation" → confirm the menu now reads "Generate Single
   Past Spreadsheet" (singular).
2. "Generate Single Spreadsheet": click Generate on a row. Confirm the dialog appears centered
   over the main window, titled/messaged "Generating Spreadsheet", Cancel enabled, Dismiss
   disabled, and the row's button is now disabled. Wait for completion; confirm the message
   changes to "Spreadsheet generated for <name>", Cancel disables, Dismiss enables. Click Dismiss;
   confirm the row's button re-enables.
3. Repeat for "Generate Single Past Spreadsheet" / Regenerate, confirming "Regenerating
   Spreadsheet" / "Spreadsheet regenerated for <name>" wording.
4. Click Generate, then immediately click Cancel. Confirm the dialog closes right away and the
   row's button stays disabled; wait for the background work to actually finish (observable via
   app log) and confirm the row's button re-enables on its own without further interaction.
5. Click Generate, then click the window's X while in progress — confirm identical behavior to
   Cancel (step 4).
6. Force a failure (e.g. temporarily break DB connectivity or pick a fee earner with no prior run
   data mid-flight) and confirm the dialog shows the "Failed to generate/regenerate spreadsheet
   for <name>: <error>" message with Dismiss enabled, Cancel disabled.

---

## Open questions

None — all grey areas resolved during brainstorming:
- Cancel semantics: UI-only; background work is not interrupted. ✔
- Post-cancel outcome visibility: silent, relies on existing logging only. ✔
- Dialog modality: `Modality.WINDOW_MODAL` over the owning grid window. ✔
- Failure display: shown in-dialog (message + Dismiss), not a separate `Alert`. ✔
- Window-close (X) behavior: acts like Cancel while in progress, like Dismiss once complete. ✔
- Row re-enable timing: stays disabled until the background work actually finishes, not just
  until the dialog closes — prevents a duplicate concurrent run for the same fee earner. ✔
