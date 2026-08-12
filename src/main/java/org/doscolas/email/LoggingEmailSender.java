package org.doscolas.email;

import org.doscolas.log.LogManager;
import org.doscolas.log.Logger;

/** Default when {@code SMTP_HOST} isn't configured — logs instead of sending, so local dev and a
 *  fresh checkout work without setting up SMTP first. Never use this in production. */
public final class LoggingEmailSender implements EmailSender {

    private static final Logger log = LogManager.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String htmlBody) {
        log.warn("SMTP_HOST not configured — logging email instead of sending. to={} subject={}", to, subject);
        log.info("Email body for {}:\n{}", to, htmlBody);
    }
}
