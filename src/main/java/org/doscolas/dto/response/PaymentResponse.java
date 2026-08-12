package org.doscolas.dto.response;

import org.doscolas.json.Json;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public final class PaymentResponse {

    public final long id;
    public final String externalReference;
    public final String authorizationCode;
    public final String cardLastFourDigits;
    public final Long petId;
    public final String petName;
    public final Long takeCareId;
    public final long userId;
    public final String userName;
    public final BigDecimal amount;
    public final BigDecimal platformFeeAmount;
    public final BigDecimal totalAmount;
    public final String currency;
    public final String status;
    public final String description;
    public final String fintocCheckoutSessionId;
    /** Only non-null immediately after {@code POST /payments} — the redirect URL isn't re-fetchable
     *  once the owner has already been sent there once, so this is a one-shot value, not persisted. */
    public final String fintocRedirectUrl;
    public final LocalDateTime createdAt;
    public final LocalDateTime paidAt;

    public PaymentResponse(long id, String externalReference, String authorizationCode,
                            String cardLastFourDigits, Long petId, String petName, Long takeCareId, long userId,
                            String userName, BigDecimal amount, BigDecimal platformFeeAmount, BigDecimal totalAmount,
                            String currency, String status, String description, String fintocCheckoutSessionId,
                            String fintocRedirectUrl, LocalDateTime createdAt, LocalDateTime paidAt) {
        this.id = id;
        this.externalReference = externalReference;
        this.authorizationCode = authorizationCode;
        this.cardLastFourDigits = cardLastFourDigits;
        this.petId = petId;
        this.petName = petName;
        this.takeCareId = takeCareId;
        this.userId = userId;
        this.userName = userName;
        this.amount = amount;
        this.platformFeeAmount = platformFeeAmount;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.status = status;
        this.description = description;
        this.fintocCheckoutSessionId = fintocCheckoutSessionId;
        this.fintocRedirectUrl = fintocRedirectUrl;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = Json.obj();
        map.put("id", id);
        map.put("externalReference", externalReference);
        map.put("authorizationCode", authorizationCode);
        map.put("cardLastFourDigits", cardLastFourDigits);
        map.put("petId", petId);
        map.put("petName", petName);
        map.put("takeCareId", takeCareId);
        map.put("userId", userId);
        map.put("userName", userName);
        map.put("amount", amount);
        map.put("platformFeeAmount", platformFeeAmount);
        map.put("totalAmount", totalAmount);
        map.put("currency", currency);
        map.put("status", status);
        map.put("description", description);
        map.put("fintocCheckoutSessionId", fintocCheckoutSessionId);
        map.put("fintocRedirectUrl", fintocRedirectUrl);
        map.put("createdAt", createdAt != null ? createdAt.toString() : null);
        map.put("paidAt", paidAt != null ? paidAt.toString() : null);
        return map;
    }
}
