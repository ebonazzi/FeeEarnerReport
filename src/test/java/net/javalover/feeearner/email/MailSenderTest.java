package net.javalover.feeearner.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMultipart;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.AppParam;
import net.javalover.feeearner.model.FeeEarnerRun;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class MailSenderTest {

    private AppConfig config() {
        return AppConfig.from(List.of(
            new AppParam(1, "smtp_server",      "localhost",            true),
            new AppParam(2, "smtp_port",        "25",                   true),
            new AppParam(3, "email_sender",     "reports@law.com",      true),
            new AppParam(4, "email_recipients", "a@law.com|b@law.com",  true),
            new AppParam(5, "email_subject",    "Fee Earner Report",    true),
            new AppParam(6, "email_body",       "Please find attached.", true),
            new AppParam(7, "log_dir",          "/tmp",                 true)
        ));
    }

    private FeeEarnerRun run() {
        return new FeeEarnerRun(1, LocalDate.of(2026, 6, 5), 100,
            "Alice Smith", "alice@law.com", "fe_100_20260605_1.xlsx",
            new byte[]{1, 2, 3}, null);
    }

    @Test
    void toRecipientsAreTheConfiguredListNotTheFeeEarner() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        var toAddrs = msg.getRecipients(jakarta.mail.Message.RecipientType.TO);
        assertEquals(2, toAddrs.length);
        var asText = java.util.Arrays.stream(toAddrs).map(Object::toString).toList();
        assertTrue(asText.contains("a@law.com"));
        assertTrue(asText.contains("b@law.com"));
        assertFalse(asText.contains("alice@law.com"), "fee earner must NOT be a recipient");
    }

    @Test
    void feeEarnerIsNeverACcRecipient() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        var ccAddrs = msg.getRecipients(jakarta.mail.Message.RecipientType.CC);
        assertNull(ccAddrs, "no CC recipients; fee earner is not emailed");
    }

    @Test
    void blankRecipientListThrows() {
        var cfg = AppConfig.from(List.of(
            new AppParam(1, "smtp_server",      "localhost",        true),
            new AppParam(2, "smtp_port",        "25",               true),
            new AppParam(3, "email_sender",     "reports@law.com",  true),
            new AppParam(4, "email_recipients", "",                 true),
            new AppParam(5, "email_subject",    "Fee Earner Report", true),
            new AppParam(6, "email_body",       "x",                true)
        ));
        var session = Session.getInstance(new Properties());
        assertThrows(IllegalStateException.class,
            () -> MailSender.buildMessage(session, run(), cfg));
    }

    @Test
    void messageHasXlsxAttachment() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        var multipart = (MimeMultipart) msg.getContent();
        assertEquals(2, multipart.getCount());
        assertEquals("fe_100_20260605_1.xlsx", multipart.getBodyPart(1).getFileName());
    }

    @Test
    void messageHasCorrectSubject() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        assertEquals("Fee Earner Report", msg.getSubject());
    }
}
