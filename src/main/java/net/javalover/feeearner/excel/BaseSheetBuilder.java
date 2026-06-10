package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.BaseRow;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

abstract class BaseSheetBuilder<T extends BaseRow> {

    /** Cap column width so a long cell never spans the whole screen (no wrapping, just clipped). */
    private static final int MAX_COLUMN_WIDTH_CHARS = 70;

    abstract List<String> headers();

    abstract void writeExtraColumns(XSSFRow row, T data, XSSFCellStyle dateStyle);

    void build(XSSFSheet sheet, List<T> rows, XSSFCellStyle dateStyle) {
        var wb = (XSSFWorkbook) sheet.getWorkbook();
        var boldStyle = createBoldStyle(wb);
        writeHeaderRow(sheet, boldStyle);
        int lastCol = headers().size() - 1;
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, lastCol));
        int rowNum = 1;
        for (T data : rows) {
            var r = sheet.createRow(rowNum++);
            writeBaseColumns(r, data, dateStyle);
            writeExtraColumns(r, data, dateStyle);
        }
        int maxWidth = MAX_COLUMN_WIDTH_CHARS * 256;   // POI width unit = 1/256th of a character
        for (int i = 0; i <= lastCol; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) > maxWidth) {
                sheet.setColumnWidth(i, maxWidth);
            }
        }
    }

    private void writeHeaderRow(XSSFSheet sheet, XSSFCellStyle boldStyle) {
        var headerRow = sheet.createRow(0);
        var hdrs = headers();
        for (int i = 0; i < hdrs.size(); i++) {
            var cell = headerRow.createCell(i);
            cell.setCellValue(displayHeader(hdrs.get(i)));
            cell.setCellStyle(boldStyle);
        }
    }

    /** Header names mirror the SQL column names; strip the [] SQL Server quoting for display. */
    private static String displayHeader(String header) {
        return header.replace("[", "").replace("]", "");
    }

    private XSSFCellStyle createBoldStyle(XSSFWorkbook wb) {
        var style = wb.createCellStyle();
        var font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    // Report Date is intentionally omitted from the spreadsheet (still archived in the DB).
    // Base columns are 0..15; subclasses add their extra column at index 16.
    protected void writeBaseColumns(XSSFRow row, BaseRow data, XSSFCellStyle dateStyle) {
        setString(row, 0, data.type());
        setString(row, 1, data.matterNumber());
        setString(row, 2, data.matterNameDescription());
        setString(row, 3, data.department());
        setString(row, 4, data.practiceCode());
        setString(row, 5, data.officeName());
        setString(row, 6, data.jurisdiction());
        setString(row, 7, data.feeEarner());
        setString(row, 8, data.legalAssistant());
        setString(row, 9, data.supervisingFeeEarner());
        setString(row, 10, data.taskDescription());
        setString(row, 11, data.taskType());
        setString(row, 12, data.taskNotes());
        setString(row, 13, data.taskOwner());
        setDateTime(row, 14, data.taskCreatedDate(), dateStyle);
        setDateTime(row, 15, data.taskDueDate(), dateStyle);
    }

    protected void setString(XSSFRow row, int col, String value) {
        if (value != null) {
            row.createCell(col).setCellValue(value);
        }
    }

    protected void setDate(XSSFRow row, int col, LocalDate date, XSSFCellStyle style) {
        if (date != null) {
            var cell = row.createCell(col);
            cell.setCellValue(date);
            cell.setCellStyle(style);
        }
    }

    protected void setDateTime(XSSFRow row, int col, LocalDateTime dt, XSSFCellStyle style) {
        if (dt != null) {
            var cell = row.createCell(col);
            cell.setCellValue(dt);
            cell.setCellStyle(style);
        }
    }
}
