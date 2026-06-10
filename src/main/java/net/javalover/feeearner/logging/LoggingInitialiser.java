package net.javalover.feeearner.logging;

import io.jstach.rainbowgum.RainbowGum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public final class LoggingInitialiser {

    private static final String LOG_FILENAME = "fee_earner_report.log";

    private LoggingInitialiser() {}

    /**
     * Programmatically configure Rainbow Gum before any SLF4J usage.
     *
     * <p>Rainbow Gum 0.8.0 reads its config from system properties prefixed with
     * {@code logging.} during its lazy initialisation, which happens on the FIRST
     * SLF4J call. These properties therefore MUST be set before anything logs —
     * notably before the HikariCP pool is constructed, since HikariCP logs via SLF4J.
     * Param loading during bootstrap goes through {@code ParamRepository.loadAllBootstrap}
     * (a plain DriverManager connection) precisely so no SLF4J logging happens first.
     *
     * <p>The file output is given as a {@code file:} URI ({@code logging.appender.file.output}).
     * Note: the {@code rainbowgum-pattern} module is not on the classpath, so a custom
     * Logback-style pattern cannot be applied — Rainbow Gum's default line format is used.
     *
     * <p>Runtime level changes are enabled here so {@link #applyLevel(String)} works:
     * {@code logging.global.change=true} is the master switch that installs a real
     * change publisher, and {@code logging.change=LEVEL} permits LEVEL changes for all
     * loggers. Both must be set at init — they are read once, when Rainbow Gum builds.
     */
    public static void init(String logDir, String level) {
        if (logDir == null || logDir.isBlank())
            throw new IllegalArgumentException("logDir must not be null or blank");
        if (level == null || level.isBlank())
            throw new IllegalArgumentException("level must not be null or blank");
        var logUri = Path.of(logDir, LOG_FILENAME).toAbsolutePath().toUri().toString();

        System.setProperty("logging.level", normalise(level));
        System.setProperty("logging.appenders", "file,console");
        System.setProperty("logging.appender.file.output", logUri);
        System.setProperty("logging.appender.console.output", "stdout");
        System.setProperty("logging.global.change", "true");
        System.setProperty("logging.change", "LEVEL");

        // Floor noisy third-party loggers regardless of the app's root level. In
        // particular the mssql-jdbc driver logs EVERY SQLException it builds at FINE
        // (-> DEBUG), e.g. one line per duplicate-key row — pure noise, since the app
        // already logs caught failures at ERROR. These floors don't move with debug_level.
        applyQuietDefaults();

        Logger startupLogger = LoggerFactory.getLogger(LoggingInitialiser.class);
        startupLogger.info("Logging initialised — level={} file={}", level, logUri);
    }

    /**
     * Changes the root log level at runtime. Updates the {@code logging.level} property
     * and asks Rainbow Gum's change publisher to re-read it — a no-op system-property
     * write alone would NOT take effect, because Rainbow Gum caches its config after
     * init. Requires the change settings established in {@link #init}.
     */
    public static void applyLevel(String level) {
        System.setProperty("logging.level", normalise(level));
        var gum = RainbowGum.getOrNull();
        if (gum != null)
            gum.config().changePublisher().publish();
    }

    /** Per-logger thresholds for chatty libraries, so app logs stay readable. */
    private static void applyQuietDefaults() {
        System.setProperty("logging.level.com.microsoft.sqlserver", "WARN");
        System.setProperty("logging.level.com.zaxxer.hikari", "INFO");
        System.setProperty("logging.level.jakarta.mail", "WARN");
        System.setProperty("logging.level.org.eclipse.angus", "WARN");
        System.setProperty("logging.level.org.apache.poi", "WARN");
    }

    private static String normalise(String level) {
        return switch (level.toUpperCase()) {
            case "TRACE", "DEBUG", "INFO", "WARN", "ERROR" -> level.toUpperCase();
            default -> "INFO";
        };
    }
}
