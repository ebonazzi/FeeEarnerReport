package net.javalover.feeearner.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ParameterServiceTest {

    @Test
    void validateParamAcceptsExistingWritableDir(@TempDir Path tmp) {
        var result = ParameterService.validate("log_dir", tmp.toString());
        assertTrue(result.valid(), result.message());
    }

    @Test
    void validateParamRejectsNonExistentDir() {
        var result = ParameterService.validate("log_dir", "/no/such/path");
        assertFalse(result.valid());
    }

    @Test
    void validateParamAcceptsNonDirParam() {
        var result = ParameterService.validate("smtp_server", "mail.example.com");
        assertTrue(result.valid());
    }

    @Test
    void validateParamRejectsInvalidOutputDir() {
        var result = ParameterService.validate("output_dir", "/no/such/path");
        assertFalse(result.valid());
    }
}
