package org.doscolas.service;

import org.doscolas.dto.response.PayoutResponse;
import org.doscolas.exception.PayoutProviderException;
import org.doscolas.exception.ResourceNotFoundException;
import org.doscolas.model.Payment;
import org.doscolas.model.Payout;
import org.doscolas.model.PayoutStatus;
import org.doscolas.model.Pet;
import org.doscolas.model.SitterBankAccount;
import org.doscolas.model.TakeCare;
import org.doscolas.log.LogManager;
import org.doscolas.log.Logger;
import org.doscolas.repository.PayoutRepository;
import org.doscolas.repository.PetRepository;
import org.doscolas.repository.SitterBankAccountRepository;
import org.doscolas.repository.TakeCareRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a completed {@link Payment} into money in the sitter's bank account, via Fintoc Transfers.
 * A sitter without a registered bank account yet gets held in {@link PayoutStatus#PENDING_BANK_ACCOUNT}
 * until they add one ({@link #releaseHeldPayouts}); transient provider failures get retried up to
 * {@link #maxAttempts} times before landing in {@link PayoutStatus#FAILED} for an admin to inspect.
 */
public final class PayoutService {

    private static final Logger log = LogManager.getLogger(PayoutService.class);

    private final PayoutRepository payoutRepository;
    private final SitterBankAccountRepository bankAccountRepository;
    private final TakeCareRepository takeCareRepository;
    private final PetRepository petRepository;
    private final FintocClient fintocClient;
    private final int maxAttempts;

    public PayoutService(PayoutRepository payoutRepository, SitterBankAccountRepository bankAccountRepository,
                          TakeCareRepository takeCareRepository, PetRepository petRepository,
                          FintocClient fintocClient, int maxAttempts) {
        this.payoutRepository = payoutRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.takeCareRepository = takeCareRepository;
        this.petRepository = petRepository;
        this.fintocClient = fintocClient;
        this.maxAttempts = maxAttempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    /** Creates the payout row for a just-completed payment (idempotent — a payout is unique per payment). */
    public void createPendingPayout(Payment payment) {
        if (payoutRepository.existsByPaymentId(payment.getId())) return;
        if (payment.getTakeCareId() == null) {
            log.warn("Payment {} has no take_care_id — cannot determine which sitter to pay out", payment.getId());
            return;
        }
        TakeCare takeCare = takeCareRepository.findById(payment.getTakeCareId()).orElse(null);
        if (takeCare == null || takeCare.getSitterId() == null) {
            log.warn("Payment {} take-care {} has no assigned sitter — cannot pay out", payment.getId(), payment.getTakeCareId());
            return;
        }

        Payout payout = new Payout();
        payout.setPaymentId(payment.getId());
        payout.setSitterId(takeCare.getSitterId());
        payout.setTakeCareId(takeCare.getId());
        payout.setAmount(payment.getAmount());
        payout.setCurrency(payment.getCurrency());
        payout.setIdempotencyKey(UUID.randomUUID().toString());
        payout.setAttempts(0);

        boolean hasBankAccount = bankAccountRepository.findByUserId(takeCare.getSitterId()).isPresent();
        payout.setStatus(hasBankAccount ? PayoutStatus.PENDING : PayoutStatus.PENDING_BANK_ACCOUNT);
        payoutRepository.insert(payout);

        if (hasBankAccount) {
            processPayoutAsync(payout.getId());
        }
    }

    public void processPayoutAsync(long payoutId) {
        Thread.ofVirtual().name("payout-" + payoutId).start(() -> processPayout(payoutId));
    }

    public void processPayout(long payoutId) {
        Payout payout = payoutRepository.findById(payoutId).orElse(null);
        if (payout == null || payout.getStatus() != PayoutStatus.PENDING) return;

        Optional<SitterBankAccount> account = bankAccountRepository.findByUserId(payout.getSitterId());
        if (account.isEmpty()) {
            payout.setStatus(PayoutStatus.PENDING_BANK_ACCOUNT);
            payoutRepository.update(payout);
            return;
        }

        try {
            FintocClient.TransferCounterparty counterparty = toCounterparty(account.get());
            FintocClient.TransferResult result = fintocClient.initiateTransfer(
                    payout.getIdempotencyKey(), payout.getAmount().longValueExact(), payout.getCurrency(),
                    counterparty, "Dos Colas payout — payment #" + payout.getPaymentId());
            payout.setFintocTransferId(result.id());
            payout.setStatus("succeeded".equalsIgnoreCase(result.status()) ? PayoutStatus.COMPLETED : PayoutStatus.PROCESSING);
            if (payout.getStatus() == PayoutStatus.COMPLETED) payout.setCompletedAt(LocalDateTime.now());
            payoutRepository.update(payout);
        } catch (PayoutProviderException e) {
            log.error("Payout {} failed (retryable={})", e, payoutId, e.isRetryable());
            payout.setAttempts(payout.getAttempts() + 1);
            payout.setLastErrorMessage(e.getMessage());
            payout.setStatus(e.isRetryable() && payout.getAttempts() < maxAttempts ? PayoutStatus.PENDING : PayoutStatus.FAILED);
            payoutRepository.update(payout);
        }
    }

    private FintocClient.TransferCounterparty toCounterparty(SitterBankAccount account) {
        return new FintocClient.TransferCounterparty(
                account.getRut(), account.getHolderName(), account.getAccountNumber(),
                account.getAccountType(), account.getBankCode());
    }

    public void markCompleted(String fintocTransferId) {
        payoutRepository.findByFintocTransferId(fintocTransferId).ifPresent(payout -> {
            payout.setStatus(PayoutStatus.COMPLETED);
            payout.setCompletedAt(LocalDateTime.now());
            payoutRepository.update(payout);
        });
    }

    public void markFailed(String fintocTransferId, String reason) {
        payoutRepository.findByFintocTransferId(fintocTransferId).ifPresent(payout -> {
            payout.setStatus(PayoutStatus.FAILED);
            payout.setLastErrorMessage(reason);
            payoutRepository.update(payout);
        });
    }

    /** Called when a sitter registers a bank account for the first time — unblocks any held payouts. */
    public void releaseHeldPayouts(long sitterId) {
        for (Payout payout : payoutRepository.findBySitterIdAndStatus(sitterId, PayoutStatus.PENDING_BANK_ACCOUNT)) {
            payout.setStatus(PayoutStatus.PENDING);
            payoutRepository.update(payout);
            processPayoutAsync(payout.getId());
        }
    }

    /** Admin action: re-arm a failed payout with a fresh idempotency key and attempt count. */
    public PayoutResponse retry(long payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout with id " + payoutId + " not found"));
        payout.setStatus(PayoutStatus.PENDING);
        payout.setAttempts(0);
        payout.setIdempotencyKey(UUID.randomUUID().toString());
        payout.setLastErrorMessage(null);
        payoutRepository.update(payout);
        processPayoutAsync(payoutId);
        return toResponse(payout);
    }

    /** Polls Fintoc for payouts stuck in PROCESSING — a safety net alongside (unconfirmed) transfer webhooks. */
    public void refreshFromProvider(long payoutId) {
        Payout payout = payoutRepository.findById(payoutId).orElse(null);
        if (payout == null || payout.getStatus() != PayoutStatus.PROCESSING || payout.getFintocTransferId() == null) return;
        try {
            FintocClient.TransferResult result = fintocClient.getTransfer(payout.getFintocTransferId());
            if ("succeeded".equalsIgnoreCase(result.status())) {
                markCompleted(payout.getFintocTransferId());
            } else if ("failed".equalsIgnoreCase(result.status())) {
                markFailed(payout.getFintocTransferId(), "Fintoc reported the transfer as failed");
            }
        } catch (PayoutProviderException e) {
            log.error("Failed to refresh payout {} from Fintoc", e, payoutId);
        }
    }

    public List<PayoutResponse> getBySitterId(long sitterId) {
        return payoutRepository.findBySitterId(sitterId).stream().map(this::toResponse).toList();
    }

    public List<PayoutResponse> getAll() {
        return payoutRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<Payout> getByStatus(PayoutStatus status) {
        return payoutRepository.findByStatus(status);
    }

    private PayoutResponse toResponse(Payout p) {
        String petName = null;
        if (p.getTakeCareId() != null) {
            petName = takeCareRepository.findById(p.getTakeCareId())
                    .map(tc -> petRepository.findById(tc.getPetId()).map(Pet::getName).orElse(null))
                    .orElse(null);
        }
        return new PayoutResponse(p.getId(), p.getPaymentId(), p.getTakeCareId(), petName, p.getSitterId(),
                p.getAmount(), p.getCurrency(), p.getStatus().name(), p.getAttempts(), p.getLastErrorMessage(),
                p.getCreatedAt(), p.getCompletedAt());
    }
}
