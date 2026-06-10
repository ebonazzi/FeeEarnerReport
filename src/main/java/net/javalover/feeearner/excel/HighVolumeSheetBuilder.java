package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.HighVolumeRow;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;

import java.util.List;

class HighVolumeSheetBuilder extends BaseSheetBuilder<HighVolumeRow> {

    private static final List<String> HEADERS = List.of(
        "[Type]", "[Matter Number]", "[Matter Name/Description]",
        "Department", "[Practice Code]", "[Office Name]", "Jurisdiction",
        "[Fee Earner]", "[Legal Assistant]", "[Supervising Fee Earner]",
        "[Task Description]", "[Task Type]", "[Task Notes]", "[Task Owner]",
        "[Task Created Date]", "[Task Due Date]", "[Matter Row Count]"
    );

    @Override
    List<String> headers() { return HEADERS; }

    @Override
    void writeExtraColumns(XSSFRow row, HighVolumeRow data, XSSFCellStyle dateStyle) {
        row.createCell(16).setCellValue(data.matterRowCount());
    }
}
