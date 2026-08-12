package org.doscolas.email;

import org.doscolas.log.LogManager;
import org.doscolas.log.Logger;
import org.doscolas.model.SmtpSettings;
import org.doscolas.repository.SmtpSettingsRepository;

/**
 * The {@link EmailSender} actually wired into the app. Prefers the admin-configured SMTP settings
 * (see the admin dashboard's Email Settings tab, backed by {@code smtp_settings} — e.g. Maileroo),
 * checked fresh on every send so a change takes effect immediately with no restart. Falls back to
 * whatever {@code SMTP_*} env vars produced at startup (real SMTP, or {@link LoggingEmailSender})
 * when no admin-configured settings are enabled.
 *
 * <p>Email volume here is low (auth notifications, not bulk sending), so building a fresh
 * {@link SmtpEmailSender} per send is deliberate — simpler than caching a session and invalidating
 * it when settings change.
 */
public final class ConfigurableEmailSender implements EmailSender {

    private static final Logger log = LogManager.getLogger(ConfigurableEmailSender.class);

    private final SmtpSettingsRepository settingsRepository;
    private final EmailSender fallback;

    public ConfigurableEmailSender(SmtpSettingsRepository settingsRepository, EmailSender fallback) {
        this.settingsRepository = settingsRepository;
        this.fallback = fallback;
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        SmtpSettings settings = settingsRepository.find().orElse(null);
        if (settings != null && settings.isUsable()) {
            senderFor(settings).send(to, subject, htmlBody);
            return;
        }
        fallback.send(to, subject, htmlBody);
    }

    private EmailSender senderFor(SmtpSettings settings) {
        return new SmtpEmailSender(settings.getHost(), settings.getPort(), settings.getUsername(),
                settings.getPassword(), settings.isStartTls(), settings.getFromAddress(),
                settings.getFromName() != null ? settings.getFromName() : "Dos Colas");
    }

    /** Used by the "send test email" admin action — sends through the given settings regardless
     *  of whether they're saved/enabled yet, so an admin can verify before flipping it on. */
    public void sendTest(SmtpSettings settings, String to, String subject, String htmlBody) {
        log.info("Sending test email via {}:{} to {}", settings.getHost(), settings.getPort(), to);
        senderFor(settings).send(to, subject, htmlBody);
    }
}
