package org.doscolas.service;

import org.doscolas.db.ConnectionPool;
import org.doscolas.dto.request.SmtpSettingsRequest;
import org.doscolas.email.ConfigurableEmailSender;
import org.doscolas.exception.BusinessRuleException;
import org.doscolas.model.SmtpSettings;
import org.doscolas.repository.SmtpSettingsRepository;
import org.doscolas.testsupport.FakeEmailSender;
import org.doscolas.testsupport.TestDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the real smtp_settings table (see {@link TestDb}) but a {@link FakeEmailSender} fallback,
 *  so tests can assert on "was the fallback used" without needing real SMTP anywhere. */
class SmtpSettingsServiceIT {

    private static ConnectionPool pool;
    private static SmtpSettingsRepository settingsRepository;
    private static FakeEmailSender fallback;
    private static ConfigurableEmailSender emailSender;
    private static SmtpSettingsService service;

    @BeforeAll
    static void setUp() {
        pool = TestDb.pool();
        settingsRepository = new SmtpSettingsRepository(pool);
        fallback = new FakeEmailSender();
        emailSender = new ConfigurableEmailSender(settingsRepository, fallback);
        service = new SmtpSettingsService(settingsRepository, emailSender);
    }

    @AfterEach
    void resetSettings() {
        // Leave the singleton row disabled/empty between tests so they don't interfere.
        settingsRepository.save(new SmtpSettings());
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    private SmtpSettingsRequest request(String host, Integer port, String username, String password,
                                         String fromAddress, boolean enabled) {
        return SmtpSettingsRequest.fromJson(new java.util.LinkedHashMap<>(Map.of(
                "provider", "maileroo", "host", host, "port", port, "username", username,
                "password", password, "startTls", true, "fromAddress", fromAddress,
                "fromName", "Dos Colas", "enabled", enabled
        )));
    }

    @Test
    void savingWithEnabledTrueRequiresHostPortAndFromAddress() {
        var missingHost = new java.util.LinkedHashMap<String, Object>();
        missingHost.put("enabled", true);
        missingHost.put("fromAddress", "no-reply@doscolas.cl");
        assertThrows(Exception.class, () -> SmtpSettingsRequest.fromJson(missingHost));
    }

    @Test
    void savedPasswordIsNeverReturnedButPersists() {
        service.update(request("smtp.maileroo.com", 587, "user1", "secret-pass", "no-reply@doscolas.cl", false));

        SmtpSettings persisted = settingsRepository.find().orElseThrow();
        assertEquals("secret-pass", persisted.getPassword());
    }

    @Test
    void blankPasswordOnUpdateKeepsThePreviouslySavedOne() {
        service.update(request("smtp.maileroo.com", 587, "user1", "secret-pass", "no-reply@doscolas.cl", false));

        // Re-save with enabled flipped but password left blank, as the admin UI does on any edit
        // that isn't specifically "change the password".
        service.update(request("smtp.maileroo.com", 587, "user1", "", "no-reply@doscolas.cl", false));

        assertEquals("secret-pass", settingsRepository.find().orElseThrow().getPassword());
    }

    @Test
    void emailsFallBackWhenSettingsAreDisabled() {
        service.update(request("smtp.maileroo.com", 587, "user1", "secret-pass", "no-reply@doscolas.cl", false));

        emailSender.send("someone@test.com", "Subject", "<p>Body</p>");

        assertEquals("someone@test.com", fallback.lastSentTo("someone@test.com").to());
    }

    @Test
    void emailsFallBackWhenNothingIsConfiguredAtAll() {
        emailSender.send("nobody-configured@test.com", "Subject", "<p>Body</p>");

        assertEquals("nobody-configured@test.com", fallback.lastSentTo("nobody-configured@test.com").to());
    }

    @Test
    void enabledSettingsAreUsedInsteadOfFallingBack() {
        // A host that can't actually be reached — the point is only to prove ConfigurableEmailSender
        // *attempted* the configured SMTP path instead of silently using the fallback.
        service.update(request("smtp.invalid.example", 587, "user1", "secret-pass", "no-reply@doscolas.cl", true));

        assertThrows(RuntimeException.class,
                () -> emailSender.send("someone@test.com", "Subject", "<p>Body</p>"));
        assertThrows(AssertionError.class, () -> fallback.lastSentTo("someone@test.com"));
    }

    @Test
    void sendTestFailsClearlyWhenNothingIsSaved() {
        assertThrows(BusinessRuleException.class, () -> service.sendTest("someone@test.com"));
    }

    @Test
    void sendTestSurfacesDeliveryFailureAsBusinessRuleException() {
        service.update(request("smtp.invalid.example", 587, "user1", "secret-pass", "no-reply@doscolas.cl", false));

        assertThrows(BusinessRuleException.class, () -> service.sendTest("someone@test.com"));
    }
}
