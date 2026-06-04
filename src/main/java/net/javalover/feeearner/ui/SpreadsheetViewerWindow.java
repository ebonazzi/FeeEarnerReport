package net.javalover.feeearner.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.javalover.feeearner.model.FeeEarnerRun;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class SpreadsheetViewerWindow {

    private final FeeEarnerRun run;

    public SpreadsheetViewerWindow(FeeEarnerRun run) {
        this.run = run;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Spreadsheet Viewer — " + run.excelFilename());

        var content = buildContent();

        var root = new BorderPane(content);
        root.setPadding(new Insets(8));

        stage.setScene(new Scene(root, 900, 600));
        stage.showAndWait();
    }

    private javafx.scene.Node buildContent() {
        byte[] blob = run.excelSpreadsheet();
        if (blob == null || blob.length == 0) {
            return new Label("No spreadsheet data available");
        }
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(blob))) {
            var fmt = new DataFormatter();
            var tabPane = new TabPane();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                var sheet = wb.getSheetAt(s);
                var table = new TableView<ObservableList<String>>();
                table.setEditable(false);

                var headerRow = sheet.getRow(0);
                int colCount = headerRow != null ? headerRow.getLastCellNum() : 0;
                for (int c = 0; c < colCount; c++) {
                    final int colIdx = c;
                    String header = headerRow.getCell(c) != null
                        ? fmt.formatCellValue(headerRow.getCell(c)) : "";
                    var col = new TableColumn<ObservableList<String>, String>(header);
                    col.setCellValueFactory(cellData -> {
                        var list = cellData.getValue();
                        return new SimpleStringProperty(
                            colIdx < list.size() ? list.get(colIdx) : "");
                    });
                    col.setPrefWidth(120);
                    table.getColumns().add(col);
                }

                var items = FXCollections.<ObservableList<String>>observableArrayList();
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    var row = sheet.getRow(r);
                    ObservableList<String> rowData = FXCollections.observableArrayList();
                    for (int c = 0; c < colCount; c++) {
                        rowData.add(row != null && row.getCell(c) != null
                            ? fmt.formatCellValue(row.getCell(c)) : "");
                    }
                    items.add(rowData);
                }
                table.setItems(items);

                var tab = new Tab(wb.getSheetName(s), table);
                tab.setClosable(false);
                tabPane.getTabs().add(tab);
            }
            return tabPane;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read spreadsheet", e);
        }
    }
}
