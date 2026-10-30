package com.bankaos.payementservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {
    private String paymentId;
    private String paymentRef;
    private String payUrl;
    private BigDecimal amount;
    private String currency;
    private String status;
}