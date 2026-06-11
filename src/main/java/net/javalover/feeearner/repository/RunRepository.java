package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.FeeEarnerRun;
import net.javalover.feeearner.model.RunInfo;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RunRepository {

    private final DataSource ds;

    public RunRepository(DataSource ds) {
        this.ds = ds;
    }

    public int createRun() {
        var sql = "INSERT INTO report.spreadsheet_run (day_run, started_at) " +
                  "OUTPUT INSERTED.run_id VALUES (CAST(GETDATE() AS DATE), GETDATE())";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs   = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
            throw new RuntimeException("INSERT returned no generated run_id");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create run", e);
        }
    }

    public void closeRun(int runId) {
        var sql = "UPDATE report.spreadsheet_run SET finished_at = GETDATE() WHERE run_id = ?";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, runId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close run " + runId, e);
        }
    }

    /**
     * Inserts the stored spreadsheet row on the caller's connection — a transaction
     * participant (does not commit/close), so it commits together with the archive rows
     * written for the same fee earner. See {@code SpreadsheetService.doGenerate}.
     */
    public void insertFeeEarnerRun(Connection conn, FeeEarnerRun run) throws SQLException {
        var sql = "INSERT INTO report.FeeEarnersRun " +
                  "(run_id, day_run, usrID, [Fee Earner], usrEmail, " +
                  " excel_filename, excel_spreadsheet, stored_at) " +
                  "VALUES (?,?,?,?,?,?,?,GETDATE())";
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, run.runId());
            stmt.setDate(2, Date.valueOf(run.dayRun()));
            stmt.setInt(3, run.usrID());
            stmt.setString(4, run.feeEarner());
            stmt.setString(5, run.usrEmail());
            stmt.setString(6, run.excelFilename());
            if (run.excelSpreadsheet() != null)
                stmt.setBytes(7, run.excelSpreadsheet());
            else
                stmt.setNull(7, Types.VARBINARY);
            stmt.executeUpdate();
        }
    }

    /** Updates the stored spreadsheet row on the caller's connection (transaction participant). */
    public void updateFeeEarnerRun(Connection conn, int runId, int usrID,
                                   String filename, byte[] xlsx) throws SQLException {
        var sql = "UPDATE report.FeeEarnersRun " +
                  "SET excel_filename=?, excel_spreadsheet=?, stored_at=GETDATE() " +
                  "WHERE run_id=? AND usrID=?";
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, filename);
            stmt.setBytes(2, xlsx);
            stmt.setInt(3, runId);
            stmt.setInt(4, usrID);
            stmt.executeUpdate();
        }
    }

    /**
     * Replaces the stored spreadsheet of a fee earner's <em>most recent</em> run row
     * (highest run_id) and stamps {@code stored_at = now}. Used by past-run regeneration:
     * the regenerated workbook becomes the current spreadsheet, regardless of which run it
     * was rebuilt from. Manages its own connection (not a transaction participant).
     */
    public void updateMostRecentFeeEarnerBlob(int usrID, String filename, byte[] xlsx) {
        var sql = "UPDATE report.FeeEarnersRun " +
                  "SET excel_filename=?, excel_spreadsheet=?, stored_at=GETDATE() " +
                  "WHERE run_id = (SELECT TOP 1 run_id FROM report.FeeEarnersRun " +
                  "                WHERE usrID=? ORDER BY run_id DESC) " +
                  "  AND usrID=?";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, filename);
            stmt.setBytes(2, xlsx);
            stmt.setInt(3, usrID);
            stmt.setInt(4, usrID);
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new IllegalStateException(
                    "No FeeEarnersRun row to update for usrID=" + usrID +
                    " (regeneration requires an existing run row)");
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                "Failed to update most-recent blob for usrID=" + usrID, e);
        }
    }

    public List<RunInfo> getAllRuns() {
        var sql = "SELECT run_id, day_run, started_at, finished_at " +
                  "FROM report.spreadsheet_run ORDER BY started_at DESC";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs   = stmt.executeQuery()) {
            var result = new ArrayList<RunInfo>();
            while (rs.next()) result.add(mapRunInfo(rs));
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all runs", e);
        }
    }

    public List<FeeEarnerRun> getFeeEarnerRunsForRun(int runId) {
        var sql = "SELECT run_id, day_run, usrID, [Fee Earner], usrEmail, " +
                  "excel_filename, excel_spreadsheet, stored_at " +
                  "FROM report.FeeEarnersRun WHERE run_id = ? " +
                  "ORDER BY [Fee Earner]";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, runId);
            try (var rs = stmt.executeQuery()) {
                var result = new ArrayList<FeeEarnerRun>();
                while (rs.next()) result.add(mapRun(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load fee earner runs for runId=" + runId, e);
        }
    }

    public Optional<FeeEarnerRun> getMostRecent(int usrID) {
        var sql = "SELECT TOP 1 run_id, day_run, usrID, [Fee Earner], usrEmail, " +
                  "excel_filename, excel_spreadsheet, stored_at " +
                  "FROM report.FeeEarnersRun WHERE usrID = ? " +
                  "ORDER BY run_id DESC";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, usrID);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapRun(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch most recent run for usrID=" + usrID, e);
        }
    }

    private RunInfo mapRunInfo(ResultSet rs) throws SQLException {
        var finishedAt = rs.getTimestamp("finished_at");
        var startedAt  = rs.getTimestamp("started_at");
        if (startedAt == null) {
            throw new RuntimeException("started_at is NULL for run_id=" + rs.getInt("run_id"));
        }
        return new RunInfo(
            rs.getInt("run_id"),
            rs.getDate("day_run").toLocalDate(),
            startedAt.toLocalDateTime(),
            finishedAt != null ? finishedAt.toLocalDateTime() : null
        );
    }

    private FeeEarnerRun mapRun(ResultSet rs) throws SQLException {
        var storedAt = rs.getTimestamp("stored_at");
        return new FeeEarnerRun(
            rs.getInt("run_id"),
            rs.getDate("day_run").toLocalDate(),
            rs.getInt("usrID"),
            rs.getString("Fee Earner"),
            rs.getString("usrEmail"),
            rs.getString("excel_filename"),
            rs.getBytes("excel_spreadsheet"),
            storedAt != null ? storedAt.toLocalDateTime() : null
        );
    }
}
