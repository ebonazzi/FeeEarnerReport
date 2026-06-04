package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorkbookBuilderTest {

    private static final List<String> EXPECTED_SHEETS = List.of(
        "Matters-Leads Full Report", "Limitation", "Aged", "Duplicate", "High Volume"
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
                "Office", "Jur", "FE", "LA", "SFE", "TaskD", "TaskT",
                "Notes", "Owner", null, null);
        var bytes = new WorkbookBuilder().build(List.of(row), List.of(), List.of(), List.of(), List.of());
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheetAt(0);
            assertEquals("Lead", sheet.getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void firstRowIsFrozen() throws IOException {
        var bytes = new WorkbookBuilder().build(List.of(), List.of(), List.of(), List.of(), List.of());
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var info = wb.getSheetAt(0).getPaneInformation();
            assertNotNull(info);
            assertTrue(info.isFreezePane());
            assertEquals(1, info.getHorizontalSplitTopRow());
        }
    }

    @Test
    void autoFilterIsEnabled() throws IOException {
        var bytes = new WorkbookBuilder().build(List.of(), List.of(), List.of(), List.of(), List.of());
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = (org.apache.poi.xssf.usermodel.XSSFSheet) wb.getSheetAt(0);
            assertTrue(sheet.getCTWorksheet().isSetAutoFilter(),
                "Auto-filter should be set on the first sheet");
        }
    }
}
