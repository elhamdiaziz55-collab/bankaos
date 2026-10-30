package com.bankaos.transactionservice.service;

import com.bankaos.transactionservice.enums.TransactionStatus;
import com.bankaos.transactionservice.exception.TransactionNotFoundException;
import com.bankaos.transactionservice.model.Transaction;
import com.bankaos.transactionservice.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class Consumer {
    private static final Logger log = LoggerFactory.getLogger(Consumer.class);
    private final TransactionRepository transactionRepository;
    private static SecureRandom secureRandom = new SecureRandom();
    private final RedisTemplate<String,String> redisTemplate;
    private static final long OTP_EXPIRY_MINUTE = 2;
    private final KafkaTemplate<String,Map<String,Object>> kafkaTemplate;
    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";
    private final TransactionService transactionService;

    public Consumer(TransactionRepository transactionRepository, RedisTemplate<String, String> redisTemplate, KafkaTemplate<String, Map<String, Object>> kafkaTemplate, TransactionService transactionService) {
        this.transactionRepository = transactionRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionService = transactionService;
    }

    /**
     * Consume verification.required event
     * Generate OTP and ask user to verify
     * @param payload
     */
    @KafkaListener(topics = "verification.required")
    public void consumeVerificationRequired(
            @Payload Map<String,Object> payload
            ){
        try{
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");
            log.info("Verification required - transaction: {} reason: {}",transactionId,reason);
            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new TransactionNotFoundException("Transaction not found " + transactionId));
            if(transaction.getTransactionStatus() != TransactionStatus.PROCESSING){
                log.warn("Transaction {} not PROCESSING - skipping",transactionId);
                return ;
            }

            //Generate 6 digit otp
            long randomNumber = secureRandom.nextLong(1000000);
            String otp = String.format("%06d",randomNumber);

            //Store OTP in Redis - expires in 5 minutes
            String otpKey = "verification:otp" + transactionId;
            redisTemplate.opsForValue().set(otpKey,otp,OTP_EXPIRY_MINUTE, TimeUnit.MINUTES);

            //Update Status
            transaction.setTransactionStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);
            log.info("OTP generated for transaction: {} expires in {} min",transactionId,OTP_EXPIRY_MINUTE);
            //Notify user
            Map<String,Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId",transactionId);
            otpEvent.put("accountNumber",accountNumber);
            otpEvent.put("reason",reason);
            otpEvent.put("otp",otp);
            otpEvent.put("amount",payload.get("amount"));
            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC,transactionId,otpEvent);
        }catch(Exception e) {
            log.error("Error handline verification required event : {}", e.getMessage());
        }
    }


    @KafkaListener(topics = "fraud.check.clean")
    public void consumeFraudCheckCleanResultEvent(
            @Payload Map<String,Object> payload
    ){
        try{
            String transactionId = (String) payload.get("transactionId");
            transactionService.processCleanResult(transactionId);
        }catch (Exception e){
          log.error("Error processing fraud check result: {}",e.getMessage());
        }
    }
}
