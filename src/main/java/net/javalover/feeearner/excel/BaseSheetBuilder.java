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
        int rowNum = 1;
        for (T data : rows) {
            var r = sheet.createRow(rowNum++);
            writeBaseColumns(r, data, dateStyle);
            writeExtraColumns(r, data, dateStyle);
        }
        for (int i = 0; i <= lastCol; i++) {
            sheet.autoSizeColumn(i);
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
        if (value != null) {
            row.createCell(col).setCellValue(value);
        }
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
