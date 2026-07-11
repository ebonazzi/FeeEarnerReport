package net.javalover.feeearner.service;

import jakarta.mail.MessagingException;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.email.MailSender;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.RunRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EmailServiceTest {

    private static final FeeEarnerRun RUN = new FeeEarnerRun(
        1, LocalDate.of(2026, 6, 5), 100, "Alice Smith",
        "alice@law.com", "fe_100_20260605_1.xlsx", new byte[]{1, 2, 3}, null);

    private AppConfig config() {
        return AppConfig.from(List.of(
            new AppParam(1, "smtp_server",  "localhost",       true),
            new AppParam(2, "smtp_port",    "25",              true),
            new AppParam(3, "email_sender", "reports@law.com", true),
            new AppParam(4, "email_subject","Report",          true),
            new AppParam(5, "email_body",   "See attached",    true),
            new AppParam(6, "log_dir",      "/tmp",            true)
        ));
    }

    @Test
    void sendForFeeEarnerDelegatesToMailSender() throws Exception {
        var sent = new AtomicInteger(0);
        var mailSender = new MailSender() {
            @Override public void send(FeeEarnerRun run, AppConfig config) { sent.incrementAndGet(); }
        };
        var runRepo = new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int usrID) {
                return Optional.of(RUN);
            }
        };
        var service = new EmailService(mailSender, runRepo);
        service.sendForFeeEarner(100, 1, config());
        assertEquals(1, sent.get());
    }

    @Test
    void sendForFeeEarnerThrowsWhenRunNotFound() {
        var mailSender = new MailSender() {
            @Override public void send(FeeEarnerRun run, AppConfig config) {}
        };
        var runRepo = new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int usrID) {
                return Optional.empty();
            }
        };
        var service = new EmailService(mailSender, runRepo);
        assertThrows(IllegalStateException.class,
            () -> service.sendForFeeEarner(100, 1, config()));
    }

    @Test
    void sendAllTracksFailures() throws Exception {
        var mailSender = new MailSender() {
            @Override public void send(FeeEarnerRun run, AppConfig config) throws MessagingException {
                throw new MessagingException("smtp down");
            }
        };
        var service = new EmailService(mailSender, null);
        var tracker = new ProgressTracker(1);
        service.sendAll(List.of(RUN), config(), tracker);
        assertEquals(1, tracker.failed().get());
        assertEquals(1, tracker.failures().size());
    }
}
