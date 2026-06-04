package net.javalover.feeearner.email;

import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FeeEarnerRun;
import java.util.Properties;

public class MailSender {

    private final AppConfig config;

    public MailSender(AppConfig config) {
        this.config = config;
    }

    public void send(FeeEarnerRun run) throws MessagingException {
        if (run == null) throw new IllegalArgumentException("run must not be null");
        if (run.usrEmail() == null || run.usrEmail().isBlank())
            throw new IllegalArgumentException("run.usrEmail() must not be blank for usrID=" + run.usrID());
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
        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(run.usrEmail()));

        var recipients = config.emailRecipients();
        if (!recipients.isBlank()) {
            for (var addr : recipients.split("\\|")) {
                var trimmed = addr.trim();
                if (!trimmed.isEmpty()) {
                    msg.addRecipient(Message.RecipientType.CC, new InternetAddress(trimmed));
                }
            }
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
