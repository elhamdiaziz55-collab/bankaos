package com.bankaos.accountservice.controller;

import com.bankaos.accountservice.dto.CreateAccountRequest;
import com.bankaos.accountservice.dto.CreateAccountResponse;
import com.bankaos.accountservice.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<CreateAccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request
            ){
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }
    @GetMapping("/{accountNumber}")
    public ResponseEntity<CreateAccountResponse> getAccount(@PathVariable String accountNumber){
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String accountNumber){
        return ResponseEntity.ok(accountService.getBalance(accountNumber));
    }

    @PutMapping("/{accountNumber}")
    public ResponseEntity<String> blockAccount(@PathVariable String accountNumber){
        accountService.blockAccount(accountNumber);
        return ResponseEntity.ok("Account blocked successfully");
    }

    /**
     * SAGA STEP1 - Deduct Balance
     * Called by Transaction when Transfer is initiated
     */
    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<String> deductBalance(
            @PathVariable String accountNumber,
            @RequestParam @Positive(message = "Amount must be positive") BigDecimal amount
    ){
        accountService.deductBalance(accountNumber,amount);
        return ResponseEntity.ok("Account deducted Successfully");
    }

    /**
     * SAGA STEP4 - Compensating transaction endpoint
     * Called by Transaction SERVICE in TWO SCENARIOS
     * 1. Fraud detected -> refund sender (undo step 1)
     * 2. Transaction completed -> Credit receiver
     */

    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<String> creditBalance(
            @PathVariable String accountNumber,
            @Valid @Positive(message = "Amount must be positive") @RequestParam  BigDecimal amount
    ){
        accountService.creditBalance(accountNumber,amount);
        return ResponseEntity.ok("Balance Credited SUCCESSFULLY");
    }

    @GetMapping("/email/{accountNumber}")
    public ResponseEntity<String> getEmail(@PathVariable String accountNumber){
        return ResponseEntity.ok(accountService.getEmail(accountNumber));
    }
}
