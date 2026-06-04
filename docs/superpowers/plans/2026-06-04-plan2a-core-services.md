# Plan 2a — Core Services + Excel + CLI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement RunService, SpreadsheetService, EmailService, MailSender, WorkbookBuilder + 5 sheet builders, and Main.java CLI entry point — producing a fully runnable headless report generator.

**Architecture:** Single Maven module. Services receive repositories via constructor injection (no frameworks). WorkbookBuilder returns `byte[]` so SpreadsheetService can write to disk, archive rows, store blob, then delete the temp file. Sheet builders extend a package-private `BaseSheetBuilder<T>` that handles headers, freeze pane, auto-filter, and auto-size. Main.java is the CLI entry point; FxApplication (Plan 2b) is the JavaFX entry point.

**Tech Stack:** Java 22 (Corretto), Apache POI 5.3.0 (`poi-ooxml`), Jakarta Mail API 2.1.3 + Angus Mail 2.0.3, HikariCP 6.2.1, JUnit 5.11.4. Build: `mvn test` (unit tests), `mvn test -Pintegration` (integration).

---

## File Map

**New — model:**
- `src/main/java/net/javalover/feeearner/model/ProgressEvent.java`

**New — service:**
- `src/main/java/net/javalover/feeearner/service/RunService.java`
- `src/main/java/net/javalover/feeearner/service/SpreadsheetService.java`
- `src/main/java/net/javalover/feeearner/service/EmailService.java`

**New — excel (package-private except WorkbookBuilder):**
- `src/main/java/net/javalover/feeearner/excel/BaseSheetBuilder.java`
- `src/main/java/net/javalover/feeearner/excel/WorkbookBuilder.java`
- `src/main/java/net/javalover/feeearner/excel/FullTaskSheetBuilder.java`
- `src/main/java/net/javalover/feeearner/excel/LimitationSheetBuilder.java`
- `src/main/java/net/javalover/feeearner/excel/AgedSheetBuilder.java`
- `src/main/java/net/javalover/feeearner/excel/DuplicateSheetBuilder.java`
- `src/main/java/net/javalover/feeearner/excel/HighVolumeSheetBuilder.java`

**New — email:**
- `src/main/java/net/javalover/feeearner/email/MailSender.java`

**New — entry point:**
- `src/main/java/net/javalover/feeearner/Main.java`

**New — tests:**
- `src/test/java/net/javalover/feeearner/model/ProgressEventTest.java`
- `src/test/java/net/javalover/feeearner/service/RunServiceTest.java`
- `src/test/java/net/javalover/feeearner/excel/WorkbookBuilderTest.java`
- `src/test/java/net/javalover/feeearner/service/SpreadsheetServiceTest.java`
- `src/test/java/net/javalover/feeearner/email/MailSenderTest.java`
- `src/test/java/net/javalover/feeearner/service/EmailServiceTest.java`

---

## Column layout reference (used by all sheet builders)

Base columns written by `BaseSheetBuilder.writeBaseColumns(row, data, dateStyle)`:

| Col | Header | Row accessor | Type |
|-----|--------|-------------|------|
| 0 | `[Type]` | `row.type()` | String |
| 1 | `[Report Date]` | `row.reportDate()` | LocalDate |
| 2 | `[Matter Number]` | `row.matterNumber()` | String |
| 3 | `[Matter Name/Description]` | `row.matterNameDescription()` | String |
| 4 | `Department` | `row.department()` | String |
| 5 | `[Practice Code]` | `row.practiceCode()` | String |
| 6 | `[Office Name]` | `row.officeName()` | String |
| 7 | `Jurisdiction` | `row.jurisdiction()` | String |
| 8 | `[Fee Earner]` | `row.feeEarner()` | String |
| 9 | `[Legal Assistant]` | `row.legalAssistant()` | String |
| 10 | `[Supervising Fee Earner]` | `row.supervisingFeeEarner()` | String |
| 11 | `[Task Description]` | `row.taskDescription()` | String |
| 12 | `[Task Type]` | `row.taskType()` | String |
| 13 | `[Task Notes]` | `row.taskNotes()` | String |
| 14 | `[Task Owner]` | `row.taskOwner()` | String |
| 15 | `[Task Created Date]` | `row.taskCreatedDate()` | LocalDateTime |
| 16 | `[Task Due Date]` | `row.taskDueDate()` | LocalDateTime |

Extra columns per sheet (index 17+):
- **FullTask**: none — 17 cols total
- **Limitation**: col 17 = `[Key Words]` → `row.keyWords()` (String)
- **Aged**: none — 17 cols total
- **Duplicate**: col 17 = `[Duplicate]` → `row.duplicate()` (String)
- **HighVolume**: col 17 = `[Matter Row Count]` → `row.matterRowCount()` (int)

---

## Task 1: ProgressEvent record

**Files:**
- Create: `src/main/java/net/javalover/feeearner/model/ProgressEvent.java`
- Create: `src/test/java/net/javalover/feeearner/model/ProgressEventTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.javalover.feeearner.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressEventTest {

    @Test
    void holdsCompletedTotalFailed() {
        var event = new ProgressEvent(3, 10, 1);
        assertEquals(3, event.completed());
        assertEquals(10, event.total());
        assertEquals(1, event.failed());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (class not found)**

```bash
mvn test -pl . -Dtest=ProgressEventTest -q
```

- [ ] **Step 3: Implement ProgressEvent**

```java
package net.javalover.feeearner.model;

public record ProgressEvent(int completed, int total, int failed) {}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn test -pl . -Dtest=ProgressEventTest -q
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/javalover/feeearner/model/ProgressEvent.java \
        src/test/java/net/javalover/feeearner/model/ProgressEventTest.java
git commit -m "feat: add ProgressEvent record"
```

---

## Task 2: RunService

**Files:**
- Create: `src/main/java/net/javalover/feeearner/service/RunService.java`
- Create: `src/test/java/net/javalover/feeearner/service/RunServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.javalover.feeearner.service;

