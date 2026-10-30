package com.bankaos.payementservice.model;

import com.bankaos.payementservice.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false,precision = 15,scale = 2)
    private BigDecimal amount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String description;

    // ID returned by Konnect
    @Column(unique = true)
    private String konnectPaymentRef;

    // URL returned by Konnect
    private String payUrl;

    private String failUrl;

    @Column(nullable = false,unique = true)
    private String OrderId;
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime completedAt;
}