package net.javalover.feeearner.config;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public final class CredentialLoader {

    private CredentialLoader() {}

    public static DbCredentials load(String filePath) {
        var props = new Properties();
        try (var reader = new FileReader(filePath)) {
            props.load(reader);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read credential file: " + filePath, e);
        }
        return new DbCredentials(
            require(props, "sqlserver_hostname", filePath),
            require(props, "sqlserver_dbname", filePath),
            Integer.parseInt(require(props, "sqlserver_portnumber", filePath)),
            require(props, "sqlserver_username", filePath),
            require(props, "sqlserver_password", filePath)
        );
    }

    private static String require(Properties props, String key, String filePath) {
        var value = props.getProperty(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(
                "Missing required key '" + key + "' in credential file: " + filePath);
        return value.trim();
    }
}
