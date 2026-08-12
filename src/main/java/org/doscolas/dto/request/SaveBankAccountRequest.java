package org.doscolas.dto.request;

import java.util.Map;

import static org.doscolas.validation.Validate.*;

public final class SaveBankAccountRequest {

    public final String bankCode;
    public final String bankName;
    public final String accountType;
    public final String accountNumber;
    public final String rut;
    public final String holderName;

    private SaveBankAccountRequest(String bankCode, String bankName, String accountType,
                                    String accountNumber, String rut, String holderName) {
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.accountType = accountType;
        this.accountNumber = accountNumber;
        this.rut = rut;
        this.holderName = holderName;
    }

    public static SaveBankAccountRequest fromJson(Map<String, Object> json) {
        String bankCode = str(json, "bankCode");
        String bankName = str(json, "bankName");
        String accountType = str(json, "accountType");
        String accountNumber = str(json, "accountNumber");
        String rut = str(json, "rut");
        String holderName = str(json, "holderName");

        var errors = newErrors();
        notBlank(errors, "bankCode", bankCode);
        notBlank(errors, "accountType", accountType);
        notBlank(errors, "accountNumber", accountNumber);
        notBlank(errors, "rut", rut);
        notBlank(errors, "holderName", holderName);
        check(errors);

        return new SaveBankAccountRequest(bankCode, bankName, accountType, accountNumber, rut, holderName);
    }
}
