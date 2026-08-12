package org.doscolas.service;

import org.doscolas.db.ConnectionPool;
import org.doscolas.model.Pet;
import org.doscolas.model.PetStatus;
import org.doscolas.model.Role;
import org.doscolas.model.TakeCare;
import org.doscolas.model.TakeCareStatus;
import org.doscolas.model.User;
import org.doscolas.repository.PetRepository;
import org.doscolas.repository.TakeCareRepository;
import org.doscolas.repository.UserRepository;
import org.doscolas.security.PasswordEncoder;
import org.doscolas.testsupport.TestDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the double-assignment race condition fixed in
 * {@code TakeCareRepository.assignSitterIfLookingForSitter}: many sitters racing the same open
 * take-care row must yield exactly one winner. Complements the HTTP-level Playwright spec
 * ({@code dc-ui/e2e/take-care-race.spec.ts}) with a faster, server-free repository-level check.
 */
class TakeCareAssignmentRaceIT {

    private static ConnectionPool pool;
    private static UserRepository userRepository;
    private static PetRepository petRepository;
    private static TakeCareRepository takeCareRepository;

    @BeforeAll
    static void setUp() {
        pool = TestDb.pool();
        userRepository = new UserRepository(pool);
        petRepository = new PetRepository(pool);
        takeCareRepository = new TakeCareRepository(pool);
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    @Test
    void onlyOneConcurrentAssignmentWinsPerTakeCare() throws InterruptedException {
        long ownerId = createUser("race_owner");
        long petId = createPet(ownerId);
        long takeCareId = createOpenTakeCare(petId);

        int sitterCount = 10;
        List<Long> sitterIds = new ArrayList<>();
        for (int i = 0; i < sitterCount; i++) {
            sitterIds.add(createUser("race_sitter" + i));
        }

        ExecutorService executor = Executors.newFixedThreadPool(sitterCount);
        CountDownLatch ready = new CountDownLatch(sitterCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        for (long sitterId : sitterIds) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (takeCareRepository.assignSitterIfLookingForSitter(takeCareId, sitterId)) {
                    successCount.incrementAndGet();
                }
            });
        }

        ready.await();
        go.countDown(); // release every thread at once to maximize contention on the same row
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(1, successCount.get(), "exactly one concurrent assignment should win");

        TakeCare finalState = takeCareRepository.findById(takeCareId).orElseThrow();
        assertEquals(TakeCareStatus.WAITING_APPROVAL, finalState.getStatus());
        assertTrue(sitterIds.contains(finalState.getSitterId()));
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

    private long createPet(long ownerId) {
        Pet pet = new Pet();
        pet.setName("RaceTestPet");
        pet.setType("DOG");
        pet.setBreed("Mix");
        pet.setAge(2);
        pet.setWeight(10);
        pet.setOwnerId(ownerId);
        pet.setStatus(PetStatus.ACTIVE);
        pet.setNotes("");
        return petRepository.insert(pet).getId();
    }

    private long createOpenTakeCare(long petId) {
        TakeCare takeCare = new TakeCare();
        takeCare.setPetId(petId);
        takeCare.setStartDate(LocalDate.now());
        takeCare.setEndDate(LocalDate.now().plusDays(2));
        takeCare.setDailyRate(25.0);
        takeCare.setTotalAmount(50.0);
        takeCare.setStatus(TakeCareStatus.LOOKING_FOR_SITTER);
        takeCare.setNotes("");
        return takeCareRepository.insert(takeCare).getId();
    }
}
