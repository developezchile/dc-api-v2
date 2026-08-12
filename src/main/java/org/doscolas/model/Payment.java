package org.doscolas.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fintoc is the only payment rail (Transbank was removed, Stripe was explored but Chile support
 * for Connect is unconfirmed). {@code fintocCheckoutSessionId} is the one gateway-specific field —
 * no provider enum, since there's only one provider.
 */
public final class Payment {

    private Long id;
    private String externalReference;
    private String authorizationCode;
    private String cardLastFourDigits;
    private Long petId;
    private Long userId;
    private Long takeCareId;
    private BigDecimal amount;
    private BigDecimal platformFeeAmount;
    private BigDecimal totalAmount;
    private String currency;
    private PaymentStatus status = PaymentStatus.PENDING;
    private String description;
    private String fintocCheckoutSessionId;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }

    public String getCardLastFourDigits() {
        return cardLastFourDigits;
    }

    public void setCardLastFourDigits(String cardLastFourDigits) {
        this.cardLastFourDigits = cardLastFourDigits;
    }

    public Long getPetId() {
        return petId;
    }

    public void setPetId(Long petId) {
        this.petId = petId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTakeCareId() {
        return takeCareId;
    }

    public void setTakeCareId(Long takeCareId) {
        this.takeCareId = takeCareId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getPlatformFeeAmount() {
        return platformFeeAmount;
    }

    public void setPlatformFeeAmount(BigDecimal platformFeeAmount) {
        this.platformFeeAmount = platformFeeAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFintocCheckoutSessionId() {
        return fintocCheckoutSessionId;
    }

    public void setFintocCheckoutSessionId(String fintocCheckoutSessionId) {
        this.fintocCheckoutSessionId = fintocCheckoutSessionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
