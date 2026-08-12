package org.doscolas.dto.response;

import org.doscolas.json.Json;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public final class PayoutResponse {

    public final long id;
    public final long paymentId;
    public final Long takeCareId;
    public final String petName;
    public final long sitterId;
    public final BigDecimal amount;
    public final String currency;
    public final String status;
    public final int attempts;
    public final String lastErrorMessage;
    public final LocalDateTime createdAt;
    public final LocalDateTime completedAt;

    public PayoutResponse(long id, long paymentId, Long takeCareId, String petName, long sitterId,
                           BigDecimal amount, String currency, String status, int attempts,
                           String lastErrorMessage, LocalDateTime createdAt, LocalDateTime completedAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.takeCareId = takeCareId;
        this.petName = petName;
        this.sitterId = sitterId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.attempts = attempts;
        this.lastErrorMessage = lastErrorMessage;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = Json.obj();
        map.put("id", id);
        map.put("paymentId", paymentId);
        map.put("takeCareId", takeCareId);
        map.put("petName", petName);
        map.put("sitterId", sitterId);
        map.put("amount", amount);
        map.put("currency", currency);
        map.put("status", status);
        map.put("attempts", attempts);
        map.put("lastErrorMessage", lastErrorMessage);
        map.put("createdAt", createdAt != null ? createdAt.toString() : null);
        map.put("completedAt", completedAt != null ? completedAt.toString() : null);
        return map;
    }
}
