package org.doscolas;

import org.doscolas.config.AppConfig;
import org.doscolas.controller.AdminController;
import org.doscolas.controller.AuthController;
import org.doscolas.controller.FintocWebhookController;
import org.doscolas.controller.HealthController;
import org.doscolas.controller.PaymentController;
import org.doscolas.controller.PayoutController;
import org.doscolas.controller.PetController;
import org.doscolas.controller.PetSitterController;
import org.doscolas.controller.SitterBankAccountController;
import org.doscolas.controller.SmtpSettingsController;
import org.doscolas.controller.TakeCareController;
import org.doscolas.controller.UserController;
import org.doscolas.db.ConnectionPool;
import org.doscolas.db.MigrationRunner;
import org.doscolas.email.ConfigurableEmailSender;
import org.doscolas.email.EmailSender;
import org.doscolas.email.LoggingEmailSender;
import org.doscolas.email.SmtpEmailSender;
import org.doscolas.http.Router;
import org.doscolas.log.LogManager;
import org.doscolas.log.Logger;
import org.doscolas.repository.EmailVerificationTokenRepository;
import org.doscolas.repository.PasswordResetTokenRepository;
import org.doscolas.repository.PaymentRepository;
import org.doscolas.repository.PayoutRepository;
import org.doscolas.repository.PetRepository;
import org.doscolas.repository.PetSitterRepository;
import org.doscolas.repository.SitterBankAccountRepository;
import org.doscolas.repository.SmtpSettingsRepository;
import org.doscolas.repository.TakeCareRepository;
import org.doscolas.repository.UserRepository;
import org.doscolas.scheduler.AppScheduler;
import org.doscolas.security.FintocWebhookVerifier;
import org.doscolas.security.JwsSigner;
import org.doscolas.security.JwtService;
import org.doscolas.security.PasswordEncoder;
import org.doscolas.security.RateLimiter;
import org.doscolas.service.AuthService;
import org.doscolas.service.FintocClient;
import org.doscolas.service.PaymentService;
import org.doscolas.service.PayoutService;
import org.doscolas.service.PetService;
import org.doscolas.service.PetSitterService;
import org.doscolas.service.SitterBankAccountService;
import org.doscolas.service.SmtpSettingsService;
import org.doscolas.service.TakeCareService;
import org.doscolas.service.UserService;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entry point. Wires controller -> service -> repository by hand (no DI framework) and starts a
 * plain JDK {@link HttpServer}.
 */
public final class Main {

    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        Banner.print();

        AppConfig config = new AppConfig();

        ConnectionPool pool = new ConnectionPool(config.dbUrl, config.dbUsername, config.dbPassword, config.dbPoolSize);
        new MigrationRunner(pool).migrate();

        UserRepository userRepository = new UserRepository(pool);
        EmailVerificationTokenRepository emailVerificationTokenRepository = new EmailVerificationTokenRepository(pool);
        PasswordResetTokenRepository passwordResetTokenRepository = new PasswordResetTokenRepository(pool);
        PetRepository petRepository = new PetRepository(pool);
        PetSitterRepository petSitterRepository = new PetSitterRepository(pool);
        TakeCareRepository takeCareRepository = new TakeCareRepository(pool);
        PaymentRepository paymentRepository = new PaymentRepository(pool);
        PayoutRepository payoutRepository = new PayoutRepository(pool);
        SitterBankAccountRepository sitterBankAccountRepository = new SitterBankAccountRepository(pool);

        PasswordEncoder passwordEncoder = new PasswordEncoder();
        JwtService jwtService = new JwtService(config.jwtSecret, config.jwtExpirationMs);

        // Env-var SMTP (or LoggingEmailSender) is the fallback; the admin dashboard's Email
        // Settings tab (smtp_settings, e.g. Maileroo) takes priority when enabled — see
        // ConfigurableEmailSender. Checked fresh per send, so admin changes need no restart.
        EmailSender envEmailSender = config.smtpHost.isBlank()
                ? new LoggingEmailSender()
                : new SmtpEmailSender(config.smtpHost, config.smtpPort, config.smtpUsername, config.smtpPassword,
                        config.smtpStartTls, config.smtpFromAddress, config.smtpFromName);
        SmtpSettingsRepository smtpSettingsRepository = new SmtpSettingsRepository(pool);
        ConfigurableEmailSender emailSender = new ConfigurableEmailSender(smtpSettingsRepository, envEmailSender);

