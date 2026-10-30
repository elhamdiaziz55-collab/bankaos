package com.bankaos.notificationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "account-service",url = "${account.service.url}")
public interface AccountServiceClient {
    @GetMapping("/api/v1/accounts/email/{accountNumber}")
    public String getEmail(@PathVariable String accountNumber);
}
