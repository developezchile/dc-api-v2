package org.doscolas.service;

import org.doscolas.db.ConnectionPool;
import org.doscolas.dto.request.AssignPetRequest;
import org.doscolas.dto.request.CreatePaymentRequest;
import org.doscolas.dto.request.UpdatePetRequest;
import org.doscolas.exception.ForbiddenException;
import org.doscolas.model.Pet;
import org.doscolas.model.PetStatus;
import org.doscolas.model.Payment;
import org.doscolas.model.PaymentStatus;
import org.doscolas.model.Role;
import org.doscolas.model.User;
import org.doscolas.repository.PaymentRepository;
import org.doscolas.repository.PayoutRepository;
import org.doscolas.repository.PetRepository;
import org.doscolas.repository.SitterBankAccountRepository;
import org.doscolas.repository.TakeCareRepository;
import org.doscolas.repository.UserRepository;
import org.doscolas.security.PasswordEncoder;
import org.doscolas.testsupport.TestDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for a real, verified authorization gap: several endpoints used to check only
 * "is logged in", not "do you own this" — any authenticated user could read/mutate other users'
 * pets, take-care listings, and payments. Fixed by pushing ownership checks (owner-or-admin) into
 * the service layer; these tests lock that in.
 */
class AuthorizationIT {

    private static ConnectionPool pool;
    private static UserRepository userRepository;
    private static PetRepository petRepository;
    private static TakeCareRepository takeCareRepository;
    private static PaymentRepository paymentRepository;
    private static PetService petService;
    private static TakeCareService takeCareService;
    private static PaymentService paymentService;

