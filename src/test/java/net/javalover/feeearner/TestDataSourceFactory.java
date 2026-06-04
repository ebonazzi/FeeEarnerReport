package net.javalover.feeearner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.javalover.feeearner.config.CredentialLoader;

import javax.sql.DataSource;
import java.net.URL;

public final class TestDataSourceFactory {

    private TestDataSourceFactory() {}

    public static DataSource create() {
        URL resource = TestDataSourceFactory.class
                .getResource("/test-credentials.properties");
        if (resource == null)
            throw new IllegalStateException(
                "test-credentials.properties not found in src/test/resources/");

        var creds = CredentialLoader.load(resource.getPath());

        var config = new HikariConfig();
        config.setJdbcUrl(creds.jdbcUrl());
        config.setUsername(creds.username());
        config.setPassword(creds.password());
        config.setMaximumPoolSize(2);
        config.setConnectionTimeout(5_000);
        return new HikariDataSource(config);
    }
}
