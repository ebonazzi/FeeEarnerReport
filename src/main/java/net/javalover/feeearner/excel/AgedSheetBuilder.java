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
        // No extra columns beyond base 17
    }
}
