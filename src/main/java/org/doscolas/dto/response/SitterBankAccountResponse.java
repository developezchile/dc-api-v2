package org.doscolas.dto.response;

import org.doscolas.json.Json;

import java.time.LocalDateTime;
import java.util.Map;

public class SitterBankAccountResponse {

    public final long id;
    public final String bankCode;
    public final String bankName;
    public final String accountType;
    public final String accountNumberMasked;
    public final String rut;
    public final String holderName;
    public final LocalDateTime updatedAt;

    public SitterBankAccountResponse(long id, String bankCode, String bankName, String accountType,
                                      String accountNumberMasked, String rut, String holderName, LocalDateTime updatedAt) {
        this.id = id;
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.accountType = accountType;
        this.accountNumberMasked = accountNumberMasked;
        this.rut = rut;
        this.holderName = holderName;
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = Json.obj();
        map.put("id", id);
        map.put("bankCode", bankCode);
        map.put("bankName", bankName);
        map.put("accountType", accountType);
        map.put("accountNumberMasked", accountNumberMasked);
        map.put("rut", rut);
        map.put("holderName", holderName);
        map.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
        return map;
    }

    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
