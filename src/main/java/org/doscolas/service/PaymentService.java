package org.doscolas.service;

import org.doscolas.dto.request.CreatePaymentRequest;
import org.doscolas.dto.response.PaymentResponse;
import org.doscolas.exception.BusinessRuleException;
import org.doscolas.exception.FintocApiException;
import org.doscolas.exception.ForbiddenException;
import org.doscolas.exception.ResourceNotFoundException;
import org.doscolas.log.LogManager;
import org.doscolas.log.Logger;
import org.doscolas.model.Payment;
import org.doscolas.model.PaymentStatus;
import org.doscolas.model.Pet;
import org.doscolas.model.TakeCare;
import org.doscolas.model.TakeCareStatus;
import org.doscolas.model.User;
import org.doscolas.repository.PaymentRepository;
import org.doscolas.repository.PetRepository;
import org.doscolas.repository.TakeCareRepository;
import org.doscolas.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Collects payment from owners via Fintoc Checkout Sessions (a hosted, Fintoc-redirect page — see
 * {@link FintocClient}) to approve a take-care request: paying is what moves a {@code TakeCare}
 * from {@code WAITING_APPROVAL} to {@code ON_SITTER} (see {@link TakeCareRepository#approveIfWaitingApproval}) —
 * the sitting doesn't start until it's paid for. Payout to the sitter happens later, when the
 * sitter marks the job done (see {@link TakeCareService}), not here. The checkout session is the
 * source of truth for whether money actually moved: {@link #handleFintocCheckoutSessionFinished}
 * (webhook) and {@link #syncFintocPayment} (an eager poll from the result page, since webhooks can
 * lag the redirect) both funnel into the same state transition.
 */
public final class PaymentService {

    private static final Logger log = LogManager.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final FintocClient fintocClient;
    private final TakeCareRepository takeCareRepository;
    /** Base URL for Fintoc's success_url/cancel_url — deliberately separate from AppConfig's
     *  general FRONTEND_URL (used for CORS + email links): Fintoc rejects non-HTTPS callback URLs,
     *  so this needs its own HTTPS-capable value independent of whatever FRONTEND_URL is set to
     *  for local dev (plain http://localhost). */
    private final String fintocCallbackBaseUrl;
    private final double platformFeePercentage;

    public PaymentService(PaymentRepository paymentRepository, UserRepository userRepository,
                           PetRepository petRepository, FintocClient fintocClient, TakeCareRepository takeCareRepository,
                           String fintocCallbackBaseUrl, double platformFeePercentage) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.petRepository = petRepository;
        this.fintocClient = fintocClient;
        this.takeCareRepository = takeCareRepository;
        this.fintocCallbackBaseUrl = fintocCallbackBaseUrl;
        this.platformFeePercentage = platformFeePercentage;
    }

    public PaymentResponse createPayment(CreatePaymentRequest request, long currentUserId) {
        if (request.userId != currentUserId) {
            throw new ForbiddenException("You can only create payments for yourself");
        }
        if (request.takeCareId != null) {
            TakeCare takeCare = takeCareRepository.findById(request.takeCareId)
                    .orElseThrow(() -> new ResourceNotFoundException("TakeCare with id " + request.takeCareId + " not found"));
            if (takeCare.getStatus() != TakeCareStatus.WAITING_APPROVAL) {
                throw new BusinessRuleException("This take-care is not awaiting payment approval");
            }
            if (paymentRepository.existsByTakeCareIdAndStatusIn(request.takeCareId, List.of(PaymentStatus.PENDING, PaymentStatus.COMPLETED))) {
                throw new BusinessRuleException("A payment already exists for this take-care");
            }
        }

        String currency = (request.currency == null || request.currency.isBlank()) ? "CLP" : request.currency;
        BigDecimal platformFeeAmount = request.amount.multiply(BigDecimal.valueOf(platformFeePercentage))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal totalAmount = request.amount.add(platformFeeAmount);

        Payment payment = new Payment();
        payment.setExternalReference(java.util.UUID.randomUUID().toString());
        payment.setPetId(request.petId);
        payment.setUserId(request.userId);
        payment.setTakeCareId(request.takeCareId);
        payment.setAmount(request.amount);
        payment.setPlatformFeeAmount(platformFeeAmount);
        payment.setTotalAmount(totalAmount);
        payment.setCurrency(currency);
        payment.setDescription(request.description);
        payment = paymentRepository.insert(payment);

        String successUrl = fintocCallbackBaseUrl + "/payment/result?paymentId=" + payment.getId() + "&status=success";
        String cancelUrl = fintocCallbackBaseUrl + "/payment/result?paymentId=" + payment.getId() + "&status=cancelled";
        try {
            FintocClient.CheckoutSessionResult session = fintocClient.createCheckoutSession(
                    totalAmount.longValueExact(), currency, successUrl, cancelUrl);
            paymentRepository.updateFintocCheckoutSessionId(payment.getId(), session.id());
            payment.setFintocCheckoutSessionId(session.id());
            return toResponse(payment, session.redirectUrl());
        } catch (FintocApiException e) {
            log.error("Failed to create Fintoc checkout session for payment {}", e, payment.getId());
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.update(payment);
            throw new BusinessRuleException("No se pudo iniciar el pago. Intenta nuevamente en unos minutos.");
        }
    }

    /** Webhook handler for {@code checkout_session.finished} / {@code .expired} events. */
    public void handleFintocCheckoutSessionFinished(String checkoutSessionId, String sessionStatus) {
        Payment payment = paymentRepository.findByFintocCheckoutSessionId(checkoutSessionId).orElse(null);
        if (payment == null) {
            log.warn("Fintoc webhook referenced unknown checkout session {}", checkoutSessionId);
            return;
        }
        applySessionStatus(payment, sessionStatus);
    }

    /** Eager check used by the result page right after redirect — webhooks can lag a few seconds. */
    public PaymentResponse syncFintocPayment(long paymentId, long currentUserId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with id " + paymentId + " not found"));
        if (payment.getUserId() != currentUserId) {
            throw new ForbiddenException("Only the payer can sync this payment");
        }
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getFintocCheckoutSessionId() != null) {
            FintocClient.CheckoutSessionResult session = fintocClient.getCheckoutSession(payment.getFintocCheckoutSessionId());
            applySessionStatus(payment, session.status());
        }
        return toResponse(payment, null);
    }

    private void applySessionStatus(Payment payment, String sessionStatus) {
        if (payment.getStatus() != PaymentStatus.PENDING) return; // already settled — idempotent
        if ("finished".equalsIgnoreCase(sessionStatus)) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.update(payment);
            if (payment.getTakeCareId() != null) {
                takeCareRepository.approveIfWaitingApproval(payment.getTakeCareId());
            }
        } else if ("expired".equalsIgnoreCase(sessionStatus)) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.update(payment);
        }
        // "created" / "in_progress" — still waiting, nothing to do yet.
    }

    public PaymentResponse updateStatus(long id, PaymentStatus status) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with id " + id + " not found"));
        payment.setStatus(status);
        return toResponse(paymentRepository.update(payment), null);
    }

    public PaymentResponse getById(long id, long currentUserId, boolean isAdmin) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with id " + id + " not found"));
        if (!isAdmin && payment.getUserId() != currentUserId) {
            throw new ForbiddenException("Only the payer can view this payment");
        }
        return toResponse(payment, null);
    }

    public List<PaymentResponse> getByUserId(long userId) {
        if (!userRepository.existsById(userId)) throw new ResourceNotFoundException("User with id " + userId + " not found");
        return paymentRepository.findByUserId(userId).stream().map(p -> toResponse(p, null)).toList();
    }

    public List<PaymentResponse> getByPetId(long petId, long currentUserId, boolean isAdmin) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet with id " + petId + " not found"));
        if (!isAdmin && (pet.getOwnerId() == null || pet.getOwnerId() != currentUserId)) {
            throw new ForbiddenException("Only the pet's owner can view its payments");
        }
        return paymentRepository.findByPetId(petId).stream().map(p -> toResponse(p, null)).toList();
    }

    public List<PaymentResponse> getAll() {
        return paymentRepository.findAll().stream().map(p -> toResponse(p, null)).toList();
    }

    private PaymentResponse toResponse(Payment p, String fintocRedirectUrl) {
        User user = userRepository.findById(p.getUserId()).orElse(null);
        String userName = user != null
                ? ((user.getFirstName() != null ? user.getFirstName() : "")
                    + (user.getLastName() != null ? " " + user.getLastName() : "")).trim()
                : null;
        String petName = p.getPetId() != null ? petRepository.findById(p.getPetId()).map(Pet::getName).orElse(null) : null;

        return new PaymentResponse(p.getId(), p.getExternalReference(), p.getAuthorizationCode(),
                p.getCardLastFourDigits(), p.getPetId(), petName, p.getTakeCareId(), p.getUserId(), userName,
                p.getAmount(), p.getPlatformFeeAmount(), p.getTotalAmount(), p.getCurrency(),
                p.getStatus().name(), p.getDescription(), p.getFintocCheckoutSessionId(), fintocRedirectUrl,
                p.getCreatedAt(), p.getPaidAt());
    }
}
