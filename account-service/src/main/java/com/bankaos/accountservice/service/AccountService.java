package com.bankaos.accountservice.service;

import com.bankaos.accountservice.dto.CreateAccountRequest;
import com.bankaos.accountservice.dto.CreateAccountResponse;
import com.bankaos.accountservice.enums.AccountStatus;
import com.bankaos.accountservice.enums.AccountType;
import com.bankaos.accountservice.exception.AccountNotActiveException;
import com.bankaos.accountservice.exception.AccountNotFoundException;
import com.bankaos.accountservice.exception.InsufficientFundsException;
import com.bankaos.accountservice.model.Account;
import com.bankaos.accountservice.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Optional;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final AccountRepository accountRepository;
    private  static SecureRandom secureRandom = new SecureRandom();

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public CreateAccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for: {}",request.getEmail());

        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setAccountNumber(generateAccountNumber());
        account.setBalance(request.getInitialDeposit());
        switch (request.getAccountType()) {

            case SAVINGS:
                account.setDailyTransactionLimit(new BigDecimal("100000"));
                break;

            case CURRENT:
                account.setDailyTransactionLimit(new BigDecimal("500000"));
                break;

            case FIXED_DEPOSIT:
                account.setDailyTransactionLimit(BigDecimal.ZERO);
                break;
        }

        Account savedAccount = accountRepository.save(account);
        log.info("Account created: {}",savedAccount.getAccountNumber());
        return mapToResponse(savedAccount);
    }

    private CreateAccountResponse mapToResponse(Account savedAccount) {
        CreateAccountResponse accountResponse = new CreateAccountResponse();
        accountResponse.setId(savedAccount.getId());
        accountResponse.setAccountNumber(savedAccount.getAccountNumber());
        accountResponse.setAccountHolderName(savedAccount.getAccountHolderName());
        accountResponse.setAccountType(savedAccount.getAccountType());
        accountResponse.setEmail(savedAccount.getEmail());
        accountResponse.setPhone(savedAccount.getPhone());
        accountResponse.setAccountStatus(savedAccount.getAccountStatus());
        accountResponse.setBalance(savedAccount.getBalance());
        accountResponse.setDailyTransactionLimit(savedAccount.getDailyTransactionLimit());
        accountResponse.setCreatedAt(savedAccount.getCreatedAt());
        return accountResponse;
    }

    private String generateAccountNumber() {
        String accountNumber;
        do{
            long number = secureRandom.nextLong(1_000_000_000_000L); // generate a random number from 0 to 999999999999
            accountNumber = String.format("%012d",number);
        }while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;

    }

    public CreateAccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                ()->  new AccountNotFoundException("Account not found with account number : " + accountNumber)
        );
        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                ()->  new AccountNotFoundException("Account not found with account number : " + accountNumber)
        );
        return account.getBalance();

    }

    /**
     * Block account - called by fraud detection service via kafka
     * @param accountNumber
     */
    public void blockAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                ()->  new AccountNotFoundException("Account not found with account number : " + accountNumber)
        );
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked : " + accountNumber);

    }

    /**
     * Deduct balance from sender account,
     * Called by transaction Service
     * @param accountNumber
     * @param amount
     */
    public void deductBalance(String accountNumber, BigDecimal amount) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                ()->  new AccountNotFoundException("Account not found with account number : " + accountNumber)
        );

        if(account.getAccountStatus() != AccountStatus.ACTIVE){
            throw new AccountNotActiveException("Account not active: " + accountNumber);
        }

        if(account.getBalance().compareTo(amount) < 0){
            throw new InsufficientFundsException("Insufficient funds for account : " + accountNumber);
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Balance updated for account {}. New Balance: {}",accountNumber,account.getBalance());

    }

    /**
     * Called by Transaction Service via Kafka
     * @param accountNumber
     * @param amount
     */

    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting {} to account: {}", amount,accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                ()->  new AccountNotFoundException("Account not found with account number : " + accountNumber)
        );
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Balance Credited. New Balance: {} for {} account",account.getBalance(),account.getAccountNumber());

    }

    public String getEmail(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                ()->  new AccountNotFoundException("Account not found with account number : " + accountNumber)
        );
        return  account.getEmail();
    }
}