import net.javalover.feeearner.repository.RunRepository;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RunServiceTest {

    @Test
    void startRunReturnsRunIdFromRepository() {
        var repo = new RunRepository(null) {
            @Override public int createRun() { return 42; }
        };
        var service = new RunService(repo);
        assertEquals(42, service.startRun());
    }

    @Test
    void finishRunDelegatesToRepository() {
        var closedId = new AtomicInteger(-1);
        var repo = new RunRepository(null) {
            @Override public int createRun() { return 0; }
            @Override public void closeRun(int runId) { closedId.set(runId); }
        };
        var service = new RunService(repo);
        service.finishRun(7);
        assertEquals(7, closedId.get());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -pl . -Dtest=RunServiceTest -q
```

- [ ] **Step 3: Implement RunService**

```java
package net.javalover.feeearner.service;

import net.javalover.feeearner.repository.RunRepository;

public class RunService {

    private final RunRepository runRepo;

    public RunService(RunRepository runRepo) {
        this.runRepo = runRepo;
    }

    public int startRun() {
        return runRepo.createRun();
    }

    public void finishRun(int runId) {
        runRepo.closeRun(runId);
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn test -pl . -Dtest=RunServiceTest -q
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/javalover/feeearner/service/RunService.java \
        src/test/java/net/javalover/feeearner/service/RunServiceTest.java
git commit -m "feat: add RunService (startRun/finishRun)"
```

---

## Task 3: BaseSheetBuilder + WorkbookBuilder

**Files:**
- Create: `src/main/java/net/javalover/feeearner/excel/BaseSheetBuilder.java`
- Create: `src/main/java/net/javalover/feeearner/excel/WorkbookBuilder.java`
- Create: `src/test/java/net/javalover/feeearner/excel/WorkbookBuilderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.*;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorkbookBuilderTest {

    private static final List<String> EXPECTED_SHEETS = List.of(
        "Matters/Leads Full Report", "Limitation", "Aged", "Duplicate", "High Volume"
    );

    @Test
    void producesFiveSheetsInOrder() throws IOException {
        var bytes = new WorkbookBuilder().build(List.of(), List.of(), List.of(), List.of(), List.of());
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(5, wb.getNumberOfSheets());
            for (int i = 0; i < 5; i++) {
                assertEquals(EXPECTED_SHEETS.get(i), wb.getSheetName(i));
            }
        }
    }

    @Test
    void firstSheetHasTypeAsFirstColumn() throws IOException {
        var row = new FullTaskRow("Lead", null, "M1", "Desc", "Dept", "PC",
                "Office", "Jur", "FE", "LA", "SFE", "TaskD", "TaskT", "Notes",
                "Owner", null, null);
        var bytes = new WorkbookBuilder().build(List.of(row), List.of(), List.of(), List.of(), List.of());
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheetAt(0);
            assertEquals("[Type]", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Lead", sheet.getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void firstSheetHasFreezePaneOnTopRow() throws IOException {
        var bytes = new WorkbookBuilder().build(List.of(), List.of(), List.of(), List.of(), List.of());
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var info = wb.getSheetAt(0).getPaneInformation();
            assertTrue(info.isFreezePane());
            assertEquals(1, info.getHorizontalSplitTopRow());
        }
    }

    @Test
    void firstSheetHasAutoFilter() throws IOException {
        var bytes = new WorkbookBuilder().build(List.of(), List.of(), List.of(), List.of(), List.of());
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(wb.getSheetAt(0).getAutoFilter());
        }
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -pl . -Dtest=WorkbookBuilderTest -q
```

- [ ] **Step 3: Implement BaseSheetBuilder**

```java
package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.BaseRow;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

abstract class BaseSheetBuilder<T extends BaseRow> {

    abstract List<String> headers();

    abstract void writeExtraColumns(XSSFRow row, T data, XSSFCellStyle dateStyle);

    void build(XSSFSheet sheet, List<T> rows, XSSFCellStyle dateStyle) {
        var wb = (XSSFWorkbook) sheet.getWorkbook();
        var boldStyle = createBoldStyle(wb);
        writeHeaderRow(sheet, boldStyle);
        int lastCol = headers().size() - 1;
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, lastCol));
        for (int i = 0; i < rows.size(); i++) {
            var dataRow = sheet.createRow(i + 1);
            writeBaseColumns(dataRow, rows.get(i), dateStyle);
            writeExtraColumns(dataRow, rows.get(i), dateStyle);
        }
        for (int col = 0; col <= lastCol; col++) {
            sheet.autoSizeColumn(col);
        }
    }

    private void writeHeaderRow(XSSFSheet sheet, XSSFCellStyle boldStyle) {
        var headerRow = sheet.createRow(0);
        var hdrs = headers();
        for (int i = 0; i < hdrs.size(); i++) {
            var cell = headerRow.createCell(i);
            cell.setCellValue(hdrs.get(i));
            cell.setCellStyle(boldStyle);
        }
    }

    private XSSFCellStyle createBoldStyle(XSSFWorkbook wb) {
        var style = wb.createCellStyle();
        var font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    protected void writeBaseColumns(XSSFRow row, BaseRow data, XSSFCellStyle dateStyle) {
        setString(row, 0, data.type());
        setDate(row, 1, data.reportDate(), dateStyle);
        setString(row, 2, data.matterNumber());
        setString(row, 3, data.matterNameDescription());
        setString(row, 4, data.department());
        setString(row, 5, data.practiceCode());
        setString(row, 6, data.officeName());
        setString(row, 7, data.jurisdiction());
        setString(row, 8, data.feeEarner());
        setString(row, 9, data.legalAssistant());
        setString(row, 10, data.supervisingFeeEarner());
        setString(row, 11, data.taskDescription());
        setString(row, 12, data.taskType());
        setString(row, 13, data.taskNotes());
        setString(row, 14, data.taskOwner());
        setDateTime(row, 15, data.taskCreatedDate(), dateStyle);
        setDateTime(row, 16, data.taskDueDate(), dateStyle);
    }

    protected void setString(XSSFRow row, int col, String value) {
        row.createCell(col).setCellValue(value != null ? value : "");
    }

    protected void setDate(XSSFRow row, int col, LocalDate date, XSSFCellStyle style) {
        var cell = row.createCell(col);
        if (date != null) {
            cell.setCellValue(date);
            cell.setCellStyle(style);
        }
    }

    protected void setDateTime(XSSFRow row, int col, LocalDateTime dt, XSSFCellStyle style) {
        var cell = row.createCell(col);
        if (dt != null) {
            cell.setCellValue(dt);
            cell.setCellStyle(style);
        }
    }
}
```

- [ ] **Step 4: Implement WorkbookBuilder** (sheet builder classes referenced here will be created in Task 4)

```java
package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class WorkbookBuilder {

    public byte[] build(
        List<FullTaskRow> fullTask,
        List<LimitationRow> limitation,
        List<AgedRow> aged,
        List<DuplicateRow> duplicate,
        List<HighVolumeRow> highVolume
    ) throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("dd/MM/yyyy"));

            new FullTaskSheetBuilder().build(
                wb.createSheet("Matters/Leads Full Report"), fullTask, dateStyle);
            new LimitationSheetBuilder().build(
                wb.createSheet("Limitation"), limitation, dateStyle);
            new AgedSheetBuilder().build(
                wb.createSheet("Aged"), aged, dateStyle);
            new DuplicateSheetBuilder().build(
                wb.createSheet("Duplicate"), duplicate, dateStyle);
            new HighVolumeSheetBuilder().build(
                wb.createSheet("High Volume"), highVolume, dateStyle);

            var out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
```

- [ ] **Step 5: Run test — expect FAIL (sheet builders not yet created)**

```bash
mvn test -pl . -Dtest=WorkbookBuilderTest -q
```

Expected: compilation failure — `FullTaskSheetBuilder` etc. not found. This is the TDD red state; proceed to Task 4.

- [ ] **Step 6: Commit BaseSheetBuilder + WorkbookBuilder (compiles after Task 4)**

Defer this commit to after Task 4 when the build is green.

---

## Task 4: Five sheet builders

**Files:**
- Create: `src/main/java/net/javalover/feeearner/excel/FullTaskSheetBuilder.java`
- Create: `src/main/java/net/javalover/feeearner/excel/LimitationSheetBuilder.java`
- Create: `src/main/java/net/javalover/feeearner/excel/AgedSheetBuilder.java`
- Create: `src/main/java/net/javalover/feeearner/excel/DuplicateSheetBuilder.java`
- Create: `src/main/java/net/javalover/feeearner/excel/HighVolumeSheetBuilder.java`

All five follow the same pattern. `[Type]` is column 0 (already written by `writeBaseColumns`). Extra columns start at 17.

- [ ] **Step 1: Implement FullTaskSheetBuilder**

```java
package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.FullTaskRow;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import java.util.List;

class FullTaskSheetBuilder extends BaseSheetBuilder<FullTaskRow> {

    private static final List<String> HEADERS = List.of(
        "[Type]", "[Report Date]", "[Matter Number]", "[Matter Name/Description]",
        "Department", "[Practice Code]", "[Office Name]", "Jurisdiction",
        "[Fee Earner]", "[Legal Assistant]", "[Supervising Fee Earner]",
        "[Task Description]", "[Task Type]", "[Task Notes]", "[Task Owner]",
        "[Task Created Date]", "[Task Due Date]"
    );

    @Override
    List<String> headers() { return HEADERS; }

    @Override
    void writeExtraColumns(XSSFRow row, FullTaskRow data, XSSFCellStyle dateStyle) {
        // no extra columns
    }
}
```

- [ ] **Step 2: Implement LimitationSheetBuilder**

```java
package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.LimitationRow;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import java.util.List;

class LimitationSheetBuilder extends BaseSheetBuilder<LimitationRow> {

    private static final List<String> HEADERS = List.of(
        "[Type]", "[Report Date]", "[Matter Number]", "[Matter Name/Description]",
        "Department", "[Practice Code]", "[Office Name]", "Jurisdiction",
        "[Fee Earner]", "[Legal Assistant]", "[Supervising Fee Earner]",
        "[Task Description]", "[Task Type]", "[Task Notes]", "[Task Owner]",
        "[Task Created Date]", "[Task Due Date]", "[Key Words]"
    );

    @Override
    List<String> headers() { return HEADERS; }

    @Override
    void writeExtraColumns(XSSFRow row, LimitationRow data, XSSFCellStyle dateStyle) {
        setString(row, 17, data.keyWords());
    }
}
```

- [ ] **Step 3: Implement AgedSheetBuilder**

```java
package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.AgedRow;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import java.util.List;

class AgedSheetBuilder extends BaseSheetBuilder<AgedRow> {

    private static final List<String> HEADERS = List.of(
        "[Type]", "[Report Date]", "[Matter Number]", "[Matter Name/Description]",
        "Department", "[Practice Code]", "[Office Name]", "Jurisdiction",
        "[Fee Earner]", "[Legal Assistant]", "[Supervising Fee Earner]",
        "[Task Description]", "[Task Type]", "[Task Notes]", "[Task Owner]",
        "[Task Created Date]", "[Task Due Date]"
    );

    @Override
    List<String> headers() { return HEADERS; }

    @Override
    void writeExtraColumns(XSSFRow row, AgedRow data, XSSFCellStyle dateStyle) {
        // no extra columns
    }
}
```

- [ ] **Step 4: Implement DuplicateSheetBuilder**

```java
package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.DuplicateRow;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import java.util.List;

class DuplicateSheetBuilder extends BaseSheetBuilder<DuplicateRow> {

    private static final List<String> HEADERS = List.of(
        "[Type]", "[Report Date]", "[Matter Number]", "[Matter Name/Description]",
        "Department", "[Practice Code]", "[Office Name]", "Jurisdiction",
        "[Fee Earner]", "[Legal Assistant]", "[Supervising Fee Earner]",
        "[Task Description]", "[Task Type]", "[Task Notes]", "[Task Owner]",
        "[Task Created Date]", "[Task Due Date]", "[Duplicate]"
    );

    @Override
    List<String> headers() { return HEADERS; }

    @Override
    void writeExtraColumns(XSSFRow row, DuplicateRow data, XSSFCellStyle dateStyle) {
        setString(row, 17, data.duplicate());
    }
}
```

- [ ] **Step 5: Implement HighVolumeSheetBuilder**

```java
package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.HighVolumeRow;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import java.util.List;

class HighVolumeSheetBuilder extends BaseSheetBuilder<HighVolumeRow> {

    private static final List<String> HEADERS = List.of(
        "[Type]", "[Report Date]", "[Matter Number]", "[Matter Name/Description]",
        "Department", "[Practice Code]", "[Office Name]", "Jurisdiction",
        "[Fee Earner]", "[Legal Assistant]", "[Supervising Fee Earner]",
        "[Task Description]", "[Task Type]", "[Task Notes]", "[Task Owner]",
        "[Task Created Date]", "[Task Due Date]", "[Matter Row Count]"
    );

    @Override
    List<String> headers() { return HEADERS; }

    @Override
    void writeExtraColumns(XSSFRow row, HighVolumeRow data, XSSFCellStyle dateStyle) {
        row.createCell(17).setCellValue(data.matterRowCount());
    }
}
```

- [ ] **Step 6: Run WorkbookBuilderTest — expect PASS**

```bash
mvn test -pl . -Dtest=WorkbookBuilderTest -q
```

Expected output: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit all excel classes**

```bash
git add src/main/java/net/javalover/feeearner/excel/ \
        src/test/java/net/javalover/feeearner/excel/
git commit -m "feat: add WorkbookBuilder with 5 sheet builders (Type first, freeze top row, auto-filter)"
```

---

## Task 5: SpreadsheetService

**Files:**
- Create: `src/main/java/net/javalover/feeearner/service/SpreadsheetService.java`
- Create: `src/test/java/net/javalover/feeearner/service/SpreadsheetServiceTest.java`

**Design notes:**
- `generateForFeeEarner(FeeEarner, int runId, LocalDate, AppConfig, Consumer<ProgressEvent>)` — fetches intersect set from repo, calls `doGenerate`
- `generateAll(List<FeeEarner>, int runId, LocalDate, AppConfig, ProgressTracker)` — fetches intersect once, submits each FE to a fixed thread pool via `CompletionService`; exceptions are caught inside each `Callable`, logged, added to tracker
- `doGenerate(FeeEarner, Set<Integer> intersectIds, ...)` — private; calls Lead and/or Matter worksheet functions based on `fe.type()` and intersect membership, builds workbook, writes file, archives, inserts FeeEarnerRun, deletes file
- Intersect logic: if `fe.type().equalsIgnoreCase("Lead")` → always call Lead functions; if also in intersect → also call Matter. Vice versa for Matter.

- [ ] **Step 1: Write the failing tests**

```java
package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.excel.WorkbookBuilder;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class SpreadsheetServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 5);
    private static final FeeEarner LEAD_FE =
        new FeeEarner(100, "Alice Smith", "alice@law.com", true, "Lead");
    private static final FeeEarner MATTER_FE =
        new FeeEarner(200, "Bob Jones", "bob@law.com", true, "Matter");
    private static final FeeEarner INTERSECT_FE =
        new FeeEarner(300, "Carol Lee", "carol@law.com", true, "Lead");

    @TempDir Path tempDir;

    private AppConfig configFor(Path dir) {
        return AppConfig.from(List.of(
            new AppParam(1, "output_dir", dir.toString(), true),
            new AppParam(2, "thread_pool_size", "2", true)
        ));
    }

    @Test
    void leadFeeEarnerCallsOnlyLeadFunctions(@TempDir Path dir) throws IOException {
        var leadCalled = new AtomicBoolean(false);
        var matterCalled = new AtomicBoolean(false);

        var worksheetRepo = new WorksheetRepository(null) {
            @Override public List<FullTaskRow> getLeadFullTaskData(int id) {
                leadCalled.set(true); return List.of();
            }
            @Override public List<LimitationRow> getLeadLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getLeadAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getLeadDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getLeadHighVolume(int id) { return List.of(); }
            @Override public List<FullTaskRow> getMatterFullTaskData(int id) {
                matterCalled.set(true); return List.of();
            }
            @Override public List<LimitationRow> getMatterLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getMatterAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getMatterDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getMatterHighVolume(int id) { return List.of(); }
        };
        var updatedFilenames = new ArrayList<String>();
        var runRepo = new RunRepository(null) {
            @Override public int createRun() { return 0; }
            @Override public void closeRun(int id) {}
            @Override public void insertFeeEarnerRun(FeeEarnerRun run) {}
            @Override public void updateFeeEarnerRun(int rId, int uId, String fn, byte[] b) {
                updatedFilenames.add(fn);
            }
        };
        var feeEarnerRepo = new FeeEarnerRepository(null) {
            @Override public Set<Integer> getIntersectUserIds() { return Set.of(); }
        };
        var service = new SpreadsheetService(worksheetRepo, new ArchiveRepository(null) {
            @Override public void insertFullTaskRows(int r, LocalDate d, int u, List<FullTaskRow> rows) {}
            @Override public void insertLimitationRows(int r, LocalDate d, int u, List<LimitationRow> rows) {}
            @Override public void insertAgedRows(int r, LocalDate d, int u, List<AgedRow> rows) {}
            @Override public void insertDuplicateRows(int r, LocalDate d, int u, List<DuplicateRow> rows) {}
            @Override public void insertHighVolumeRows(int r, LocalDate d, int u, List<HighVolumeRow> rows) {}
        }, runRepo, feeEarnerRepo, new WorkbookBuilder());

        service.generateForFeeEarner(LEAD_FE, 1, DAY, configFor(dir), e -> {});

        assertTrue(leadCalled.get(), "Lead functions should be called for Lead fee earner");
        assertFalse(matterCalled.get(), "Matter functions should NOT be called for Lead-only fee earner");
        assertEquals(1, updatedFilenames.size());
        assertEquals("fe_100_20260605_1.xlsx", updatedFilenames.get(0));
    }

    @Test
    void intersectFeeEarnerCallsBothFunctions(@TempDir Path dir) throws IOException {
        var leadCalled = new AtomicBoolean(false);
        var matterCalled = new AtomicBoolean(false);
        var worksheetRepo = new WorksheetRepository(null) {
            @Override public List<FullTaskRow> getLeadFullTaskData(int id) {
                leadCalled.set(true); return List.of();
            }
            @Override public List<LimitationRow> getLeadLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getLeadAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getLeadDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getLeadHighVolume(int id) { return List.of(); }
            @Override public List<FullTaskRow> getMatterFullTaskData(int id) {
                matterCalled.set(true); return List.of();
            }
            @Override public List<LimitationRow> getMatterLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getMatterAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getMatterDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getMatterHighVolume(int id) { return List.of(); }
        };
        var feeEarnerRepo = new FeeEarnerRepository(null) {
            @Override public Set<Integer> getIntersectUserIds() { return Set.of(300); }
        };
        var service = new SpreadsheetService(worksheetRepo, new ArchiveRepository(null) {
            @Override public void insertFullTaskRows(int r, LocalDate d, int u, List<FullTaskRow> rows) {}
            @Override public void insertLimitationRows(int r, LocalDate d, int u, List<LimitationRow> rows) {}
            @Override public void insertAgedRows(int r, LocalDate d, int u, List<AgedRow> rows) {}
            @Override public void insertDuplicateRows(int r, LocalDate d, int u, List<DuplicateRow> rows) {}
            @Override public void insertHighVolumeRows(int r, LocalDate d, int u, List<HighVolumeRow> rows) {}
        }, new RunRepository(null) {
            @Override public int createRun() { return 0; }
            @Override public void closeRun(int id) {}
            @Override public void insertFeeEarnerRun(FeeEarnerRun run) {}
        }, feeEarnerRepo, new WorkbookBuilder());

        service.generateForFeeEarner(INTERSECT_FE, 1, DAY, configFor(dir), e -> {});

        assertTrue(leadCalled.get(), "Lead functions must be called for intersect fee earner");
        assertTrue(matterCalled.get(), "Matter functions must also be called for intersect fee earner");
    }

    @Test
    void generateForFeeEarnerDeletesTempFile(@TempDir Path dir) throws IOException {
        var worksheetRepo = new WorksheetRepository(null) {
            @Override public List<FullTaskRow> getLeadFullTaskData(int id) { return List.of(); }
            @Override public List<LimitationRow> getLeadLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getLeadAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getLeadDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getLeadHighVolume(int id) { return List.of(); }
            @Override public List<FullTaskRow> getMatterFullTaskData(int id) { return List.of(); }
            @Override public List<LimitationRow> getMatterLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getMatterAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getMatterDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getMatterHighVolume(int id) { return List.of(); }
        };
        var service = new SpreadsheetService(worksheetRepo, new ArchiveRepository(null) {
            @Override public void insertFullTaskRows(int r, LocalDate d, int u, List<FullTaskRow> rows) {}
            @Override public void insertLimitationRows(int r, LocalDate d, int u, List<LimitationRow> rows) {}
            @Override public void insertAgedRows(int r, LocalDate d, int u, List<AgedRow> rows) {}
            @Override public void insertDuplicateRows(int r, LocalDate d, int u, List<DuplicateRow> rows) {}
            @Override public void insertHighVolumeRows(int r, LocalDate d, int u, List<HighVolumeRow> rows) {}
        }, new RunRepository(null) {
            @Override public int createRun() { return 0; }
            @Override public void closeRun(int id) {}
            @Override public void insertFeeEarnerRun(FeeEarnerRun run) {}
        }, new FeeEarnerRepository(null) {
            @Override public Set<Integer> getIntersectUserIds() { return Set.of(); }
        }, new WorkbookBuilder());

        service.generateForFeeEarner(LEAD_FE, 1, DAY, configFor(dir), e -> {});

        // temp file must be deleted after run
        var remaining = Files.list(dir).toList();
        assertTrue(remaining.isEmpty(), "Temp xlsx file must be deleted after insertFeeEarnerRun");
    }

    @Test
    void generateAllTracksFailures(@TempDir Path dir) {
        var failingRepo = new WorksheetRepository(null) {
            @Override public List<FullTaskRow> getLeadFullTaskData(int id) {
                throw new RuntimeException("DB error for " + id);
            }
            @Override public List<LimitationRow> getLeadLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getLeadAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getLeadDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getLeadHighVolume(int id) { return List.of(); }
            @Override public List<FullTaskRow> getMatterFullTaskData(int id) {
                throw new RuntimeException("DB error for " + id);
            }
            @Override public List<LimitationRow> getMatterLimitation(int id) { return List.of(); }
            @Override public List<AgedRow> getMatterAged(int id) { return List.of(); }
            @Override public List<DuplicateRow> getMatterDuplicate(int id) { return List.of(); }
            @Override public List<HighVolumeRow> getMatterHighVolume(int id) { return List.of(); }
        };
        var service = new SpreadsheetService(failingRepo, new ArchiveRepository(null) {
            @Override public void insertFullTaskRows(int r, LocalDate d, int u, List<FullTaskRow> rows) {}
            @Override public void insertLimitationRows(int r, LocalDate d, int u, List<LimitationRow> rows) {}
            @Override public void insertAgedRows(int r, LocalDate d, int u, List<AgedRow> rows) {}
            @Override public void insertDuplicateRows(int r, LocalDate d, int u, List<DuplicateRow> rows) {}
            @Override public void insertHighVolumeRows(int r, LocalDate d, int u, List<HighVolumeRow> rows) {}
        }, new RunRepository(null) {
            @Override public int createRun() { return 0; }
            @Override public void closeRun(int id) {}
            @Override public void insertFeeEarnerRun(FeeEarnerRun run) {}
        }, new FeeEarnerRepository(null) {
            @Override public Set<Integer> getIntersectUserIds() { return Set.of(); }
        }, new WorkbookBuilder());

        var tracker = new ProgressTracker(2);
        service.generateAll(List.of(LEAD_FE, MATTER_FE), 1, DAY, configFor(dir), tracker);

        assertEquals(2, tracker.failed().get());
        assertEquals(2, tracker.failures().size());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -pl . -Dtest=SpreadsheetServiceTest -q
```

- [ ] **Step 3: Implement SpreadsheetService**

```java
package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.excel.WorkbookBuilder;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class SpreadsheetService {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WorksheetRepository worksheetRepo;
    private final ArchiveRepository archiveRepo;
    private final RunRepository runRepo;
    private final FeeEarnerRepository feeEarnerRepo;
    private final WorkbookBuilder workbookBuilder;

    public SpreadsheetService(WorksheetRepository worksheetRepo,
                              ArchiveRepository archiveRepo,
                              RunRepository runRepo,
                              FeeEarnerRepository feeEarnerRepo,
                              WorkbookBuilder workbookBuilder) {
        this.worksheetRepo = worksheetRepo;
        this.archiveRepo = archiveRepo;
        this.runRepo = runRepo;
        this.feeEarnerRepo = feeEarnerRepo;
        this.workbookBuilder = workbookBuilder;
    }

    public void generateForFeeEarner(FeeEarner fe, int runId, LocalDate dayRun,
                                     AppConfig config, Consumer<ProgressEvent> onProgress)
            throws IOException {
        var intersect = feeEarnerRepo.getIntersectUserIds();
        doGenerate(fe, intersect, runId, dayRun, config, false); // false = updateFeeEarnerRun
        onProgress.accept(new ProgressEvent(1, 1, 0));
    }

    public void generateAll(List<FeeEarner> feeEarners, int runId, LocalDate dayRun,
                            AppConfig config, ProgressTracker tracker) {
        var intersect = feeEarnerRepo.getIntersectUserIds();
        var pool = Executors.newFixedThreadPool(config.threadPoolSize());
        var completion = new ExecutorCompletionService<Void>(pool);

        for (var fe : feeEarners) {
            completion.submit(() -> {
                try {
                    doGenerate(fe, intersect, runId, dayRun, config, true); // true = insertFeeEarnerRun
                    tracker.completed().incrementAndGet();
                } catch (Exception e) {
                    log.error("Failed to generate for usrID={} name='{}'", fe.usrID(), fe.feeEarner(), e);
                    tracker.failed().incrementAndGet();
                    tracker.failures().add(
                        new FailedEntry(fe.usrID(), fe.feeEarner(), e.getMessage()));
                }
                return null;
            });
        }

        int total = feeEarners.size();
        for (int i = 0; i < total; i++) {
            try {
                completion.take().get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException ignored) {
                // exceptions are handled inside the Callable
            }
        }
        pool.shutdown();
    }

    private void doGenerate(FeeEarner fe, Set<Integer> intersectIds,
                            int runId, LocalDate dayRun, AppConfig config,
                            boolean isNewRun) throws IOException {
        boolean isLead = fe.type().equalsIgnoreCase("Lead");
        boolean inIntersect = intersectIds.contains(fe.usrID());

        var fullTask = new ArrayList<FullTaskRow>();
        var limitation = new ArrayList<LimitationRow>();
        var aged = new ArrayList<AgedRow>();
        var duplicate = new ArrayList<DuplicateRow>();
        var highVolume = new ArrayList<HighVolumeRow>();

        if (isLead || inIntersect) {
            fullTask.addAll(worksheetRepo.getLeadFullTaskData(fe.usrID()));
            limitation.addAll(worksheetRepo.getLeadLimitation(fe.usrID()));
            aged.addAll(worksheetRepo.getLeadAged(fe.usrID()));
            duplicate.addAll(worksheetRepo.getLeadDuplicate(fe.usrID()));
            highVolume.addAll(worksheetRepo.getLeadHighVolume(fe.usrID()));
        }
        if (!isLead || inIntersect) {
            fullTask.addAll(worksheetRepo.getMatterFullTaskData(fe.usrID()));
            limitation.addAll(worksheetRepo.getMatterLimitation(fe.usrID()));
            aged.addAll(worksheetRepo.getMatterAged(fe.usrID()));
            duplicate.addAll(worksheetRepo.getMatterDuplicate(fe.usrID()));
            highVolume.addAll(worksheetRepo.getMatterHighVolume(fe.usrID()));
        }

        byte[] xlsx = workbookBuilder.build(fullTask, limitation, aged, duplicate, highVolume);

        String filename = "fe_" + fe.usrID() + "_" + dayRun.format(DATE_FMT) + "_" + runId + ".xlsx";
        Path file = Path.of(config.outputDir()).resolve(filename);
        Files.write(file, xlsx);

        archiveRepo.insertFullTaskRows(runId, dayRun, fe.usrID(), fullTask);
        archiveRepo.insertLimitationRows(runId, dayRun, fe.usrID(), limitation);
        archiveRepo.insertAgedRows(runId, dayRun, fe.usrID(), aged);
        archiveRepo.insertDuplicateRows(runId, dayRun, fe.usrID(), duplicate);
        archiveRepo.insertHighVolumeRows(runId, dayRun, fe.usrID(), highVolume);

        byte[] stored = Files.readAllBytes(file);
        if (isNewRun) {
            runRepo.insertFeeEarnerRun(
                new FeeEarnerRun(runId, dayRun, fe.usrID(), fe.feeEarner(),
                                 fe.usrEmail(), filename, stored, null));
        } else {
            runRepo.updateFeeEarnerRun(runId, fe.usrID(), filename, stored);
        }

        Files.delete(file);
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
mvn test -pl . -Dtest=SpreadsheetServiceTest -q
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Run all tests to confirm nothing broken**

```bash
mvn test -q
```

Expected: all 17 existing tests + 4 new WorkbookBuilder + 4 new SpreadsheetService + 2 RunService + 1 ProgressEvent = 28 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/javalover/feeearner/service/SpreadsheetService.java \
        src/test/java/net/javalover/feeearner/service/SpreadsheetServiceTest.java
git commit -m "feat: add SpreadsheetService (generateForFeeEarner, generateAll with thread pool)"
```

---

## Task 6: MailSender

**Files:**
- Create: `src/main/java/net/javalover/feeearner/email/MailSender.java`
- Create: `src/test/java/net/javalover/feeearner/email/MailSenderTest.java`

**Design:** `MailSender` exposes two methods: `send(MimeMessage)` for actual delivery, and a package-private `buildMessage(Session, FeeEarnerRun, AppConfig)` for message construction — the latter is unit-testable without a live SMTP server.

- [ ] **Step 1: Write the failing test**

```java
package net.javalover.feeearner.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.AppParam;
import net.javalover.feeearner.model.FeeEarnerRun;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class MailSenderTest {

    private AppConfig config() {
        return AppConfig.from(List.of(
            new AppParam(1, "smtp_server",       "localhost",            true),
            new AppParam(2, "smtp_port",         "25",                   true),
            new AppParam(3, "email_sender",      "reports@law.com",      true),
            new AppParam(4, "email_recipients",  "a@law.com|b@law.com",  true),
            new AppParam(5, "email_subject",     "Fee Earner Report",    true),
            new AppParam(6, "email_body",        "Please find attached.", true)
        ));
    }

    private FeeEarnerRun run() {
        return new FeeEarnerRun(1, LocalDate.of(2026, 6, 5), 100,
            "Alice Smith", "alice@law.com", "fe_100_20260605_1.xlsx",
            new byte[]{1, 2, 3}, null);
    }

    @Test
    void messageHasCorrectRecipients() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        var toAddrs = msg.getRecipients(jakarta.mail.Message.RecipientType.TO);
        assertEquals(1, toAddrs.length);
        assertEquals("alice@law.com", toAddrs[0].toString());
    }

    @Test
    void messageHasCcRecipients() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        var ccAddrs = msg.getRecipients(jakarta.mail.Message.RecipientType.CC);
        assertEquals(2, ccAddrs.length);
    }

    @Test
    void messageHasAttachment() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        assertTrue(msg.getContent() instanceof MimeMultipart);
        var mp = (MimeMultipart) msg.getContent();
        assertEquals(2, mp.getCount(), "Expected text body part + xlsx attachment");
    }

    @Test
    void messageSubjectAndFrom() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        assertEquals("Fee Earner Report", msg.getSubject());
        assertEquals("reports@law.com", msg.getFrom()[0].toString());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -pl . -Dtest=MailSenderTest -q
```

- [ ] **Step 3: Implement MailSender**

```java
package net.javalover.feeearner.email;

import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FeeEarnerRun;
import java.util.Arrays;
import java.util.Properties;

public class MailSender {

    private final AppConfig config;

    public MailSender(AppConfig config) {
        this.config = config;
    }

    public void send(FeeEarnerRun run) throws MessagingException {
        var props = new Properties();
        props.put("mail.smtp.host", config.smtpServer());
        props.put("mail.smtp.port", String.valueOf(config.smtpPort()));
        props.put("mail.smtp.auth", "false");
        props.put("mail.smtp.starttls.enable", "false");
        var session = Session.getInstance(props);
        Transport.send(buildMessage(session, run, config));
    }

    static MimeMessage buildMessage(Session session, FeeEarnerRun run, AppConfig config)
            throws MessagingException {
        var msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(config.emailSender()));
        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(run.usrEmail()));

        var recipients = config.emailRecipients();
        if (!recipients.isBlank()) {
            for (var addr : recipients.split("\\|")) {
                var trimmed = addr.trim();
                if (!trimmed.isEmpty()) {
                    msg.addRecipient(Message.RecipientType.CC, new InternetAddress(trimmed));
                }
            }
        }

        msg.setSubject(config.emailSubject());

        var body = new MimeBodyPart();
        body.setText(config.emailBody());

        var attachment = new MimeBodyPart();
        attachment.setDataHandler(new DataHandler(
            new ByteArrayDataSource(run.excelSpreadsheet(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
        attachment.setFileName(run.excelFilename());

        var multipart = new MimeMultipart();
        multipart.addBodyPart(body);
        multipart.addBodyPart(attachment);
        msg.setContent(multipart);

        return msg;
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn test -pl . -Dtest=MailSenderTest -q
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/javalover/feeearner/email/MailSender.java \
        src/test/java/net/javalover/feeearner/email/MailSenderTest.java
git commit -m "feat: add MailSender with Jakarta Mail (no auth, pipe-split CC)"
```

---

## Task 7: EmailService

**Files:**
- Create: `src/main/java/net/javalover/feeearner/service/EmailService.java`
- Create: `src/test/java/net/javalover/feeearner/service/EmailServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.email.MailSender;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.RunRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EmailServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 5);

    private AppConfig config() {
        return AppConfig.from(List.of(
            new AppParam(1, "smtp_server",       "localhost",       true),
            new AppParam(2, "smtp_port",         "25",              true),
            new AppParam(3, "email_sender",      "r@law.com",       true),
            new AppParam(4, "email_recipients",  "",                true),
            new AppParam(5, "email_subject",     "Report",          true),
            new AppParam(6, "email_body",        "Attached.",       true)
        ));
    }

    private FeeEarnerRun runFor(int usrID, int runId) {
        return new FeeEarnerRun(runId, DAY, usrID, "Name", "e@e.com",
            "fe_" + usrID + ".xlsx", new byte[]{1}, null);
    }

    @Test
    void sendForFeeEarnerFetchesRunAndDelegates() throws Exception {
        var sentCount = new AtomicInteger(0);
        var runRepo = new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int id) {
                return Optional.of(runFor(id, 1));
            }
        };
        var sender = new MailSender(config()) {
            @Override public void send(FeeEarnerRun run) { sentCount.incrementAndGet(); }
        };
        var service = new EmailService(runRepo, sender);
        service.sendForFeeEarner(100, 1, config());
        assertEquals(1, sentCount.get());
    }

    @Test
    void sendAllTracksFailures() {
        var runRepo = new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int id) {
                return Optional.of(runFor(id, 1));
            }
        };
        var sender = new MailSender(config()) {
            @Override public void send(FeeEarnerRun run) {
                throw new RuntimeException("SMTP down");
            }
        };
        var tracker = new ProgressTracker(2);
        var runs = List.of(runFor(100, 1), runFor(200, 1));
        new EmailService(runRepo, sender).sendAll(runs, config(), tracker);
        assertEquals(2, tracker.failed().get());
        assertEquals(2, tracker.failures().size());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -pl . -Dtest=EmailServiceTest -q
```

- [ ] **Step 3: Implement EmailService**

```java
package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.email.MailSender;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final RunRepository runRepo;
    private final MailSender mailSender;

    public EmailService(RunRepository runRepo, MailSender mailSender) {
        this.runRepo = runRepo;
        this.mailSender = mailSender;
    }

    public void sendForFeeEarner(int usrID, int runId, AppConfig config) {
        var run = runRepo.getMostRecent(usrID)
            .orElseThrow(() -> new IllegalStateException(
                "No FeeEarnerRun found for usrID=" + usrID + " runId=" + runId));
        try {
            mailSender.send(run);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email for usrID=" + usrID, e);
        }
    }

    public void sendAll(List<FeeEarnerRun> runs, AppConfig config, ProgressTracker tracker) {
        for (var run : runs) {
            try {
                mailSender.send(run);
                tracker.completed().incrementAndGet();
            } catch (Exception e) {
                log.error("Failed to send email for usrID={}", run.usrID(), e);
                tracker.failed().incrementAndGet();
                tracker.failures().add(
                    new FailedEntry(run.usrID(), run.feeEarner(), e.getMessage()));
            }
        }
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn test -pl . -Dtest=EmailServiceTest -q
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/javalover/feeearner/service/EmailService.java \
        src/test/java/net/javalover/feeearner/service/EmailServiceTest.java
git commit -m "feat: add EmailService (sendForFeeEarner, sendAll with failure tracking)"
```

---

## Task 8: Main.java CLI entry point

**Files:**
- Create: `src/main/java/net/javalover/feeearner/Main.java`

No unit tests — Main.java is tested via integration with a real DB. It is exercised by running the shaded JAR. Verify it compiles and the full test suite stays green.

**Bootstrap sequence** (from design spec §14):
```
args[0] = path to credential file
→ parse credentials (CredentialLoader)
→ build HikariDataSource
→ ParamRepository.loadAll() → AppConfig
→ LoggingInitialiser.init(logDir, level)
→ FeeEarnerRepository.getLeadFeeEarners() + getMatterFeeEarners() (deduplicated by usrID)
→ RunService.startRun() → runId
→ SpreadsheetService.generateAll(feeEarners, runId, today, config, tracker)
→ RunService.finishRun(runId)
→ print summary (tracker.failures())
→ exit 0; bootstrap failures exit 1
```

- [ ] **Step 1: Implement Main.java**

```java
package net.javalover.feeearner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.config.CredentialLoader;
import net.javalover.feeearner.excel.WorkbookBuilder;
import net.javalover.feeearner.logging.LoggingInitialiser;
import net.javalover.feeearner.model.FeeEarner;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.repository.*;
import net.javalover.feeearner.service.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: FeeEarnerReport <path-to-credentials-file>");
            System.exit(1);
        }

        HikariDataSource ds = null;
        try {
            var creds = CredentialLoader.load(args[0]);

            var hikariCfg = new HikariConfig();
            hikariCfg.setJdbcUrl("jdbc:sqlserver://" + creds.host() + ":" + creds.port()
                + ";databaseName=" + creds.database()
                + ";encrypt=true;trustServerCertificate=true");
            hikariCfg.setUsername(creds.username());
            hikariCfg.setPassword(creds.password());
            hikariCfg.setMaximumPoolSize(10);
            ds = new HikariDataSource(hikariCfg);

            var paramRepo       = new ParamRepository(ds);
            var config          = AppConfig.from(paramRepo.loadAll());

            LoggingInitialiser.init(config.logDir(), config.debugLevel());

            var feeEarnerRepo   = new FeeEarnerRepository(ds);
            var worksheetRepo   = new WorksheetRepository(ds);
            var archiveRepo     = new ArchiveRepository(ds);
            var runRepo         = new RunRepository(ds);
            var runService      = new RunService(runRepo);
            var spreadsheetSvc  = new SpreadsheetService(
                worksheetRepo, archiveRepo, runRepo, feeEarnerRepo, new WorkbookBuilder());

            var leadFEs   = feeEarnerRepo.getLeadFeeEarners();
            var matterFEs = feeEarnerRepo.getMatterFeeEarners();
            var feeEarners = merge(leadFEs, matterFEs);

            int runId   = runService.startRun();
            var tracker = new ProgressTracker(feeEarners.size());

            spreadsheetSvc.generateAll(feeEarners, runId, LocalDate.now(), config, tracker);
            runService.finishRun(runId);

            System.out.printf("Done. Completed: %d  Failed: %d%n",
                tracker.completed().get(), tracker.failed().get());
            tracker.failures().forEach(f ->
                System.out.printf("  FAILED usrID=%d (%s): %s%n",
                    f.usrID(), f.feeEarner(), f.errorMessage()));

        } catch (Exception e) {
            System.err.println("Bootstrap failed: " + e.getMessage());
            System.exit(1);
        } finally {
            if (ds != null) ds.close();
        }
    }

    // Keep lead entry for intersect usrIDs (they appear in both lists)
    private static List<FeeEarner> merge(List<FeeEarner> lead, List<FeeEarner> matter) {
        var result = new ArrayList<>(lead);
        var leadIds = lead.stream().map(FeeEarner::usrID).collect(Collectors.toSet());
        matter.stream()
            .filter(fe -> !leadIds.contains(fe.usrID()))
            .forEach(result::add);
        return result;
    }
}
```

- [ ] **Step 2: Compile check + full test suite**

```bash
mvn test -q
```

Expected: all tests pass (28+), 0 failures, BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/javalover/feeearner/Main.java
git commit -m "feat: add Main.java CLI entry point"
```

---

## Plan 2a Completion Checklist

- [ ] `mvn test -q` → BUILD SUCCESS, 0 failures
- [ ] `mvn package -DskipTests -q` → shaded JAR builds without error
- [ ] All new classes are in the correct packages under `net.javalover.feeearner`
- [ ] `[Type]` is column 0 in every sheet
- [ ] Freeze pane active on all sheets (top row frozen)
- [ ] Auto-filter active on all sheets
- [ ] SpreadsheetService deletes temp xlsx after inserting blob
- [ ] generateAll catches exceptions per-FE and continues; failures recorded in ProgressTracker
- [ ] Main.java exits 1 on bootstrap failure, 0 on success

---

## Next: Plan 2b — JavaFX UI

Six modal windows: ParameterEditorWindow, PreviousRunsWindow, FeeEarnerDetailWindow, SpreadsheetViewerWindow, GenerateAllWindow/EmailAllWindow, SingleGenerateWindow, SingleEmailWindow. Services are reused as-is; no logic lives in controllers.