    @BeforeAll
    static void setUp() {
        pool = TestDb.pool();
        userRepository = new UserRepository(pool);
        petRepository = new PetRepository(pool);
        takeCareRepository = new TakeCareRepository(pool);
        paymentRepository = new PaymentRepository(pool);

        petService = new PetService(petRepository, userRepository);

        FintocClient fintocClient = new FintocClient("https://api.fintoc.com", "sk_test_placeholder", "acc_placeholder", null);
        PayoutService payoutService = new PayoutService(new PayoutRepository(pool), new SitterBankAccountRepository(pool),
                takeCareRepository, petRepository, fintocClient, 3);
        takeCareService = new TakeCareService(takeCareRepository, petRepository, userRepository, paymentRepository, payoutService);
        paymentService = new PaymentService(paymentRepository, userRepository, petRepository,
                fintocClient, takeCareRepository, "http://localhost:3000", 0.10);
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    private long createUser(String label) {
        User user = new User();
        user.setUsername(label + "_" + System.nanoTime());
        user.setEmail(label + "_" + System.nanoTime() + "@test.com");
        user.setPassword(new PasswordEncoder().encode("Password123!"));
        user.setRoles(Set.of(Role.OWNER));
        user.setEnabled(true);
        user.setEmailVerified(true);
        return userRepository.insert(user).getId();
    }

    private long createPet(long ownerId) {
        Pet pet = new Pet();
        pet.setName("AuthzTestPet");
        pet.setType("DOG");
        pet.setBreed("Mix");
        pet.setAge(2);
        pet.setWeight(10);
        pet.setOwnerId(ownerId);
        pet.setStatus(PetStatus.ACTIVE);
        pet.setNotes("");
        return petRepository.insert(pet).getId();
    }

    // --- PetService ---

    @Test
    void nonOwnerCannotUpdateAnotherUsersPet() {
        long ownerId = createUser("owner");
        long strangerId = createUser("stranger");
        long petId = createPet(ownerId);

        var req = UpdatePetRequest.fromJson(Map.of("name", "Hacked"));
        assertThrows(ForbiddenException.class, () -> petService.update(petId, req, strangerId, false));
    }

    @Test
    void ownerCanUpdateTheirOwnPet() {
        long ownerId = createUser("owner");
        long petId = createPet(ownerId);

        var req = UpdatePetRequest.fromJson(Map.of("name", "Renamed"));
        assertDoesNotThrow(() -> petService.update(petId, req, ownerId, false));
    }

    @Test
    void adminCanUpdateAnyPet() {
        long ownerId = createUser("owner");
        long adminId = createUser("admin");
        long petId = createPet(ownerId);

        var req = UpdatePetRequest.fromJson(Map.of("name", "AdminRenamed"));
        assertDoesNotThrow(() -> petService.update(petId, req, adminId, true));
    }

    @Test
    void nonOwnerCannotDeleteOrChangeStatusOfAnotherUsersPet() {
        long ownerId = createUser("owner");
        long strangerId = createUser("stranger");
        long petId = createPet(ownerId);

        assertThrows(ForbiddenException.class, () -> petService.delete(petId, strangerId, false));
        assertThrows(ForbiddenException.class,
                () -> petService.updateStatus(petId, PetStatus.INACTIVE, strangerId, false));
    }

    // --- TakeCareService ---

    @Test
    void nonOwnerCannotListAnotherUsersPetAsLookingForSitter() {
        long ownerId = createUser("owner");
        long strangerId = createUser("stranger");
        long petId = createPet(ownerId);

        AssignPetRequest req = AssignPetRequest.fromJson(Map.of(
                "petId", petId, "startDate", java.time.LocalDate.now().toString(),
                "endDate", java.time.LocalDate.now().plusDays(2).toString(),
                "dailyRate", 25.0, "status", "LOOKING_FOR_SITTER"));

        assertThrows(ForbiddenException.class, () -> takeCareService.create(req, strangerId, false));
    }

    @Test
    void onlyPetOwnerCanReadTakeCareHistoryForTheirPet() {
        long ownerId = createUser("owner");
        long strangerId = createUser("stranger");
        long petId = createPet(ownerId);

        assertDoesNotThrow(() -> takeCareService.getByPetId(petId, ownerId, false));
        assertThrows(ForbiddenException.class, () -> takeCareService.getByPetId(petId, strangerId, false));
        assertDoesNotThrow(() -> takeCareService.getByPetId(petId, strangerId, true)); // admin
    }

    // --- PaymentService ---

    @Test
    void cannotCreateAPaymentAttributedToSomeoneElse() {
        long selfId = createUser("payer");
        long someoneElseId = createUser("victim");

        var req = CreatePaymentRequest.fromJson(Map.of("userId", someoneElseId, "amount", 1000));

        assertThrows(ForbiddenException.class, () -> paymentService.createPayment(req, selfId));
    }

    @Test
    void onlyThePayerOrAdminCanReadAPayment() {
        long payerId = createUser("payer");
        long strangerId = createUser("stranger");
        long paymentId = insertRawPayment(payerId, null);

        assertDoesNotThrow(() -> paymentService.getById(paymentId, payerId, false));
        assertThrows(ForbiddenException.class, () -> paymentService.getById(paymentId, strangerId, false));
        assertDoesNotThrow(() -> paymentService.getById(paymentId, strangerId, true)); // admin
    }

    @Test
    void onlyThePetOwnerOrAdminCanListPaymentsForAPet() {
        long ownerId = createUser("owner");
        long strangerId = createUser("stranger");
        long petId = createPet(ownerId);
        insertRawPayment(ownerId, petId);

        assertDoesNotThrow(() -> paymentService.getByPetId(petId, ownerId, false));
        assertThrows(ForbiddenException.class, () -> paymentService.getByPetId(petId, strangerId, false));
    }

    private long insertRawPayment(long userId, Long petId) {
        Payment payment = new Payment();
        payment.setExternalReference(UUID.randomUUID().toString());
        payment.setUserId(userId);
        payment.setPetId(petId);
        payment.setAmount(BigDecimal.valueOf(1000));
        payment.setPlatformFeeAmount(BigDecimal.ZERO);
        payment.setTotalAmount(BigDecimal.valueOf(1000));
        payment.setCurrency("CLP");
        payment.setStatus(PaymentStatus.PENDING);
        return paymentRepository.insert(payment).getId();
    }
}
