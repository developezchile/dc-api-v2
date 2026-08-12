package org.doscolas.controller;

import com.sun.net.httpserver.HttpServer;
import org.doscolas.config.AppConfig;
import org.doscolas.db.ConnectionPool;
import org.doscolas.http.Router;
import org.doscolas.json.Json;
import org.doscolas.model.Payment;
import org.doscolas.model.PaymentStatus;
import org.doscolas.model.Payout;
import org.doscolas.model.PayoutStatus;
import org.doscolas.model.Pet;
import org.doscolas.model.PetStatus;
import org.doscolas.model.Role;
import org.doscolas.model.TakeCare;
import org.doscolas.model.TakeCareStatus;
import org.doscolas.model.User;
import org.doscolas.repository.PaymentRepository;
import org.doscolas.repository.PayoutRepository;
import org.doscolas.repository.PetRepository;
import org.doscolas.repository.SitterBankAccountRepository;
import org.doscolas.repository.TakeCareRepository;
import org.doscolas.repository.UserRepository;
import org.doscolas.security.FintocWebhookVerifier;
import org.doscolas.security.JwtService;
import org.doscolas.security.PasswordEncoder;
import org.doscolas.service.FintocClient;
import org.doscolas.service.PaymentService;
import org.doscolas.service.PayoutService;
import org.doscolas.testsupport.TestDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real HTTP + real DB exercise of the {@code POST /webhooks/fintoc} gate: {@link FintocWebhookController}
 * wired exactly as {@code Main} wires it, listening on an ephemeral port, hit with actual signed
 * (and forged) requests over the wire. This is the part of the "restored Fintoc, now with JWS +
 * webhook signing" work that had only been checked manually before — see the project's Fintoc
 * memory notes on the 2026-08-06 restore.
 */
class FintocWebhookControllerIT {

    private static final String WEBHOOK_SECRET = new AppConfig().fintocWebhookSecret; // whsec_placeholder in dev

    private static ConnectionPool pool;
    private static UserRepository userRepository;
    private static PetRepository petRepository;
    private static TakeCareRepository takeCareRepository;
    private static PaymentRepository paymentRepository;
    private static PayoutRepository payoutRepository;
    private static HttpServer server;
    private static HttpClient httpClient;
    private static String baseUrl;

