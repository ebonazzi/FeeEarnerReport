package net.javalover.feeearner.config;

import net.javalover.feeearner.model.AppParam;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    void fromFiltersInactiveParams() {
        var active = new AppParam(1, "log_dir", "/tmp/logs", "", true);
        var inactive = new AppParam(2, "output_dir", "/tmp/out", "", false);
        var config = AppConfig.from(List.of(active, inactive));
        assertEquals("/tmp/logs", config.logDir());
        assertThrows(IllegalStateException.class, config::outputDir);
    }

    @Test
    void requireThrowsForMissingKey() {
        var config = AppConfig.from(List.of());
        assertThrows(IllegalStateException.class, () -> config.require("log_dir"));
    }

    @Test
    void getIntReturnsDefaultWhenAbsent() {
        var config = AppConfig.from(List.of());
        assertEquals(2, config.threadPoolSize());
    }

    @Test
    void getIntThrowsOnNonNumericValue() {
        var param = new AppParam(1, "thread_pool_size", "notanumber", "2", true);
        var config = AppConfig.from(List.of(param));
        assertThrows(IllegalStateException.class, config::threadPoolSize);
    }

    @Test
    void paramsMapIsImmutable() {
        var param = new AppParam(1, "log_dir", "/tmp", "", true);
        var config = AppConfig.from(List.of(param));
        assertThrows(UnsupportedOperationException.class,
            () -> config.params().put("log_dir", "hacked"));
    }
}
