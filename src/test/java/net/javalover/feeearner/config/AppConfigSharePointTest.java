package net.javalover.feeearner.config;

import net.javalover.feeearner.model.AppParam;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigSharePointTest {

    private AppConfig config() {
        return AppConfig.from(List.of(
            new AppParam(1, "shrpnt_client_id",  "client-123",                         true),
            new AppParam(2, "shrpnt_tenant_id",  "tenant-456",                         true),
            new AppParam(3, "shrpnt_secret_id",  "secret-789",                         true),
            new AppParam(4, "shrpnt_host",       "example.sharepoint.com",             true),
            new AppParam(5, "shrpnt_site_name",  "root",                               true),
            new AppParam(6, "shrpnt_target_dir", "Shared Documents/LSP/VIC_reports",   true)
        ));
    }

    @Test
    void exposesAllSixSharePointParams() {
        var c = config();
        assertEquals("client-123", c.sharePointClientId());
        assertEquals("tenant-456", c.sharePointTenantId());
        assertEquals("secret-789", c.sharePointSecretId());
        assertEquals("example.sharepoint.com", c.sharePointHost());
        assertEquals("root", c.sharePointSiteName());
        assertEquals("Shared Documents/LSP/VIC_reports", c.sharePointTargetDir());
    }

    @Test
    void missingSharePointParamThrows() {
        var c = AppConfig.from(List.of());
        assertThrows(IllegalStateException.class, c::sharePointClientId);
    }
}
