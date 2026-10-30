package com.bankaos.payementservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KonnectInitPaymentResponse {
    private String payUrl;
    private String paymentRef;
}
