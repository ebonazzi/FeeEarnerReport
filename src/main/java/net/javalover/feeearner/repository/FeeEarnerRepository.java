package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.FeeEarner;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class FeeEarnerRepository {

    private final DataSource ds;

    public FeeEarnerRepository(DataSource ds) {
        this.ds = ds;
    }

    public List<FeeEarner> getLeadFeeEarners() {
        return query("SELECT usrID, [Fee Earner], usrEmail, usrActive, [Type] " +
                     "FROM report.fn_VIC_Lead_Active_FeeEarners()");
    }

    public List<FeeEarner> getMatterFeeEarners() {
        return query("SELECT usrID, [Fee Earner], usrEmail, usrActive, [Type] " +
                     "FROM report.fn_VIC_Matter_Active_FeeEarners()");
    }

    public Set<Integer> getIntersectUserIds() {
        var sql = "SELECT usrID FROM report.fn_VIC_Lead_Active_FeeEarners() " +
                  "INTERSECT " +
                  "SELECT usrID FROM report.fn_VIC_Matter_Active_FeeEarners()";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs   = stmt.executeQuery()) {
            var ids = new HashSet<Integer>();
            while (rs.next()) ids.add(rs.getInt("usrID"));
            return ids;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch intersect fee earners", e);
        }
    }

    private List<FeeEarner> query(String sql) {
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs   = stmt.executeQuery()) {
            var list = new ArrayList<FeeEarner>();
            while (rs.next()) list.add(mapFeeEarner(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch fee earners", e);
        }
    }

    private FeeEarner mapFeeEarner(ResultSet rs) throws SQLException {
        return new FeeEarner(
            rs.getInt("usrID"),
            rs.getString("Fee Earner"),
            rs.getString("usrEmail"),
            rs.getBoolean("usrActive"),
            rs.getString("Type")
        );
    }
}
