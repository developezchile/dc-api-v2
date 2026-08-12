package org.doscolas.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public final class AdminBankAccountResponse extends SitterBankAccountResponse {

    public final long userId;
    public final String username;

    public AdminBankAccountResponse(long id, long userId, String username, String bankCode, String bankName,
                                     String accountType, String accountNumberMasked, String rut,
                                     String holderName, LocalDateTime updatedAt) {
        super(id, bankCode, bankName, accountType, accountNumberMasked, rut, holderName, updatedAt);
        this.userId = userId;
        this.username = username;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();
        map.put("userId", userId);
        map.put("username", username);
        return map;
    }
}
