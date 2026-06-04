package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;

public class ArchiveRepository {

    private final DataSource ds;

    public ArchiveRepository(DataSource ds) {
        this.ds = ds;
    }

    public void insertFullTaskRows(int runId, LocalDate dayRun, int usrID,
                                   List<FullTaskRow> rows) {
        var sql = "INSERT INTO report.full_task_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Task Complete],[Type]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?)";
        batchInsert(sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseRowParams(stmt, row, 5);
        });
    }

    public void insertLimitationRows(int runId, LocalDate dayRun, int usrID,
                                     List<LimitationRow> rows) {
        var sql = "INSERT INTO report.limitation_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Type],[Key Words]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        batchInsert(sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseRowParams(stmt, row, 5);
            stmt.setString(22, row.keyWords());
        });
    }

    public void insertAgedRows(int runId, LocalDate dayRun, int usrID,
                               List<AgedRow> rows) {
        var sql = "INSERT INTO report.aged_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Type]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        batchInsert(sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseRowParams(stmt, row, 5);
        });
    }

    public void insertDuplicateRows(int runId, LocalDate dayRun, int usrID,
                                    List<DuplicateRow> rows) {
        var sql = "INSERT INTO report.duplicate_task_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Type],[Duplicate]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        batchInsert(sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseRowParams(stmt, row, 5);
            stmt.setString(22, row.duplicate());
        });
    }

    public void insertHighVolumeRows(int runId, LocalDate dayRun, int usrID,
                                     List<HighVolumeRow> rows) {
        var sql = "INSERT INTO report.high_volume_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Type],[Matter Row Count]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        batchInsert(sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseRowParams(stmt, row, 5);
            stmt.setInt(22, row.matterRowCount());
        });
    }

    @FunctionalInterface
    private interface RowBinder<T extends BaseRow> {
        void bind(PreparedStatement stmt, T row, int rowNum) throws SQLException;
    }

    private <T extends BaseRow> void batchInsert(
            String sql, int runId, LocalDate dayRun, int usrID,
            List<T> rows, RowBinder<T> binder) {
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            int rowNum = 1;
            for (var row : rows) {
                binder.bind(stmt, row, rowNum++);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Archive batch insert failed", e);
        }
    }

    private static void setBaseRowParams(PreparedStatement stmt, BaseRow row, int offset)
            throws SQLException {
        stmt.setDate(offset,     row.reportDate() != null ? Date.valueOf(row.reportDate()) : null);
        stmt.setString(offset+1, row.matterNumber());
        stmt.setString(offset+2, row.matterNameDescription());
        stmt.setString(offset+3, row.department());
        stmt.setString(offset+4, row.practiceCode());
        stmt.setString(offset+5, row.officeName());
        stmt.setString(offset+6, row.jurisdiction());
        stmt.setString(offset+7, row.feeEarner());
        stmt.setString(offset+8, row.legalAssistant());
        stmt.setString(offset+9, row.supervisingFeeEarner());
        stmt.setString(offset+10, row.taskDescription());
        stmt.setString(offset+11, row.taskType());
        stmt.setString(offset+12, row.taskNotes());
        stmt.setString(offset+13, row.taskOwner());
        Timestamp created = row.taskCreatedDate() != null
            ? Timestamp.valueOf(row.taskCreatedDate()) : null;
        stmt.setTimestamp(offset+14, created);
        Timestamp due = row.taskDueDate() != null
            ? Timestamp.valueOf(row.taskDueDate()) : null;
        stmt.setTimestamp(offset+15, due);
        stmt.setString(offset+16, row.type());
    }
}
