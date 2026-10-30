package com.bankaos.payementservice.controller;


import com.bankaos.payementservice.dto.CreatePaymentRequest;
import com.bankaos.payementservice.dto.PaymentOrderResponse;
import com.bankaos.payementservice.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-order")
    public ResponseEntity<PaymentOrderResponse> createPaymentOrder(
            @Valid @RequestBody CreatePaymentRequest request ){
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPaymentOrder(request));
    }

    @GetMapping("/webhook")
    public ResponseEntity<Void> paymentWebhook(
            @RequestParam("payment_ref") String paymentRef
    ) {
        if (paymentRef == null || paymentRef.isBlank()) {
            log.warn("Received Konnect webhook without payment_ref");

            return ResponseEntity.badRequest().build();
        }

        paymentService.handleWebhook(paymentRef);
        return ResponseEntity.ok().build();
    }
}
