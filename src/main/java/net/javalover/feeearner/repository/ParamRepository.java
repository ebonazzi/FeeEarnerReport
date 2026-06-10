package net.javalover.feeearner.repository;

import net.javalover.feeearner.config.DbCredentials;
import net.javalover.feeearner.model.AppParam;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ParamRepository {

    private static final String LOAD_ALL_SQL =
            "SELECT param_id, parameter_name, parameter_value, active " +
            "FROM MCMBLIVE.report.report_param WHERE active = 1";

    private final DataSource ds;

    public ParamRepository(DataSource ds) {
        this.ds = ds;
    }

    public List<AppParam> loadAll() {
        try (var conn = ds.getConnection()) {
            return loadAll(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parameters", e);
        }
    }

    /**
     * Loads parameters over a one-shot {@link DriverManager} connection — used during
     * bootstrap, before the HikariCP pool exists. This deliberately avoids HikariCP
     * (which logs via SLF4J on construction); logging must be configured from these
     * params <em>before</em> anything triggers Rainbow Gum's lazy initialisation.
     */
    public static List<AppParam> loadAllBootstrap(DbCredentials creds) {
        try (var conn = DriverManager.getConnection(
                creds.jdbcUrl(), creds.username(), creds.password())) {
            return loadAll(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parameters (bootstrap)", e);
        }
    }

    private static List<AppParam> loadAll(Connection conn) throws SQLException {
        try (var stmt = conn.prepareStatement(LOAD_ALL_SQL);
             var rs   = stmt.executeQuery()) {
            var list = new ArrayList<AppParam>();
            while (rs.next()) list.add(mapParam(rs));
            return list;
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

    private static AppParam mapParam(ResultSet rs) throws SQLException {
        return new AppParam(
            rs.getInt("param_id"),
            rs.getString("parameter_name"),
            rs.getString("parameter_value"),
            rs.getBoolean("active")
        );
    }
}
