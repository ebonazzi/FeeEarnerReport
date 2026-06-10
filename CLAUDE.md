# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Java 25 (Liberica Full JDK) desktop app that, per active fee earner, queries SQL Server, builds a 5-sheet Excel workbook, archives the rows, stores the workbook as a blob, and emails it. It runs two ways from one codebase: a headless CLI (`Main`) for IntelliJ debugging and bulk runs, and a JavaFX GUI (`FxApplication`) for interactive operation. The authoritative design doc is `docs/superpowers/specs/2026-06-04-fee-earner-report-design.md` — read it before non-trivial changes; it describes intended behavior section by section.

## Build & run

```bash
mvn package                      # uberjar via shade plugin -> target/FeeEarnerReport-1.0-SNAPSHOT.jar (main = FxApplication)
mvn package -Dmain.class=net.javalover.feeearner.Main   # uberjar whose manifest main is the CLI
mvn -Pnative package             # GraalVM native image via gluonfx-maven-plugin

# Run (both entry points take a credentials file as args[0]):
java -jar target/FeeEarnerReport-1.0-SNAPSHOT.jar /path/to/credentials.properties
```

JavaFX is `provided` scope — it is expected to come from a **Liberica Full JDK** at runtime and is deliberately excluded from the uberjar (`org.openjfx:*` excluded in the shade config). Don't add JavaFX to the fat jar.

> **Running in IntelliJ requires the _Full_ JDK.** The project SDK and the `FxApplication` run-config JRE must point at a Liberica **Full** JDK (e.g. `/usr/lib/jvm/bellsoft-java25-full-amd64`), which bundles the `javafx.*` modules in its runtime image. The standard Liberica JDK (`bellsoft-java25-amd64`) has no JavaFX, so launching `FxApplication` against it fails with `NoClassDefFoundError: javafx/application/Application`. With the Full JDK the normal `-classpath` launch works — no `--module-path` needed. Verify a JDK is Full with `java --list-modules | grep javafx`.

## Tests

```bash
mvn test                         # unit tests only — surefire excludes group "integration"
mvn -Pintegration test           # also runs *IT.java (tagged @Tag("integration"))
mvn test -Dtest=AppConfigTest    # single test class
mvn test -Dtest=AppConfigTest#methodName
```

Integration tests (`*IT.java`, e.g. `FeeEarnerRepositoryIT`, `ParamRepositoryIT`) hit a **real SQL Server**. They load `TestDataSourceFactory.create()`, which reads `src/test/resources/test-credentials.properties` (NOT committed — create it locally, same key format as the runtime credentials file). Without it, integration tests fail at startup.

## Configuration: two-tier

1. **Credentials file** (CLI arg, java `.properties` format): `sqlserver_hostname`, `sqlserver_dbname`, `sqlserver_portnumber`, `sqlserver_username`, `sqlserver_password`. Parsed by `CredentialLoader` into `DbCredentials`, which builds an *unencrypted* JDBC URL (`encrypt=false;trustServerCertificate=true`) — intranet-only by design.
2. **`report_param` DB table**: every other setting (`log_dir`, `output_dir`, `debug_level`, thread pool sizes, all email/SMTP settings). Loaded by `ParamRepository.loadAll()` into the immutable `AppConfig` record. `AppConfig` exposes typed accessors (`logDir()`, `smtpPort()`, etc.); `require()` throws if a needed param is missing/blank. Editing params in the GUI calls `ParameterService.reload()`, which also re-applies the log level live.

## Bootstrap sequence (identical in `Main` and `FxApplication`)

CredentialLoader → HikariDataSource → `ParamRepository.loadAll()` → `AppConfig` → `LoggingInitialiser.init(logDir, level)` → construct repositories (all take the `DataSource`) → construct services → run (CLI) or `MainWindow.show()` (FX). Bootstrap failures print to stderr and exit (CLI) or show an `Alert` and `Platform.exit()` (FX).

## Architecture (layered, one direction)

`ui` / `Main` → `service` → `repository` → SQL Server. Models are immutable Java **records**.

