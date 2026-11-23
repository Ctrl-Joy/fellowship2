package app.strata.auth;

import app.strata.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Outbound mail. In {@code log} mode (local development, no SMTP configured) messages are written to the
 * application log under the MAIL-DEV marker so verification/reset links can be followed by hand.
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private final AppProperties props;
    private final ObjectProvider<JavaMailSender> sender;

    public MailService(AppProperties props, ObjectProvider<JavaMailSender> sender) {
        this.props = props;
        this.sender = sender;
    }

    public void send(String to, String subject, String body) {
        if (!"smtp".equals(props.mail().mode()) || sender.getIfAvailable() == null) {
            log.info("MAIL-DEV to=<{}> subject=\"{}\"\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(props.mail().from());
            m.setTo(to);
            m.setSubject(subject);
            m.setText(body);
            sender.getObject().send(m);
        } catch (RuntimeException e) {
            log.warn("Failed to send mail '{}': {}", subject, e.getMessage());
        }
    }

    public void sendVerification(String to, String username, String token) {
        String link = props.app().publicUrl() + "/verify-email?token=" + token;
        send(to, "Verify your " + props.app().name() + " email",
                "Hi " + username + ",\n\nConfirm your email address:\n" + link + "\n\nThis link expires in 24 hours.");
    }

    public void sendPasswordReset(String to, String username, String token) {
        String link = props.app().publicUrl() + "/reset-password?token=" + token;
        send(to, "Reset your " + props.app().name() + " password",
                "Hi " + username + ",\n\nReset your password:\n" + link
                        + "\n\nThis link expires in 1 hour. If you didn't request it, ignore this email.");
    }
}
