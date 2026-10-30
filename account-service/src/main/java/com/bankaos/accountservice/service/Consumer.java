package com.bankaos.accountservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class Consumer {
    private final AccountService accountService;

    private static final Logger log = LoggerFactory.getLogger(Consumer.class);

    public Consumer(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Consume transaction. Completed event from Kafka
     *Credits receiver account
     * @param payload
     */
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompletedEvent(
            @Payload Map<String,Object> payload
            ){
        try{
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());
            log.info("Crediting account: {} amount: {}",receiverAccount,amount);
            accountService.creditBalance(receiverAccount,amount);
        }catch (Exception e){
            log.error("Error crediting account: {}",e.getMessage());
        }

    }

    /**
     * Consume fraud.detected event from Kafka
     * Blocks the flagged account
     * @param payload
     */
    @KafkaListener(topics="fraud.detected")
    public void consumeFraudDetectedEvent(
            @Payload Map<String,Object> payload
    ){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            log.info("Fraud detected - blocking account : {}",accountNumber);
            accountService.blockAccount(accountNumber);
        }catch (Exception e){
            log.error("Error blocking account: {}",e.getMessage());
        }

    }

}

//6. @Payload vs @Header
//
//This distinction is useful with Kafka.
//
//A Kafka message can have:
//
//Headers
//────────────────────
//eventType = TRANSACTION_COMPLETED
//        source    = transaction-service
//
//Payload
//────────────────────
//        {
//        "transactionId": "TX123",
//        "amount": 500,
//        "receiverAccountId": "ACC200"
//        }
//
//@Payload gets the body:
//
//@Payload Map<String, Object> payload
