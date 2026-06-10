package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.DuplicateRow;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;

import java.util.List;

class DuplicateSheetBuilder extends BaseSheetBuilder<DuplicateRow> {

    private static final List<String> HEADERS = List.of(
        "[Type]", "[Matter Number]", "[Matter Name/Description]",
        "Department", "[Practice Code]", "[Office Name]", "Jurisdiction",
        "[Fee Earner]", "[Legal Assistant]", "[Supervising Fee Earner]",
        "[Task Description]", "[Task Type]", "[Task Notes]", "[Task Owner]",
        "[Task Created Date]", "[Task Due Date]", "[Duplicate]"
    );

    @Override
    List<String> headers() { return HEADERS; }

    @Override
    void writeExtraColumns(XSSFRow row, DuplicateRow data, XSSFCellStyle dateStyle) {
        setString(row, 16, data.duplicate());
    }
}
