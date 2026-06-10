package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;

public class ArchiveRepository {

    /** All five archive tables, in the order rows are inserted. */
    private static final List<String> ARCHIVE_TABLES = List.of(
        "report.full_task_archive",
        "report.limitation_archive",
        "report.aged_archive",
        "report.duplicate_task_archive",
        "report.high_volume_archive");

    /**
     * Writes one fee earner's archive rows across all five tables, on the caller's
     * connection. This is a transaction <em>participant</em>: it never commits, rolls
     * back, or closes {@code conn} — {@code SpreadsheetService} owns the transaction so
     * the archive rows and the {@code FeeEarnersRun} blob commit together (all-or-nothing).
     *
     * <p>When {@code replaceExisting} is true (single-spreadsheet regeneration, which
     * reuses the existing {@code run_id} via {@code updateFeeEarnerRun}), prior rows for
     * this {@code (day_run, run_id, usrID)} are deleted first so the re-insert doesn't
     * collide with the unique clustered index {@code (day_run, run_id, usrID, row_number)}.
     * Deleting (rather than upserting on {@code row_number}) also drops now-stale rows when
     * a regeneration yields fewer rows. A fresh bulk run gets a new {@code run_id} and
     * passes {@code false}.
     */
    public void writeForFeeEarner(Connection conn, int runId, LocalDate dayRun, int usrID,
                                  boolean replaceExisting,
                                  List<FullTaskRow> fullTask,
                                  List<LimitationRow> limitation,
                                  List<AgedRow> aged,
                                  List<DuplicateRow> duplicate,
                                  List<HighVolumeRow> highVolume) throws SQLException {
        if (replaceExisting)
            deleteForFeeEarner(conn, runId, dayRun, usrID);
        insertFullTaskRows(conn, runId, dayRun, usrID, fullTask);
        insertLimitationRows(conn, runId, dayRun, usrID, limitation);
        insertAgedRows(conn, runId, dayRun, usrID, aged);
        insertDuplicateRows(conn, runId, dayRun, usrID, duplicate);
        insertHighVolumeRows(conn, runId, dayRun, usrID, highVolume);
    }

    private void deleteForFeeEarner(Connection conn, int runId, LocalDate dayRun, int usrID)
            throws SQLException {
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
    }

    private void insertFullTaskRows(Connection conn, int runId, LocalDate dayRun, int usrID,
                                    List<FullTaskRow> rows) throws SQLException {
        var sql = "INSERT INTO report.full_task_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Task Complete],[Type]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?)";
        batchInsert(conn, sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseRowParams(stmt, row, 5);
        });
    }

    private void insertLimitationRows(Connection conn, int runId, LocalDate dayRun, int usrID,
                                      List<LimitationRow> rows) throws SQLException {
        var sql = "INSERT INTO report.limitation_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Type],[Key Words]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        batchInsert(conn, sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseRowParams(stmt, row, 5);
            stmt.setString(22, row.keyWords());
        });
    }

    private void insertAgedRows(Connection conn, int runId, LocalDate dayRun, int usrID,
                                List<AgedRow> rows) throws SQLException {
        var sql = "INSERT INTO report.aged_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Type]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        batchInsert(conn, sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseRowParams(stmt, row, 5);
        });
    }

    private void insertDuplicateRows(Connection conn, int runId, LocalDate dayRun, int usrID,
                                     List<DuplicateRow> rows) throws SQLException {
        var sql = "INSERT INTO report.duplicate_task_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Type],[Duplicate]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        batchInsert(conn, sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseRowParams(stmt, row, 5);
            stmt.setString(22, row.duplicate());
        });
    }

    private void insertHighVolumeRows(Connection conn, int runId, LocalDate dayRun, int usrID,
                                      List<HighVolumeRow> rows) throws SQLException {
        var sql = "INSERT INTO report.high_volume_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Type],[Matter Row Count]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        batchInsert(conn, sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
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

    /** Batch-inserts on the caller's connection — does not open, commit, or close it. */
    private <T extends BaseRow> void batchInsert(
            Connection conn, String sql, int runId, LocalDate dayRun, int usrID,
            List<T> rows, RowBinder<T> binder) throws SQLException {
        if (rows == null || rows.isEmpty()) return;
        try (var stmt = conn.prepareStatement(sql)) {
            int rowNum = 1;
            for (var row : rows) {
                binder.bind(stmt, row, rowNum++);
                stmt.addBatch();
            }
            stmt.executeBatch();
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
