package net.javalover.feeearner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.javalover.feeearner.config.CredentialLoader;

import javax.sql.DataSource;
import java.util.Properties;

public final class TestDataSourceFactory {

    private TestDataSourceFactory() {}

    public static DataSource create() {
        var props = new Properties();
        try (var in = TestDataSourceFactory.class
                .getResourceAsStream("/test-credentials.properties")) {
            if (in == null)
                throw new IllegalStateException(
                    "test-credentials.properties not found in src/test/resources/");
            props.load(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        var creds = CredentialLoader.load(
            TestDataSourceFactory.class
                .getResource("/test-credentials.properties")
                .getPath());

        var config = new HikariConfig();
        config.setJdbcUrl(creds.jdbcUrl());
        config.setUsername(creds.username());
        config.setPassword(creds.password());
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }
}
