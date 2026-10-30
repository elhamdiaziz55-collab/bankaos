package com.bankaos.payementservice.repository;

import com.bankaos.payementservice.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByKonnectPaymentRef(String konnectPaymentRef);
}