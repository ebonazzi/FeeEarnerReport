package net.javalover.feeearner.logging;

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

        Logger startupLogger = LoggerFactory.getLogger(LoggingInitialiser.class);
        startupLogger.info("Logging initialised — level={} file={}", level, logUri);
    }

    public static void applyLevel(String level) {
        System.setProperty("logging.level", normalise(level));
    }

    private static String normalise(String level) {
        return switch (level.toUpperCase()) {
            case "TRACE", "DEBUG", "INFO", "WARN", "ERROR" -> level.toUpperCase();
            default -> "INFO";
        };
    }
}
