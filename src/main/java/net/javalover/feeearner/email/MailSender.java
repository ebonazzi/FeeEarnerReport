package net.javalover.feeearner.email;

import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FeeEarnerRun;
import java.util.Properties;

public class MailSender {

    public void send(FeeEarnerRun run, AppConfig config) throws MessagingException {
        if (run == null) throw new IllegalArgumentException("run must not be null");
        var props = new Properties();
        props.put("mail.smtp.host", config.smtpServer());
        props.put("mail.smtp.port", String.valueOf(config.smtpPort()));
        props.put("mail.smtp.auth", "false");
        props.put("mail.smtp.starttls.enable", "false");
        var session = Session.getInstance(props);
        Transport.send(buildMessage(session, run, config));
    }

    static MimeMessage buildMessage(Session session, FeeEarnerRun run, AppConfig config)
            throws MessagingException {
        var msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(config.emailSender()));

        // Spreadsheets go ONLY to the configured email_recipients list; the fee earner
        // is never emailed. A blank list is an error (we do not send a recipient-less message).
        int added = 0;
        var recipients = config.emailRecipients();
        if (!recipients.isBlank()) {
            for (var addr : recipients.split("\\|")) {
                var trimmed = addr.trim();
                if (!trimmed.isEmpty()) {
                    msg.addRecipient(Message.RecipientType.TO, new InternetAddress(trimmed));
                    added++;
                }
            }
        }
        if (added == 0) {
            throw new IllegalStateException(
                "email_recipients is empty — no recipients to send the spreadsheet to (usrID="
                + run.usrID() + ")");
        }

        msg.setSubject(config.emailSubject());

        var body = new MimeBodyPart();
        body.setText(config.emailBody());

        var attachment = new MimeBodyPart();
        if (run.excelSpreadsheet() == null) {
            throw new IllegalStateException(
                "No excel spreadsheet stored for usrID=" + run.usrID());
        }
        attachment.setDataHandler(new DataHandler(
            new ByteArrayDataSource(run.excelSpreadsheet(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
        attachment.setFileName(run.excelFilename());

        var multipart = new MimeMultipart();
        multipart.addBodyPart(body);
        multipart.addBodyPart(attachment);
        msg.setContent(multipart);

        return msg;
    }
}
