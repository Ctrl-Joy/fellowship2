package app.strata.auth;

import app.strata.common.config.AppProperties;
import app.strata.mail.EmailTemplates;
import app.strata.mail.MailDispatcher;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * The auth emails. Rendering happens here; delivery goes through {@link MailDispatcher}, which sends after the
 * surrounding transaction commits, off the request thread, with retries (D-038). The transport ({@code log},
 * {@code smtp} or {@code brevo}) comes from {@code MAIL_MODE}.
 */
@Service
public class MailService {
    private final MailDispatcher dispatcher;
    private final EmailTemplates templates;

    public MailService(AppProperties props, MailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.templates = new EmailTemplates(EmailTemplates.BRAND, props.app().publicUrl());
    }

    public void sendVerification(String to, String username, String token, Duration expiresIn) {
        dispatcher.submit(templates.verification(to, username, token, expiresIn));
    }

    public void sendPasswordReset(String to, String username, String token, Duration expiresIn) {
        dispatcher.submit(templates.passwordReset(to, username, token, expiresIn));
    }
}