- **repository/** — thin JDBC over `report.*` SQL functions and archive tables, try-with-resources per call. `WorksheetRepository` has 10 methods: `{Lead,Matter} × {FullTask, Limitation, Aged, Duplicate, HighVolume}`, each `report.fn_VIC_*` TVFs. `FeeEarnerRepository.getIntersectUserIds()` finds users present in both the Lead and Matter lists.
- **service/** — `SpreadsheetService` is the core: for one fee earner it calls the function set for their source list, *plus* the other set if their `usrID` is in the intersect, merges Lead+Matter rows per worksheet (no de-dup), builds the workbook, bulk-inserts archives, stores the blob, deletes the temp file. **`output_dir` is scratch space** — the xlsx is written there, persisted as a DB blob (`FeeEarnersRun`), then deleted, so an empty `output_dir` after a run is expected; view generated sheets via the GUI's "Show Previous Runs". `generateAll`/`sendAll` use an `ExecutorService` + `CompletionService`; **per-item exceptions are caught, logged, recorded in `ProgressTracker.failures`, and the loop continues** — one bad fee earner never aborts the batch.
- **excel/** — `WorkbookBuilder` orchestrates 5 `*SheetBuilder`s. `BaseRow` is a **sealed interface** permitting exactly the 5 row records; shared columns live there. Convention enforced across all sheets: the **`[Type]` column ("Matter"/"Enquiry") is always column 0**, header row is bold + frozen + auto-filtered, date cells use `dd/MM/yyyy`. Output file name: `fe_<usrID>_<YYYYMMDD>_<runId>.xlsx`.
- **UI contract**: every button handler calls a service method directly — the *same* method the CLI calls. **No business logic lives in controllers.** Bulk windows poll `ProgressTracker` on a `ScheduledService` and update via `Platform.runLater()`. Single-generate requires a prior bulk run to exist (`RunRepository.getMostRecent`).

## Logging (a known footgun)

Logging is SLF4J → Rainbow Gum, wired entirely through `pom.xml` service bindings, and it is fragile — see the extensive comments there before touching logging deps:
- `rainbowgum-slf4j` provides the `SLF4JServiceProvider`. Remove it and **all SLF4J logging silently becomes NOP** (no errors, just no logs).
- `log4j-to-slf4j` bridges Apache POI's Log4j2-API calls onto Rainbow Gum. Remove it and POI prints "could not find a logging implementation".
- The shade plugin's `ServicesResourceTransformer` is what makes these `META-INF/services` bindings survive uberjar assembly — keep it.

`LoggingInitialiser.init(logDir, level)` configures Rainbow Gum 0.8.0 by setting **`logging.*` system properties** (NOT `rainbowgum.*` — that prefix is silently ignored): `logging.appenders=file,console`, `logging.appender.file.output=<file: URI>`, `logging.appender.console.output=stdout`, `logging.level`. Key constraints:
- **Ordering is load-bearing.** Rainbow Gum initialises lazily on the *first* SLF4J call and caches its config forever. So `init()` must run before anything logs — in particular before `new HikariDataSource(...)` (HikariCP logs on construction). The bootstrap therefore reads params via `ParamRepository.loadAllBootstrap(creds)` (a plain `DriverManager` connection, no SLF4J) → `init()` → *then* builds the Hikari pool. Don't reorder this.
- **No custom log pattern / rotation.** `rainbowgum-pattern` is not a dependency, so the Logback-style `%d|%level|...` pattern can't be applied — Rainbow Gum's default line format is used. Size-based rotation + gz compression are likewise not wired up. Adding either means adding Rainbow Gum modules/config, not just system properties.
- **Dynamic level change** (`applyLevel`, called by `ParameterService.reload()` when `debug_level` is saved in the GUI) works only because `init()` sets `logging.global.change=true` (master switch) and `logging.change=LEVEL` (allow level changes for all loggers). `applyLevel` then updates `logging.level` and calls `RainbowGum.getOrNull().config().changePublisher().publish()` — writing the system property alone does nothing, since Rainbow Gum caches its config after first init.
- **Noisy third-party loggers are floored** in `init()` via per-logger `logging.level.<pkg>` keys (`applyQuietDefaults`): mssql-jdbc → WARN (it logs every SQLException it builds at FINE/DEBUG — e.g. one line per duplicate-key row), plus Jakarta Mail / Angus / POI. These floors are independent of `debug_level`, so raising the app to DEBUG won't re-flood with driver internals. Application failures are still logged by the app itself at ERROR (e.g. `SpreadsheetService.generateAll`'s per-fee-earner catch).

## Database schema & SQL scripts

`src/main/resources/sql/` (numbered, run in order): `01_tables.sql`, `02_partition_functions.sql`, `03_partition_schemes.sql`, `04_apply_partitioning.sql`, `05_widen_type_column.sql`. The 5 archive tables are weekly-partitioned by `day_run`. `99_truncate_data.sql` resets generated data while preserving `report_param`. These are DDL the app depends on but does not run itself — apply them to the DB out of band.

## Repo conventions

The `.idea/` directory is **tracked** in this repo (e.g. `.idea/misc.xml`, `.idea/codeStyles/`) — shared IntelliJ project settings are committed intentionally, not git-ignored. When changing project/run/code-style settings, expect `.idea/` files to show up in `git status` and commit them deliberately rather than discarding them.
