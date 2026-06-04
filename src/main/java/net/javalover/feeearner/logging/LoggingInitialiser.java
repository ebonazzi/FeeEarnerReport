package net.javalover.feeearner.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public final class LoggingInitialiser {

    private static final String LOG_FILENAME = "fee_earner_report.log";

    private LoggingInitialiser() {}

    /**
     * Programmatically configure Rainbow Gum before any SLF4J usage.
     * Must be called once, after params are loaded and log_dir is known.
     * Rainbow Gum 0.8.0 reads these system properties during its lazy initialisation.
     */
    public static void init(String logDir, String level) {
        var logPath = Path.of(logDir, LOG_FILENAME).toAbsolutePath().toString();

        System.setProperty("rainbowgum.level", normalise(level));
        System.setProperty("rainbowgum.appenders", "file,console");

        System.setProperty("rainbowgum.appender.file.output", logPath);
        System.setProperty("rainbowgum.appender.file.maxFileSize", "100MB");
        System.setProperty("rainbowgum.appender.file.compress", "gz");
        System.setProperty("rainbowgum.appender.file.pattern",
            "%d{yyyy-MM-dd HH:mm:ss.SSS}|%-5level|%t|%logger{36}|%msg|%ex%n");

        System.setProperty("rainbowgum.appender.console.pattern",
            "%d{HH:mm:ss.SSS}|%-5level|%msg%n");

        Logger startupLogger = LoggerFactory.getLogger(LoggingInitialiser.class);
        startupLogger.info("Logging initialised — level={} file={}", level, logPath);
    }

    public static void applyLevel(String level) {
        System.setProperty("rainbowgum.level", normalise(level));
    }

    private static String normalise(String level) {
        return switch (level.toUpperCase()) {
            case "TRACE", "DEBUG", "INFO", "WARN", "ERROR" -> level.toUpperCase();
            default -> "INFO";
        };
    }
}
