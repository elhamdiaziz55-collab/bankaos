package com.bankaos.payementservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KonnectPaymentDetails {

    private String id;

    private String status;

    private Long amountDue;

    private Long reachedAmount;

    private Long amount;

    private String token;

    private String orderId;

    private String type;

    private String expirationDate;

    private String failUrl;
}