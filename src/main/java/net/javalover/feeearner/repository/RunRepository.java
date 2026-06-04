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

    public void insertFeeEarnerRun(FeeEarnerRun run) {
        var sql = "INSERT INTO report.FeeEarnersRun " +
                  "(run_id, day_run, usrID, [Fee Earner], usrEmail, " +
                  " excel_filename, excel_spreadsheet, stored_at) " +
                  "VALUES (?,?,?,?,?,?,?,GETDATE())";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
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
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert FeeEarnerRun for usrID=" + run.usrID(), e);
        }
    }

    public void updateFeeEarnerRun(int runId, int usrID, String filename, byte[] xlsx) {
        var sql = "UPDATE report.FeeEarnersRun " +
                  "SET excel_filename=?, excel_spreadsheet=?, stored_at=GETDATE() " +
                  "WHERE run_id=? AND usrID=?";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, filename);
            stmt.setBytes(2, xlsx);
            stmt.setInt(3, runId);
            stmt.setInt(4, usrID);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update FeeEarnerRun for usrID=" + usrID, e);
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
