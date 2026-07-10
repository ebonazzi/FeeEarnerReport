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
     * <p>The file output ({@code logging.appender.file.output}) is given as a path
     * relative to the JVM working directory (e.g. {@code ./relative/fee_earner_report.log}),
     * NOT an absolute {@code file:} URI. This is a deliberate workaround for a bug in
     * {@code io.jstach.rainbowgum.LogOutputRegistry.normalize(URI)} (present in both 0.8.0
     * and 0.8.2): for a {@code file:} URI it derives a path via {@code uri.getPath()} and
     * passes that decoded string straight to {@code Paths.get(String)}, which is not
     * URI-aware and throws {@code InvalidPathException} on Windows because the decoded
     * path has a leading {@code /} before the drive letter (e.g. {@code /C:/logs/...}).
     * A relative path with no scheme routes into a different branch of that same method
     * ({@code Paths.get(path).toUri()} directly, no leading slash involved), which resolves
     * correctly on both Windows and Linux. See {@code windows_logging_crash_rootcause} notes.
     *
     * <p>Note: the {@code rainbowgum-pattern} module is not on the classpath, so a custom
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
        var logAbsolutePath = Path.of(logDir, LOG_FILENAME).toAbsolutePath().normalize();
        var workingDir = Path.of("").toAbsolutePath();
        Path relativePath;
        try {
            relativePath = workingDir.relativize(logAbsolutePath);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Cannot express log file path '" + logAbsolutePath + "' relative to the working "
                            + "directory '" + workingDir + "' — they are on different filesystem roots "
                            + "(e.g. different Windows drive letters). Set log_dir to a path on the same "
                            + "drive as the application's working directory.", e);
        }
        var logOutputValue = "./" + relativePath.toString().replace('\\', '/');

        System.setProperty("logging.level", normalise(level));
        System.setProperty("logging.appenders", "file,console");
        System.setProperty("logging.appender.file.output", logOutputValue);
        System.setProperty("logging.appender.console.output", "stdout");
        System.setProperty("logging.global.change", "true");
        System.setProperty("logging.change", "LEVEL");

        // Floor noisy third-party loggers regardless of the app's root level. In
        // particular the mssql-jdbc driver logs EVERY SQLException it builds at FINE
        // (-> DEBUG), e.g. one line per duplicate-key row — pure noise, since the app
        // already logs caught failures at ERROR. These floors don't move with debug_level.
        applyQuietDefaults();

        Logger startupLogger = LoggerFactory.getLogger(LoggingInitialiser.class);
        startupLogger.info("Logging initialised — level={} file={}", level, logAbsolutePath);
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
