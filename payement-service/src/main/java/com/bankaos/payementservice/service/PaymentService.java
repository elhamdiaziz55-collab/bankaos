package com.bankaos.payementservice.service;

import com.bankaos.payementservice.dto.*;
import com.bankaos.payementservice.enums.PaymentStatus;
import com.bankaos.payementservice.model.Payment;
import com.bankaos.payementservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository paymentRepository;
    private static final String PAYMENT_STATUS_TOPIC = "payment.event";
    private final RestClient restClient;
    private final KafkaTemplate<String,Object> kafkaTemplate;
    @Value("${konnect.wallet-id}")
    private String walletId;
    @Value("${konnect.api-key}")
    private String apiKey;
    @Value("${konnect.webhook-url}")
    private String webhookUrl;



    public PaymentService(PaymentRepository paymentRepository, RestClient restClient, KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.restClient = restClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Create Order in Konnect
     * Save Payment record in DB
     * Return Order details to frontend
     * User redirected to Konnect page to pay
     * User pays
     * Kconnect calls webhook
     * @param request
     * @return
     */
    public PaymentOrderResponse createPaymentOrder(CreatePaymentRequest request) {
        log.info("Creating payment order for account: {} amount : {}",request.getAccountNumber(),request.getAmount());

        Payment payment = new Payment();
        payment.setAccountNumber(request.getAccountNumber());
        payment.setAmount(request.getAmount());
        payment.setCurrency("TND");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setOrderId(request.getOrderId());
        payment.setDescription(request.getDescription());
        Payment savedPayment = paymentRepository.save(payment);
        //Convert TND → Millimes

        long amountInMillimes = request.getAmount()
                .multiply(BigDecimal.valueOf(1000))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();


        // Create request for Konnect

        KonnectInitPaymentRequest konnectRequest =
                new KonnectInitPaymentRequest(
                        walletId,
                        "TND",
                        amountInMillimes,
                        "immediate",
                        savedPayment.getDescription(),
                        List.of("wallet", "bank_card", "e-DINAR"),
                        10,
                        true,
                        false,
                        savedPayment.getOrderId(),
                        webhookUrl,
                        "light"
                );
        KonnectInitPaymentResponse response = restClient.post()
                .uri("api/v2/payments/init-payment")
                .header("x-api-key",apiKey)
                .body(konnectRequest)
                .retrieve()
                .body(KonnectInitPaymentResponse.class);

        savedPayment.setPayUrl(response.getPayUrl());
        savedPayment.setKonnectPaymentRef(response.getPaymentRef());
        paymentRepository.save(savedPayment);
        return new PaymentOrderResponse(
                savedPayment.getId(),
                savedPayment.getKonnectPaymentRef(),
                savedPayment.getPayUrl(),
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                savedPayment.getStatus().toString()
        );
    }

    public void handleWebhook(String paymentRef) {
        log.info("Handling webhook event for: {}", paymentRef);

        Payment payment = paymentRepository
                .findByKonnectPaymentRef(paymentRef)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentRef));

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("Payment already completed. Ignoring duplicate webhook. paymentId={}",
                    payment.getId());
            return;
        }

        KonnectPaymentDetailsResponse response = restClient.get()
                .uri("https://api.sandbox.konnect.network/api/v2/payments/{paymentId}", paymentRef)
                .header("x-api-key", apiKey)
                .retrieve()
                .body(KonnectPaymentDetailsResponse.class);

        if (response == null || response.getPayment() == null) {
            log.error("Invalid response from Konnect. paymentRef={}", paymentRef);
            throw new RuntimeException("Invalid response from Konnect");
        }

        KonnectPaymentDetails paymentDetails = response.getPayment();
        String status = paymentDetails.getStatus().toLowerCase();

        switch (status) {
            case "completed":
                handleCompletedPayment(payment);
                break;
            case "pending":
                handlePendingPayment(payment);
                break;
            case "failed":
                handleFailedPayment(payment, paymentDetails);
                break;
            case "cancelled":
                handleCancelledPayment(payment);
                break;
            case "expired" :
                handleExpiredPayment(payment);
                break;
            default:
                log.warn("Unknown Konnect payment status: {} for paymentId={}",
                        status, payment.getId());
                throw new RuntimeException("Unknown payment status: " + status);
        }
    }

    private void handleCompletedPayment(Payment payment) {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        publishPaymentEvent(payment, "PAYMENT_COMPLETED");
        log.info("Payment completed successfully. paymentId={}", payment.getId());
    }

    private void handlePendingPayment(Payment payment) {
            payment.setStatus(PaymentStatus.PENDING);
            log.info("Payment still pending. paymentId={}", payment.getId());
            paymentRepository.save(payment);
    }

    private void handleExpiredPayment(Payment payment) {
            payment.setStatus(PaymentStatus.EXPIRED);
            publishPaymentEvent(payment, "PAYMENT_EXPIRED");
            log.warn("Payment expired. paymentId={}", payment.getId());
            paymentRepository.save(payment);
    }

    private void handleFailedPayment(Payment payment, KonnectPaymentDetails details) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailUrl(details.getFailUrl()); // Store error details
        paymentRepository.save(payment);

        publishPaymentEvent(payment, "PAYMENT_FAILED");
        log.error("Payment failed. paymentId={}, reason={}",
                payment.getId(), details.getFailUrl());
    }
    private void handleCancelledPayment(Payment payment) {
        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);
        publishPaymentEvent(payment, "PAYMENT_CANCELLED");
        log.info("Payment cancelled by user. paymentId={}", payment.getId());
    }
    private void publishPaymentEvent(Payment payment, String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("paymentId", payment.getId());
        event.put("accountNumber", payment.getAccountNumber());
        event.put("amount", payment.getAmount());
        event.put("failUrl",payment.getFailUrl());
        event.put("paymentRef", payment.getKonnectPaymentRef());
        event.put("eventType", eventType);

        try {
        kafkaTemplate.send(PAYMENT_STATUS_TOPIC, payment.getId(), event);
            log.info("{} event published: {}", eventType, payment.getId());
        } catch (Exception e) {
            log.error("Error publishing event: {}", e.getMessage());
        }
    }

}
