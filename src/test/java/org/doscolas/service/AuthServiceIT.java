package org.doscolas.service;

import org.doscolas.db.ConnectionPool;
import org.doscolas.dto.request.AuthRequest;
import org.doscolas.dto.request.RegisterRequest;
import org.doscolas.dto.response.AuthResponse;
import org.doscolas.exception.BusinessRuleException;
import org.doscolas.exception.DuplicateResourceException;
import org.doscolas.repository.EmailVerificationTokenRepository;
import org.doscolas.repository.PasswordResetTokenRepository;
import org.doscolas.repository.UserRepository;
import org.doscolas.security.JwtService;
import org.doscolas.security.PasswordEncoder;
import org.doscolas.testsupport.FakeEmailSender;
import org.doscolas.testsupport.TestDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises AuthService's registration/verification/reset flows against a real local Postgres
 * (see {@link TestDb}), with a {@link FakeEmailSender} standing in for SMTP so tests can read the
 * verification/reset token straight out of the "sent" email instead of needing a mail server.
 */
class AuthServiceIT {

    private static ConnectionPool pool;
    private static AuthService authService;
    private static FakeEmailSender emailSender;

    @BeforeAll
    static void setUp() {
        pool = TestDb.pool();
        UserRepository userRepository = new UserRepository(pool);
        EmailVerificationTokenRepository evtRepo = new EmailVerificationTokenRepository(pool);
        PasswordResetTokenRepository prtRepo = new PasswordResetTokenRepository(pool);
        emailSender = new FakeEmailSender();
        authService = new AuthService(userRepository, evtRepo, prtRepo, new PasswordEncoder(),
                new JwtService("dGVzdC1zZWNyZXQta2V5LWZvci1qdW5pdC10ZXN0cy1vbmx5", 3_600_000), emailSender,
                "http://localhost:3000");
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    private RegisterRequest registerRequest(String username, String email) {
        return RegisterRequest.fromJson(Map.of(
                "username", username, "email", email, "password", "Password123!", "roles", List.of("OWNER")));
    }

    @Test
    void registeredUserCannotLoginUntilVerified() {
        String email = uniqueEmail("unverified");
        AuthResponse registered = authService.register(registerRequest("unverified" + suffix(email), email));
        assertNotNull(registered.token);

        AuthRequest login = AuthRequest.fromJson(Map.of("email", email, "password", "Password123!"));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> authService.authenticate(login));
        assertTrue(ex.getMessage().toLowerCase().contains("verificar"));
    }

    @Test
    void verifyingWithTheEmailedTokenUnlocksLogin() {
        String email = uniqueEmail("verifyme");
        authService.register(registerRequest("verifyme" + suffix(email), email));
        String token = emailSender.lastTokenSentTo(email);

        authService.verifyEmail(token);

        AuthRequest login = AuthRequest.fromJson(Map.of("email", email, "password", "Password123!"));
        AuthResponse response = authService.authenticate(login);
        assertEquals(email, response.email);
    }

    @Test
    void verificationTokenCannotBeReused() {
        String email = uniqueEmail("reuse");
        authService.register(registerRequest("reuse" + suffix(email), email));
        String token = emailSender.lastTokenSentTo(email);

        authService.verifyEmail(token);

        assertThrows(BusinessRuleException.class, () -> authService.verifyEmail(token));
    }

    @Test
    void duplicateUsernameOrEmailIsRejected() {
        String email = uniqueEmail("dup");
        authService.register(registerRequest("dupuser" + suffix(email), email));

        assertThrows(DuplicateResourceException.class,
                () -> authService.register(registerRequest("dupuser" + suffix(email), uniqueEmail("dup2"))));
        assertThrows(DuplicateResourceException.class,
                () -> authService.register(registerRequest("someoneElse" + suffix(email), email)));
    }

    @Test
    void forgotPasswordThenResetChangesThePassword() {
        String email = uniqueEmail("reset");
        authService.register(registerRequest("reset" + suffix(email), email));
        authService.verifyEmail(emailSender.lastTokenSentTo(email));

        authService.forgotPassword(email);
        String resetToken = emailSender.lastTokenSentTo(email);
        authService.resetPassword(resetToken, "NewPassword456!");

        assertThrows(BusinessRuleException.class, () -> authService.authenticate(
                AuthRequest.fromJson(Map.of("email", email, "password", "Password123!"))));
        AuthResponse response = authService.authenticate(
                AuthRequest.fromJson(Map.of("email", email, "password", "NewPassword456!")));
        assertEquals(email, response.email);
    }

    @Test
    void resetTokenCannotBeReused() {
        String email = uniqueEmail("resetreuse");
        authService.register(registerRequest("resetreuse" + suffix(email), email));
        authService.verifyEmail(emailSender.lastTokenSentTo(email));

        authService.forgotPassword(email);
        String resetToken = emailSender.lastTokenSentTo(email);
        authService.resetPassword(resetToken, "NewPassword456!");

        assertThrows(BusinessRuleException.class, () -> authService.resetPassword(resetToken, "AnotherOne789!"));
    }

    @Test
    void forgotPasswordForUnknownEmailDoesNotThrow() {
        assertDoesNotThrow(() -> authService.forgotPassword("definitely-not-registered-" + System.nanoTime() + "@test.com"));
    }

    @Test
    void resendVerificationForUnknownEmailDoesNotThrow() {
        assertDoesNotThrow(() -> authService.resendVerification("definitely-not-registered-" + System.nanoTime() + "@test.com"));
    }

    private String uniqueEmail(String label) {
        return "authit_" + label + "_" + System.nanoTime() + "@test.com";
    }

    private String suffix(String email) {
        return String.valueOf(Math.abs(email.hashCode()));
    }
}
