# STATE.md — Fee Earner Report

## Project Reference
**What this is:** Java 25 desktop app that generates per-fee-earner Excel spreadsheets from SQL Server data and emails them. Runs headless (CLI/IntelliJ) or as a JavaFX GUI.

## Current Position
- **Plan 1 (Foundation Layer):** COMPLETE — 14 tasks, 17 tests
- **Plan 2a (Core Services + Excel + CLI):** COMPLETE — 8 tasks, 35 tests total, all passing
- **Plan 2b (JavaFX UI):** NOT STARTED — next action

## Progress
`[████████░░]` ~80% — services and CLI complete; UI layer remaining

## Recent Decisions
- `[Type]` column is always first (column 0) in every sheet
- Freeze pane on top row + auto-filter on all sheets
- No SMTP auth; CC via pipe-split `email_recipients` param
- `generateAll` / `sendAll` catch per-FE exceptions and continue (failures tracked in `ProgressTracker`)
- Main.java is the CLI entry point; FxApplication.java (not yet created) will be the JavaFX entry point

## What's Implemented
- model/, config/, repository/ (5 repos), service/ (4 services), excel/ (WorkbookBuilder + 5 builders), email/MailSender, logging/LoggingInitialiser, Main.java

## What's Missing (Plan 2b)
- `FxApplication.java`
- `ui/MainWindow.java` (MenuBar with 6 menu items)
- `ui/ParameterEditorWindow.java`
- `ui/PreviousRunsWindow.java`
- `ui/FeeEarnerDetailWindow.java`
- `ui/SpreadsheetViewerWindow.java`
- `ui/GenerateAllWindow.java`
- `ui/EmailAllWindow.java`
- `ui/SingleGenerateWindow.java`
- `ui/SingleEmailWindow.java`

## Blockers / Concerns
- None

## Session Continuity
Last session: 2026-06-04
Stopped at: Plan 2a complete (35 tests passing). Plan 2b not started.
Resume file: none
