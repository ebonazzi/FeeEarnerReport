package net.javalover.feeearner.excel;

import net.javalover.feeearner.model.LimitationRow;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;

import java.util.List;

class LimitationSheetBuilder extends BaseSheetBuilder<LimitationRow> {

    private static final List<String> HEADERS = List.of(
        "[Type]", "[Matter Number]", "[Matter Name/Description]",
        "Department", "[Practice Code]", "[Office Name]", "Jurisdiction",
        "[Fee Earner]", "[Legal Assistant]", "[Supervising Fee Earner]",
        "[Task Description]", "[Task Type]", "[Task Notes]", "[Task Owner]",
        "[Task Created Date]", "[Task Due Date]", "[Key Words]"
    );

    @Override
    List<String> headers() { return HEADERS; }

    @Override
    void writeExtraColumns(XSSFRow row, LimitationRow data, XSSFCellStyle dateStyle) {
        setString(row, 16, data.keyWords());
    }
}
