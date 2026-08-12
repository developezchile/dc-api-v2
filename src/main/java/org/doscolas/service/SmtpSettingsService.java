package org.doscolas.service;

import org.doscolas.dto.request.SmtpSettingsRequest;
import org.doscolas.email.ConfigurableEmailSender;
import org.doscolas.email.EmailTemplates;
import org.doscolas.exception.BusinessRuleException;
import org.doscolas.model.SmtpSettings;
import org.doscolas.repository.SmtpSettingsRepository;

public final class SmtpSettingsService {

    private final SmtpSettingsRepository settingsRepository;
    private final ConfigurableEmailSender emailSender;

    public SmtpSettingsService(SmtpSettingsRepository settingsRepository, ConfigurableEmailSender emailSender) {
        this.settingsRepository = settingsRepository;
        this.emailSender = emailSender;
    }

    public SmtpSettings get() {
        return settingsRepository.find().orElse(null);
    }

    public SmtpSettings update(SmtpSettingsRequest req) {
        SmtpSettings existing = settingsRepository.find().orElse(null);

        SmtpSettings settings = new SmtpSettings();
        settings.setProvider(req.provider);
        settings.setHost(req.host);
        settings.setPort(req.port);
        settings.setUsername(req.username);
        // Blank password on update means "keep the one already saved" — the response never sends
        // it back, so the form can't round-trip it, and re-typing it on every unrelated edit
        // (e.g. just flipping "enabled") would be a bad time.
        settings.setPassword((req.password == null || req.password.isBlank())
                ? (existing != null ? existing.getPassword() : null)
                : req.password);
        settings.setStartTls(req.startTls);
        settings.setFromAddress(req.fromAddress);
        settings.setFromName(req.fromName);
        settings.setEnabled(req.enabled);

        return settingsRepository.save(settings);
    }

    public void sendTest(String to) {
        SmtpSettings settings = settingsRepository.find().orElse(null);
        if (settings == null || settings.getHost() == null || settings.getHost().isBlank()) {
            throw new BusinessRuleException("No hay configuración SMTP guardada todavía.");
        }
        try {
            emailSender.sendTest(settings, to, "Correo de prueba — Dos Colas",
                    EmailTemplates.testEmail(settings.getProvider()));
        } catch (Exception e) {
            throw new BusinessRuleException("No se pudo enviar el correo de prueba: " + e.getMessage());
        }
    }
}
