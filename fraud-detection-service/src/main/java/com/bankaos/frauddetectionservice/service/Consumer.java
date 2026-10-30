package com.bankaos.frauddetectionservice.service;

import com.bankaos.frauddetectionservice.event.TransactionInitiatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class Consumer {

    private static final Logger log = LoggerFactory.getLogger(Consumer.class);
    private final FraudDetectionService fraudDetectionService;

    public Consumer(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    /**
     * Listens to transaction.Initiated topic
     * Every transaction goes throw fraud check before completing
     * @param event
     */
    @KafkaListener(topics = "transaction.initiated",groupId = "fraud-detection-group")
    public void consumeTransactionInitiatedEvent(
            TransactionInitiatedEvent event
    ){
        log.info("Received transaction for fraud check: {}", event.getTransactionId());
        try{
            fraudDetectionService.checkTransaction(event);
        }catch (Exception e){
            log.error("exception occurred during processing in fraud detection service : {}",e.getMessage());
        }
    }

}
