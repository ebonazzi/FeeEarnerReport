package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WorksheetRepository {

    private final DataSource ds;

    public WorksheetRepository(DataSource ds) {
        this.ds = ds;
    }

    // ── Lead functions ────────────────────────────────────────────────────────

    public List<FullTaskRow> getLeadFullTaskData(int usrID) {
        return queryFull("SELECT * FROM report.fn_VIC_Lead_Full_Task_Data(?)", usrID);
    }

    public List<LimitationRow> getLeadLimitation(int usrID) {
        return queryLimitation("SELECT * FROM report.fn_VIC_Lead_Limitation_Report(?)", usrID);
    }

    public List<AgedRow> getLeadAged(int usrID) {
        return queryAged("SELECT * FROM report.fn_VIC_Lead_Aged_Report(?)", usrID);
    }

    public List<DuplicateRow> getLeadDuplicate(int usrID) {
        return queryDuplicate("SELECT * FROM report.fn_VIC_Lead_Duplicate_Tasks_Report(?)", usrID);
    }

    public List<HighVolumeRow> getLeadHighVolume(int usrID) {
        return queryHighVolume("SELECT * FROM report.fn_VIC_Lead_High_Volume_Report(?)", usrID);
    }

    // ── Matter functions ──────────────────────────────────────────────────────

    public List<FullTaskRow> getMatterFullTaskData(int usrID) {
        return queryFull("SELECT * FROM report.fn_VIC_Matter_Full_Task_Data(?)", usrID);
    }

    public List<LimitationRow> getMatterLimitation(int usrID) {
        return queryLimitation("SELECT * FROM report.fn_VIC_Matter_Limitation_Report(?)", usrID);
    }

    public List<AgedRow> getMatterAged(int usrID) {
        return queryAged("SELECT * FROM report.fn_VIC_Matter_Aged_Report(?)", usrID);
    }

    public List<DuplicateRow> getMatterDuplicate(int usrID) {
        return queryDuplicate("SELECT * FROM report.fn_VIC_Matter_Duplicate_Tasks_Report(?)", usrID);
    }

    public List<HighVolumeRow> getMatterHighVolume(int usrID) {
        return queryHighVolume("SELECT * FROM report.fn_VIC_Matter_High_Volume_Report(?)", usrID);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private List<FullTaskRow> queryFull(String sql, int usrID) {
        return query(sql, usrID, rs -> new FullTaskRow(
            rs.getString("Type"),
            toLocalDate(rs, "Report Date"),
            rs.getString("Matter Number"),
            rs.getString("Matter Name/Description"),
            rs.getString("Department"),
            rs.getString("Practice Code"),
            rs.getString("Office Name"),
            rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"),
            rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"),
            rs.getString("Task Description"),
            rs.getString("Task Type"),
            rs.getString("Task Notes"),
            rs.getString("Task Owner"),
            toLocalDateTime(rs, "Task Created Date"),
            toLocalDateTime(rs, "Task Due Date")
        ));
    }

    private List<LimitationRow> queryLimitation(String sql, int usrID) {
        return query(sql, usrID, rs -> new LimitationRow(
            rs.getString("Type"),
            toLocalDate(rs, "Report Date"),
            rs.getString("Matter Number"),
            rs.getString("Matter Name/Description"),
            rs.getString("Department"),
            rs.getString("Practice Code"),
            rs.getString("Office Name"),
            rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"),
            rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"),
            rs.getString("Task Description"),
            rs.getString("Task Type"),
            rs.getString("Task Notes"),
            rs.getString("Task Owner"),
            toLocalDateTime(rs, "Task Created Date"),
            toLocalDateTime(rs, "Task Due Date"),
            rs.getString("Key Words")
        ));
    }

    private List<AgedRow> queryAged(String sql, int usrID) {
        return query(sql, usrID, rs -> new AgedRow(
            rs.getString("Type"),
            toLocalDate(rs, "Report Date"),
            rs.getString("Matter Number"),
            rs.getString("Matter Name/Description"),
            rs.getString("Department"),
            rs.getString("Practice Code"),
            rs.getString("Office Name"),
            rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"),
            rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"),
            rs.getString("Task Description"),
            rs.getString("Task Type"),
            rs.getString("Task Notes"),
            rs.getString("Task Owner"),
            toLocalDateTime(rs, "Task Created Date"),
            toLocalDateTime(rs, "Task Due Date")
        ));
    }

    private List<DuplicateRow> queryDuplicate(String sql, int usrID) {
        return query(sql, usrID, rs -> new DuplicateRow(
            rs.getString("Type"),
            toLocalDate(rs, "Report Date"),
            rs.getString("Matter Number"),
            rs.getString("Matter Name/Description"),
            rs.getString("Department"),
            rs.getString("Practice Code"),
            rs.getString("Office Name"),
            rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"),
            rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"),
            rs.getString("Task Description"),
            rs.getString("Task Type"),
            rs.getString("Task Notes"),
            rs.getString("Task Owner"),
            toLocalDateTime(rs, "Task Created Date"),
            toLocalDateTime(rs, "Task Due Date"),
            rs.getString("Duplicate")
        ));
    }

    private List<HighVolumeRow> queryHighVolume(String sql, int usrID) {
        return query(sql, usrID, rs -> new HighVolumeRow(
            rs.getString("Type"),
            toLocalDate(rs, "Report Date"),
            rs.getString("Matter Number"),
            rs.getString("Matter Name/Description"),
            rs.getString("Department"),
            rs.getString("Practice Code"),
            rs.getString("Office Name"),
            rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"),
            rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"),
            rs.getString("Task Description"),
            rs.getString("Task Type"),
            rs.getString("Task Notes"),
            rs.getString("Task Owner"),
            toLocalDateTime(rs, "Task Created Date"),
            toLocalDateTime(rs, "Task Due Date"),
            rs.getInt("Matter Row Count")
        ));
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private <T> List<T> query(String sql, int usrID, RowMapper<T> mapper) {
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, usrID);
            var rs = stmt.executeQuery();
            var list = new ArrayList<T>();
            while (rs.next()) list.add(mapper.map(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Worksheet query failed: " + sql, e);
        }
    }

    private static LocalDate toLocalDate(ResultSet rs, String col) throws SQLException {
        var d = rs.getDate(col);
        return d != null ? d.toLocalDate() : null;
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String col) throws SQLException {
        var ts = rs.getTimestamp(col);
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
