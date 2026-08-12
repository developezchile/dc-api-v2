package org.doscolas.service;

import org.doscolas.dto.request.SaveBankAccountRequest;
import org.doscolas.dto.response.AdminBankAccountResponse;
import org.doscolas.dto.response.SitterBankAccountResponse;
import org.doscolas.exception.ResourceNotFoundException;
import org.doscolas.model.SitterBankAccount;
import org.doscolas.model.User;
import org.doscolas.repository.SitterBankAccountRepository;
import org.doscolas.repository.UserRepository;

import java.util.List;

public final class SitterBankAccountService {

    private final SitterBankAccountRepository repository;
    private final UserRepository userRepository;
    private final PayoutService payoutService;

    public SitterBankAccountService(SitterBankAccountRepository repository, UserRepository userRepository,
                                     PayoutService payoutService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.payoutService = payoutService;
    }

    public SitterBankAccountResponse getByUserId(long userId) {
        SitterBankAccount account = repository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No bank account on file for user " + userId));
        return toResponse(account);
    }

    public SitterBankAccountResponse save(long userId, SaveBankAccountRequest request) {
        SitterBankAccount account = repository.findByUserId(userId).orElseGet(SitterBankAccount::new);
        boolean isNew = account.getId() == null;
        account.setUserId(userId);
        account.setBankCode(request.bankCode);
        account.setBankName(request.bankName);
        account.setAccountType(request.accountType);
        account.setAccountNumber(request.accountNumber);
        account.setRut(request.rut);
        account.setHolderName(request.holderName);

        SitterBankAccount saved = isNew ? repository.insert(account) : repository.update(account);
        payoutService.releaseHeldPayouts(userId);
        return toResponse(saved);
    }

    public List<AdminBankAccountResponse> listAllForAdmin() {
        return repository.findAll().stream().map(account -> {
            User user = userRepository.findById(account.getUserId()).orElse(null);
            return new AdminBankAccountResponse(account.getId(), account.getUserId(),
                    user != null ? user.getUsername() : null, account.getBankCode(), account.getBankName(),
                    account.getAccountType(), SitterBankAccountResponse.mask(account.getAccountNumber()),
                    account.getRut(), account.getHolderName(), account.getUpdatedAt());
        }).toList();
    }

    public void deleteByIdAsAdmin(long id) {
        repository.deleteById(id);
    }

    private SitterBankAccountResponse toResponse(SitterBankAccount account) {
        return new SitterBankAccountResponse(account.getId(), account.getBankCode(), account.getBankName(),
                account.getAccountType(), SitterBankAccountResponse.mask(account.getAccountNumber()),
                account.getRut(), account.getHolderName(), account.getUpdatedAt());
    }
}
