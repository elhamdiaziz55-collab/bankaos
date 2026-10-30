package com.bankaos.payementservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KonnectInitPaymentRequest {

    private String receiverWalletId;
    private String token;
    private Long amount;
    private String type;
    private String description;
    private List<String> acceptedPaymentMethods;
    private Integer lifespan;
    private Boolean checkoutForm;
    private Boolean addPaymentFeesToAmount;
    private String orderId;
    private String webhook;
    private String theme;
}
