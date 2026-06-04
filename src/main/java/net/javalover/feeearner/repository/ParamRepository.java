package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.AppParam;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ParamRepository {

    private final DataSource ds;

    public ParamRepository(DataSource ds) {
        this.ds = ds;
    }

    public List<AppParam> loadAll() {
        var sql = "SELECT param_id, parameter_name, parameter_value, active " +
                  "FROM MCMBLIVE.report.report_param WHERE active = 1";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs   = stmt.executeQuery()) {
            var list = new ArrayList<AppParam>();
            while (rs.next()) list.add(mapParam(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parameters", e);
        }
    }

    public void save(AppParam param) {
        var sql = "UPDATE MCMBLIVE.report.report_param " +
                  "SET parameter_value = ? WHERE param_id = ?";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, param.value());
            stmt.setInt(2, param.paramId());
            int rows = stmt.executeUpdate();
            if (rows == 0)
                throw new RuntimeException("No parameter found with id=" + param.paramId());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save parameter id=" + param.paramId(), e);
        }
    }

    private AppParam mapParam(ResultSet rs) throws SQLException {
        return new AppParam(
            rs.getInt("param_id"),
            rs.getString("parameter_name"),
            rs.getString("parameter_value"),
            rs.getBoolean("active")
        );
    }
}
