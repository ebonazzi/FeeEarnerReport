package net.javalover.feeearner.config;

public record DbCredentials(String hostname, String dbName, int port,
                             String username, String password) {
    public String jdbcUrl() {
        return "jdbc:sqlserver://" + hostname + ":" + port
               + ";databaseName=" + dbName
               + ";encrypt=false;trustServerCertificate=true";
    }
}
