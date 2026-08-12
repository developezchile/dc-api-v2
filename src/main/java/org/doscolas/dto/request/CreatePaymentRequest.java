package org.doscolas.dto.request;

import java.math.BigDecimal;
import java.util.Map;

import static org.doscolas.validation.Validate.*;

public final class CreatePaymentRequest {

    public final Long userId;
    public final Long petId;
    public final Long takeCareId;
    public final BigDecimal amount;
    public final String currency;
    public final String description;

    private CreatePaymentRequest(Long userId, Long petId, Long takeCareId,
                                  BigDecimal amount, String currency, String description) {
        this.userId = userId;
        this.petId = petId;
        this.takeCareId = takeCareId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
    }

    public static CreatePaymentRequest fromJson(Map<String, Object> json) {
        Long userId = longVal(json, "userId");
        Long petId = longVal(json, "petId");
        Long takeCareId = longVal(json, "takeCareId");
        BigDecimal amount = decimalVal(json, "amount");
        String currency = str(json, "currency");
        String description = str(json, "description");

        var errors = newErrors();
        notNull(errors, "userId", userId);
        notNull(errors, "amount", amount);
        positive(errors, "amount", amount);
        check(errors);

        return new CreatePaymentRequest(userId, petId, takeCareId, amount, currency, description);
    }
}
