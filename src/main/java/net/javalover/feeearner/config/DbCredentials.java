package net.javalover.feeearner.config;

public record DbCredentials(String hostname, String dbName, int port,
                             String username, String password) {
    /** Unencrypted connection — only valid for trusted local/intranet SQL Server. */
    public String jdbcUrl() {
        return "jdbc:sqlserver://" + hostname + ":" + port
               + ";databaseName=" + dbName
               + ";encrypt=false;trustServerCertificate=true";
    }
}
