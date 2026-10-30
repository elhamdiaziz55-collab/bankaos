package com.bankaos.transactionservice.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "account-service",url = "${account.service.url}")
public interface AccountServiceClient {
    @PutMapping("/api/v1/accounts/{accountNumber}/deduct")
    ResponseEntity<String> deductBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount
    );


    @GetMapping("/api/v1/accounts/{accountNumber}/balance")
    BigDecimal getBalance(@PathVariable String accountNumber);

    @PutMapping("/api/v1/accounts/{accountNumber}/credit")
    public String creditBalance(
            @PathVariable String accountNumber,
            @RequestParam  BigDecimal amount
    );
}