    @BeforeAll
    static void setUp() throws Exception {
        pool = TestDb.pool();
        AppConfig config = new AppConfig();

        userRepository = new UserRepository(pool);
        petRepository = new PetRepository(pool);
        takeCareRepository = new TakeCareRepository(pool);
        paymentRepository = new PaymentRepository(pool);
        payoutRepository = new PayoutRepository(pool);
        SitterBankAccountRepository sitterBankAccountRepository = new SitterBankAccountRepository(pool);

        FintocClient fintocClient = new FintocClient(config.fintocApiUrl, config.fintocSecretKey, config.fintocAccountId, null);
        PayoutService payoutService = new PayoutService(payoutRepository, sitterBankAccountRepository,
                takeCareRepository, petRepository, fintocClient, config.payoutMaxAttempts);
        PaymentService paymentService = new PaymentService(paymentRepository, userRepository, petRepository,
                fintocClient, takeCareRepository, config.fintocCallbackUrl, config.platformFeePercentage);

        FintocWebhookVerifier verifier = new FintocWebhookVerifier(WEBHOOK_SECRET);
        FintocWebhookController controller = new FintocWebhookController(verifier, paymentService, payoutService);

        JwtService jwtService = new JwtService(config.jwtSecret, config.jwtExpirationMs);
        Router router = new Router(jwtService, List.of("*"), "");
        controller.register(router);

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    static void tearDown() {
        server.stop(0);
        pool.close();
    }

    private long createUser(String label) {
        User user = new User();
        user.setUsername(label + "_" + System.nanoTime());
        user.setEmail(label + "_" + System.nanoTime() + "@test.com");
        user.setPassword(new PasswordEncoder().encode("Password123!"));
        user.setRoles(Set.of(Role.OWNER, Role.SITTER));
        user.setEnabled(true);
        user.setEmailVerified(true);
        return userRepository.insert(user).getId();
    }

    private long createPendingPayment(long payerId, String checkoutSessionId) {
        return createPendingPayment(payerId, checkoutSessionId, null);
    }

    private long createPendingPayment(long payerId, String checkoutSessionId, Long takeCareId) {
        Payment payment = new Payment();
        payment.setExternalReference(UUID.randomUUID().toString());
        payment.setUserId(payerId);
        payment.setTakeCareId(takeCareId);
        payment.setAmount(BigDecimal.valueOf(10000));
        payment.setPlatformFeeAmount(BigDecimal.valueOf(1000));
        payment.setTotalAmount(BigDecimal.valueOf(11000));
        payment.setCurrency("CLP");
        payment.setStatus(PaymentStatus.PENDING);
        long id = paymentRepository.insert(payment).getId();
        paymentRepository.updateFintocCheckoutSessionId(id, checkoutSessionId);
        return id;
    }

    private long createPet(long ownerId) {
        Pet pet = new Pet();
        pet.setName("WebhookTestPet");
        pet.setType("DOG");
        pet.setBreed("Mix");
        pet.setAge(2);
        pet.setWeight(10);
        pet.setOwnerId(ownerId);
        pet.setStatus(PetStatus.ACTIVE);
        pet.setNotes("");
        return petRepository.insert(pet).getId();
    }

    /** A take-care in the state a real payment targets today: sitter's applied, owner is paying to
     *  approve it before the sitting starts (see PaymentService#createPayment). */
    private long createWaitingApprovalTakeCare(long petId, long sitterId) {
        TakeCare takeCare = new TakeCare();
        takeCare.setPetId(petId);
        takeCare.setSitterId(sitterId);
        takeCare.setStartDate(java.time.LocalDate.now());
        takeCare.setEndDate(java.time.LocalDate.now().plusDays(2));
        takeCare.setDailyRate(25.0);
        takeCare.setTotalAmount(75.0);
        takeCare.setStatus(TakeCareStatus.WAITING_APPROVAL);
        takeCare.setNotes("");
        return takeCareRepository.insert(takeCare).getId();
    }

    private long createProcessingPayout(long paymentId, long sitterId, String fintocTransferId) {
        Payout payout = new Payout();
        payout.setPaymentId(paymentId);
        payout.setSitterId(sitterId);
        payout.setAmount(BigDecimal.valueOf(9000));
        payout.setCurrency("CLP");
        payout.setStatus(PayoutStatus.PROCESSING);
        payout.setIdempotencyKey(UUID.randomUUID().toString());
        payout.setFintocTransferId(fintocTransferId);
        payout.setAttempts(1);
        return payoutRepository.insert(payout).getId();
    }

    @Test
    void validlySignedCheckoutSessionFinishedEventCompletesThePayment() throws Exception {
        long payerId = createUser("payer");
        String sessionId = "cs_test_" + System.nanoTime();
        long paymentId = createPendingPayment(payerId, sessionId);

        String body = Json.write(Map.of("type", "checkout_session.finished",
                "data", Map.of("object", Map.of("id", sessionId, "status", "finished"))));

        HttpResponse<String> response = post(body, signedHeader(WEBHOOK_SECRET, body));

        assertEquals(200, response.statusCode());
        Payment updated = paymentRepository.findById(paymentId).orElseThrow();
        assertEquals(PaymentStatus.COMPLETED, updated.getStatus());
        assertNotNull(updated.getPaidAt());
    }

    @Test
    void validlySignedCheckoutSessionFinishedEventApprovesTheTakeCare() throws Exception {
        long ownerId = createUser("owner");
        long sitterId = createUser("sitter");
        long petId = createPet(ownerId);
        long takeCareId = createWaitingApprovalTakeCare(petId, sitterId);

        String sessionId = "cs_test_" + System.nanoTime();
        createPendingPayment(ownerId, sessionId, takeCareId);

        String body = Json.write(Map.of("type", "checkout_session.finished",
                "data", Map.of("object", Map.of("id", sessionId, "status", "finished"))));

        HttpResponse<String> response = post(body, signedHeader(WEBHOOK_SECRET, body));

        assertEquals(200, response.statusCode());
        TakeCare updated = takeCareRepository.findById(takeCareId).orElseThrow();
        assertEquals(TakeCareStatus.ON_SITTER, updated.getStatus());
    }

    @Test
    void forgedSignatureIsRejectedAndThePaymentIsLeftUntouched() throws Exception {
        long payerId = createUser("payer");
        String sessionId = "cs_test_" + System.nanoTime();
        long paymentId = createPendingPayment(payerId, sessionId);

        String body = Json.write(Map.of("type", "checkout_session.finished",
                "data", Map.of("object", Map.of("id", sessionId, "status", "finished"))));

        HttpResponse<String> response = post(body, signedHeader("an_attacker_guessed_secret", body));

        assertEquals(401, response.statusCode());
        Payment untouched = paymentRepository.findById(paymentId).orElseThrow();
        assertEquals(PaymentStatus.PENDING, untouched.getStatus());
        assertNull(untouched.getPaidAt());
    }

    @Test
    void missingSignatureHeaderIsRejected() throws Exception {
        String body = Json.write(Map.of("type", "checkout_session.finished",
                "data", Map.of("object", Map.of("id", "cs_whatever", "status", "finished"))));

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/webhooks/fintoc"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
    }

    @Test
    void validlySignedTransferSucceededEventCompletesThePayout() throws Exception {
        long payerId = createUser("payer");
        long sitterId = createUser("sitter");
        long paymentId = createPendingPayment(payerId, "cs_unrelated_" + System.nanoTime());
        String transferId = "tr_test_" + System.nanoTime();
        long payoutId = createProcessingPayout(paymentId, sitterId, transferId);

        String body = Json.write(Map.of("type", "transfer.succeeded",
                "data", Map.of("object", Map.of("id", transferId, "status", "succeeded"))));

        HttpResponse<String> response = post(body, signedHeader(WEBHOOK_SECRET, body));

        assertEquals(200, response.statusCode());
        Payout updated = payoutRepository.findById(payoutId).orElseThrow();
        assertEquals(PayoutStatus.COMPLETED, updated.getStatus());
        assertNotNull(updated.getCompletedAt());
    }

    @Test
    void validlySignedTransferFailedEventMarksThePayoutFailed() throws Exception {
        long payerId = createUser("payer");
        long sitterId = createUser("sitter");
        long paymentId = createPendingPayment(payerId, "cs_unrelated_" + System.nanoTime());
        String transferId = "tr_test_" + System.nanoTime();
        long payoutId = createProcessingPayout(paymentId, sitterId, transferId);

        String body = Json.write(Map.of("type", "transfer.failed",
                "data", Map.of("object", Map.of("id", transferId, "status", "failed"))));

        HttpResponse<String> response = post(body, signedHeader(WEBHOOK_SECRET, body));

        assertEquals(200, response.statusCode());
        Payout updated = payoutRepository.findById(payoutId).orElseThrow();
        assertEquals(PayoutStatus.FAILED, updated.getStatus());
        assertNotNull(updated.getLastErrorMessage());
    }

    private HttpResponse<String> post(String body, String signatureHeader) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/webhooks/fintoc"))
                        .header("Content-Type", "application/json")
                        .header("Fintoc-Signature", signatureHeader)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Same {@code t=<ts>,v1=<hex hmac>} shape Fintoc itself sends — see FintocWebhookVerifier. */
    private static String signedHeader(String secret, String rawBody) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + rawBody).getBytes());
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
