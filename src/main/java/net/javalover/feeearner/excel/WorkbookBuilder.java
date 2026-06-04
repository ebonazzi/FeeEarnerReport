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
            var fmt = wb.createDataFormat();
            dateStyle.setDataFormat(fmt.getFormat("dd/mm/yyyy"));

            new FullTaskSheetBuilder().build(
                wb.createSheet("Matters-Leads Full Report"), fullTask, dateStyle);
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