        RateLimiter authRateLimiter = new RateLimiter(config.rateLimitMaxRequests, config.rateLimitWindowMs);

        AuthService authService = new AuthService(userRepository, emailVerificationTokenRepository,
                passwordResetTokenRepository, passwordEncoder, jwtService, emailSender, config.frontendUrl);
        AuthController authController = new AuthController(authService, authRateLimiter);

        SmtpSettingsService smtpSettingsService = new SmtpSettingsService(smtpSettingsRepository, emailSender);
        SmtpSettingsController smtpSettingsController = new SmtpSettingsController(smtpSettingsService);

        HealthController healthController = new HealthController(pool);

        UserService userService = new UserService(userRepository, passwordEncoder);
        UserController userController = new UserController(userService);

        PetService petService = new PetService(petRepository, userRepository);
        PetController petController = new PetController(petService);

        PetSitterService petSitterService = new PetSitterService(petSitterRepository, petRepository, userRepository);
        PetSitterController petSitterController = new PetSitterController(petSitterService);

        // Fintoc: JWS signing (payouts) only comes alive once FINTOC_JWS_PRIVATE_KEY is set — see
        // AppConfig's javadoc for how to generate/register a key pair. Without it, Transfers calls
        // fail loudly (PayoutProviderException) rather than silently no-op.
        JwsSigner jwsSigner = config.fintocJwsPrivateKey.isBlank() ? null : new JwsSigner(config.fintocJwsPrivateKey);
        FintocClient fintocClient = new FintocClient(config.fintocApiUrl, config.fintocSecretKey, config.fintocAccountId, jwsSigner);

        PayoutService payoutService = new PayoutService(payoutRepository, sitterBankAccountRepository,
                takeCareRepository, petRepository, fintocClient, config.payoutMaxAttempts);
        PayoutController payoutController = new PayoutController(payoutService);

        // Payout now fires when the sitter marks the job COMPLETED (see TakeCareService), not at
        // payment time — payment approves the sitting (WAITING_APPROVAL -> ON_SITTER), it doesn't
        // finish it — so TakeCareService needs PayoutService/PaymentRepository, built above.
        TakeCareService takeCareService = new TakeCareService(takeCareRepository, petRepository, userRepository,
                paymentRepository, payoutService);
        TakeCareController takeCareController = new TakeCareController(takeCareService);

        SitterBankAccountService sitterBankAccountService =
                new SitterBankAccountService(sitterBankAccountRepository, userRepository, payoutService);
        SitterBankAccountController sitterBankAccountController = new SitterBankAccountController(sitterBankAccountService);

        PaymentService paymentService = new PaymentService(paymentRepository, userRepository, petRepository,
                fintocClient, takeCareRepository, config.fintocCallbackUrl, config.platformFeePercentage);
        PaymentController paymentController = new PaymentController(paymentService);

        FintocWebhookVerifier fintocWebhookVerifier = new FintocWebhookVerifier(config.fintocWebhookSecret);
        FintocWebhookController fintocWebhookController =
                new FintocWebhookController(fintocWebhookVerifier, paymentService, payoutService);

        AdminController adminController = new AdminController(takeCareService, paymentService,
                sitterBankAccountService, payoutService);

        List<String> allowedOrigins = List.of(config.frontendUrl,
                "http://localhost:3000", "http://localhost:4200", "http://localhost:5173", "http://localhost:8080");
        Router router = new Router(jwtService, allowedOrigins, config.contextPath);
        healthController.register(router);
        authController.register(router);
        userController.register(router);
        petController.register(router);
        petSitterController.register(router);
        takeCareController.register(router);
        paymentController.register(router);
        payoutController.register(router);
        sitterBankAccountController.register(router);
        fintocWebhookController.register(router);
        adminController.register(router);
        smtpSettingsController.register(router);

        AppScheduler scheduler = new AppScheduler(takeCareService, payoutService,
                config.payoutProcessIntervalMs, config.payoutPollIntervalMs);
        scheduler.start();

        ScheduledExecutorService rateLimiterCleanup = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "rate-limiter-cleanup"));
        rateLimiterCleanup.scheduleAtFixedRate(authRateLimiter::evictExpired, 5, 5, TimeUnit.MINUTES);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            scheduler.close();
            rateLimiterCleanup.shutdownNow();
            pool.close();
        }));

        log.info("dc-api-v2 listening on http://localhost:{}{}", config.port, config.contextPath);
    }
}
