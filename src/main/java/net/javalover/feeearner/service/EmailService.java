package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.email.MailSender;
import net.javalover.feeearner.model.FailedEntry;
import net.javalover.feeearner.model.FeeEarnerRun;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.repository.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final MailSender mailSender;
    private final RunRepository runRepo;

    public EmailService(MailSender mailSender, RunRepository runRepo) {
        this.mailSender = mailSender;
        this.runRepo = runRepo;
    }

    public void sendForFeeEarner(int usrID, int runId, AppConfig config) {
        var run = runRepo.getMostRecent(usrID)
            .orElseThrow(() -> new IllegalStateException(
                "No FeeEarnerRun found for usrID=" + usrID + " runId=" + runId));
        try {
            mailSender.send(run);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email for usrID=" + usrID, e);
        }
    }

    public void sendAll(List<FeeEarnerRun> runs, AppConfig config, ProgressTracker tracker) {
        for (var run : runs) {
            try {
                mailSender.send(run);
                tracker.completed().incrementAndGet();
            } catch (Exception e) {
                log.error("Failed to send email for usrID={}", run.usrID(), e);
                tracker.failed().incrementAndGet();
                tracker.failures().add(
                    new FailedEntry(run.usrID(), run.feeEarner(), e.getMessage()));
            }
        }
    }
}
