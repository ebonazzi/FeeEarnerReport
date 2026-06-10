package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;

public class ArchiveRepository {

    private final DataSource ds;

    /** All five archive tables, in the order rows are inserted. */
    private static final List<String> ARCHIVE_TABLES = List.of(
        "report.full_task_archive",
        "report.limitation_archive",
        "report.aged_archive",
        "report.duplicate_task_archive",
        "report.high_volume_archive");

    public ArchiveRepository(DataSource ds) {
        this.ds = ds;
    }

    /**
     * Removes any existing archive rows for one fee earner within a run, across all five
     * archive tables. Single-spreadsheet regeneration reuses the existing {@code run_id}
     * (via {@code updateFeeEarnerRun}), so without this the re-insert would collide with
     * the unique clustered index {@code (day_run, run_id, usrID, row_number)}. Deleting
     * first (rather than upserting on {@code row_number}) also drops now-stale rows when a
     * regeneration produces fewer rows than before.
     */
    public void deleteForFeeEarner(int runId, LocalDate dayRun, int usrID) {
        try (var conn = ds.getConnection()) {
            for (var table : ARCHIVE_TABLES) {
                var sql = "DELETE FROM " + table +
                          " WHERE day_run = ? AND run_id = ? AND usrID = ?";
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setDate(1, Date.valueOf(dayRun));
                    stmt.setInt(2, runId);
                    stmt.setInt(3, usrID);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                "Archive delete failed for usrID=" + usrID + " run=" + runId, e);
        }
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
        if (rows == null || rows.isEmpty()) return;
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
        if (row.reportDate() != null)
            stmt.setDate(offset, Date.valueOf(row.reportDate()));
        else
            stmt.setNull(offset, java.sql.Types.DATE);
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
        if (row.taskCreatedDate() != null)
            stmt.setTimestamp(offset+14, Timestamp.valueOf(row.taskCreatedDate()));
        else
            stmt.setNull(offset+14, java.sql.Types.TIMESTAMP);
        if (row.taskDueDate() != null)
            stmt.setTimestamp(offset+15, Timestamp.valueOf(row.taskDueDate()));
        else
            stmt.setNull(offset+15, java.sql.Types.TIMESTAMP);
        stmt.setString(offset+16, row.type());
    }
}
