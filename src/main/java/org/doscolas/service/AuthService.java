package org.doscolas.service;

import org.doscolas.dto.request.AuthRequest;
import org.doscolas.dto.request.RegisterRequest;
import org.doscolas.dto.response.AuthResponse;
import org.doscolas.email.EmailSender;
import org.doscolas.email.EmailTemplates;
import org.doscolas.exception.BusinessRuleException;
import org.doscolas.exception.DuplicateResourceException;
import org.doscolas.exception.ResourceNotFoundException;
import org.doscolas.log.LogManager;
import org.doscolas.log.Logger;
import org.doscolas.model.EmailVerificationToken;
import org.doscolas.model.PasswordResetToken;
import org.doscolas.model.Role;
import org.doscolas.model.User;
import org.doscolas.repository.EmailVerificationTokenRepository;
import org.doscolas.repository.PasswordResetTokenRepository;
import org.doscolas.repository.UserRepository;
import org.doscolas.security.JwtService;
import org.doscolas.security.PasswordEncoder;
import org.doscolas.security.TokenGenerator;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/** Registration + login. No {@code AuthenticationManager} — password check happens right here via {@link PasswordEncoder}. */
public final class AuthService {

    private static final Logger log = LogManager.getLogger(AuthService.class);

    private static final long VERIFICATION_TOKEN_TTL_MINUTES = 24 * 60;
    private static final long RESET_TOKEN_TTL_MINUTES = 60;

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailSender emailSender;
    private final String frontendUrl;

    public AuthService(UserRepository userRepository, EmailVerificationTokenRepository emailVerificationTokenRepository,
                        PasswordResetTokenRepository passwordResetTokenRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, EmailSender emailSender, String frontendUrl) {
        this.userRepository = userRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailSender = emailSender;
        this.frontendUrl = frontendUrl;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username)) {
            throw new DuplicateResourceException("Username '" + req.username + "' already taken");
        }
        if (userRepository.existsByEmail(req.email)) {
            throw new DuplicateResourceException("Email '" + req.email + "' already registered");
        }

        User user = new User();
        user.setUsername(req.username);
        user.setEmail(req.email);
        user.setPassword(passwordEncoder.encode(req.password));
        user.setFirstName(req.firstName);
        user.setLastName(req.lastName);
        user.setPhone(req.phone);
        user.setAddress(req.address);
        user.setRoles(req.roles);
        user.setEnabled(true);
        user.setEmailVerified(false);

        User saved = userRepository.insert(user);
        issueVerificationEmail(saved);

        // The session issued here stays valid for its normal lifetime even though the account
        // isn't verified yet — see authenticate() for where verification is actually enforced.
        String token = jwtService.generateToken(saved.getId(), saved.getEmail(), roleNames(saved.getRoles()));
        return new AuthResponse(saved.getId(), token, saved.getUsername(), saved.getEmail(), roleNames(saved.getRoles()));
    }

    public AuthResponse authenticate(AuthRequest req) {
        User user = userRepository.findByEmail(req.email)
                .orElseThrow(() -> new BusinessRuleException("Credenciales inválidas"));
        if (!passwordEncoder.matches(req.password, user.getPassword())) {
            throw new BusinessRuleException("Credenciales inválidas");
        }
        if (!user.isEnabled()) {
            throw new BusinessRuleException("La cuenta está deshabilitada");
        }
        if (!user.isEmailVerified()) {
            throw new BusinessRuleException("Debes verificar tu correo electrónico antes de iniciar sesión. Revisa tu bandeja de entrada.");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), roleNames(user.getRoles()));
        return new AuthResponse(user.getId(), token, user.getUsername(), user.getEmail(), roleNames(user.getRoles()));
    }

    public void verifyEmail(String rawToken) {
        EmailVerificationToken evt = emailVerificationTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new BusinessRuleException("Token de verificación inválido"));
        if (evt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("El token de verificación ha expirado. Solicita uno nuevo.");
        }
        User user = userRepository.findById(evt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        user.setEmailVerified(true);
        userRepository.update(user);
        emailVerificationTokenRepository.deleteByUserId(user.getId());
    }

    /** Always no-throw regardless of whether the email exists or is already verified — the
     *  controller returns the same response either way so this can't be used to enumerate accounts. */
    public void resendVerification(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                issueVerificationEmail(user);
            }
        });
    }

    /** Same no-enumeration shape as {@link #resendVerification}. */
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(this::issueResetEmail);
    }

    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken prt = passwordResetTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new BusinessRuleException("Token de restablecimiento inválido"));
        if (prt.getUsedAt() != null) {
            throw new BusinessRuleException("Este enlace ya fue utilizado");
        }
        if (prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("El enlace ha expirado. Solicita uno nuevo.");
        }
        User user = userRepository.findById(prt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.update(user);
        passwordResetTokenRepository.markUsed(prt.getId());
        passwordResetTokenRepository.deleteByUserId(user.getId());
    }

    private void issueVerificationEmail(User user) {
        emailVerificationTokenRepository.deleteByUserId(user.getId());

        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUserId(user.getId());
        evt.setToken(TokenGenerator.generate());
        evt.setExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_TOKEN_TTL_MINUTES));
        emailVerificationTokenRepository.insert(evt);

        String url = frontendUrl + "/verify-email?token=" + evt.getToken();
        sendEmailSafely(user.getEmail(), "Verifica tu correo — Dos Colas",
                EmailTemplates.verifyEmail(user.getUsername(), url));
    }

    private void issueResetEmail(User user) {
        passwordResetTokenRepository.deleteByUserId(user.getId());

        PasswordResetToken prt = new PasswordResetToken();
        prt.setUserId(user.getId());
        prt.setToken(TokenGenerator.generate());
        prt.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
        passwordResetTokenRepository.insert(prt);

        String url = frontendUrl + "/reset-password?token=" + prt.getToken();
        sendEmailSafely(user.getEmail(), "Restablece tu contraseña — Dos Colas",
                EmailTemplates.resetPassword(user.getUsername(), url));
    }

    /** A flaky SMTP provider must not turn "register" or "forgot password" into a 500 — log and move on. */
    private void sendEmailSafely(String to, String subject, String htmlBody) {
        try {
            emailSender.send(to, subject, htmlBody);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", e, to, e.getMessage());
        }
    }

    private Set<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::name).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
