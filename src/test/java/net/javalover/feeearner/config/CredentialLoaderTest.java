package net.javalover.feeearner.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CredentialLoaderTest {

    @Test
    void loadsValidCredentialFile(@TempDir Path tmp) throws IOException {
        var cred = tmp.resolve("creds.properties");
        Files.writeString(cred, """
            sqlserver_hostname=myserver
            sqlserver_dbname=MCMBLIVE
            sqlserver_portnumber=1433
            sqlserver_username=sa
            sqlserver_password=secret
            """);
        var result = CredentialLoader.load(cred.toString());
        assertEquals("myserver", result.hostname());
        assertEquals("MCMBLIVE", result.dbName());
        assertEquals(1433, result.port());
        assertEquals("sa", result.username());
        assertEquals("secret", result.password());
    }

    @Test
    void throwsWhenFileMissing() {
        assertThrows(IllegalArgumentException.class,
            () -> CredentialLoader.load("/nonexistent/path.properties"));
    }

    @Test
    void throwsWhenKeyMissing(@TempDir Path tmp) throws IOException {
        var cred = tmp.resolve("creds.properties");
        Files.writeString(cred, "sqlserver_hostname=myserver\n");
        assertThrows(IllegalArgumentException.class,
            () -> CredentialLoader.load(cred.toString()));
    }
}
