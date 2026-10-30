package com.bankaos.transactionservice.service;

import com.bankaos.transactionservice.client.AccountServiceClient;
import com.bankaos.transactionservice.dto.TransactionResponse;
import com.bankaos.transactionservice.dto.TransferRequest;
import com.bankaos.transactionservice.enums.TransactionStatus;
import com.bankaos.transactionservice.enums.TransactionType;
import com.bankaos.transactionservice.event.TransactionCompletedEvent;
import com.bankaos.transactionservice.event.TransactionInitiatedEvent;
import com.bankaos.transactionservice.exception.TransactionNotFoundException;
import com.bankaos.transactionservice.model.Transaction;
import com.bankaos.transactionservice.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    public final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";
    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final RedisTemplate<String,String> redisTemplate;

    public TransactionService(TransactionRepository transactionRepository, AccountServiceClient accountServiceClient, KafkaTemplate<String, Object> kafkaTemplate, RedisTemplate<String, String> redisTemplate, KafkaTemplate<String, Map<String, Object>> kafkaTemplate1) {
        this.transactionRepository = transactionRepository;
        this.accountServiceClient = accountServiceClient;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * SAGA STEP -1: Initiate transfer
     * Deducts from sender via feign
     * Saves transaction as PROCESSING
     * Publish event to Kafka for fraud check
     * Returns
     * @param transferRequest
     * @return
     */
    public TransactionResponse transfer(TransferRequest transferRequest) {
        log.info("SAGA START - Transfer: {} -> {} amount: {}"
                , transferRequest.getSenderAccountNumber()
                ,transferRequest.getReceiverAccountNumber()
                ,transferRequest.getAmount());
        BigDecimal originalBalance = accountServiceClient.getBalance(transferRequest.getSenderAccountNumber());
        if(originalBalance == null){
            log.error("original balance is null , in the first step because of accountServiceClient request ");
        }
        accountServiceClient.deductBalance(
                transferRequest.getSenderAccountNumber(),
                transferRequest.getAmount()
        );

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(transferRequest.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(transferRequest.getReceiverAccountNumber());
        transaction.setAmount(transferRequest.getAmount());
        transaction.setDescription(transferRequest.getDescription());
        transaction.setTransactionStatus(TransactionStatus.PROCESSING);
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setReferenceNumber(UUID.randomUUID().toString());
        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Transaction saved as PROCESSING: {}",savedTransaction.getId());

        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription(),
                originalBalance
        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,transaction.getId(),event);
        log.info("TransactionInitiatedEvent published : {}",savedTransaction.getId());
        return mapToResponse(savedTransaction);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse(
        transaction.getId(),
        transaction.getSenderAccountNumber(),
        transaction.getReceiverAccountNumber(),
        transaction.getAmount(),
        transaction.getTransactionType(),
        transaction.getTransactionStatus(),
        transaction.getDescription(),
        transaction.getFailureReason(),
        transaction.getReferenceNumber(),
        transaction.getCreatedAt(),
        transaction.getCompletedAt()
        );

        return response;
    }


    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository.findById(transactionId).orElseThrow(
                () -> new TransactionNotFoundException("Transaction not found:" + transactionId)
        ));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        List<Transaction> transactions = transactionRepository
                .findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber);
        return transactions.stream()
                .map(this::mapToResponse).toList();
    }

    public TransactionResponse verifyOTP(String transactionId, String otp) {
        log.info("OTP verification for the transaction: {}",transactionId);
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(
                () -> new TransactionNotFoundException("Transaction not found:" + transactionId)
        );
        String otpKey = "verification:otp" + transactionId;
        String storedOTP = redisTemplate.opsForValue().get(otpKey);
        if(storedOTP == null){
            //OTP EXPIRED
            log.warn("OTP expired for transaction: {}", transactionId);
            compensateTransaction(transaction,"OTP expired - transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }

        if (!storedOTP.equals(otp)){
            //Block Account AND REFUND
            log.warn("Wrong OTP - blocking account and refunding: {}",transactionId);
            redisTemplate.delete(otpKey);
            BlockAccountAndCompensate(transaction,
                    "Wrong OTP entered - transaction cancelled, account blocked for security");
            return mapToResponse(transaction);
        }

        // OTP correct - complete transaction
        log.info("OTP verified - completing transaction: {}",transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return mapToResponse(transaction);
    }

    private void completeTransaction(Transaction transaction) {
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getAmount(),
                transaction.getDescription()
        );
        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC,transaction.getId(),completedEvent);
        log.info("SAGA COMPLETE - Transaction {} completed",transaction.getId());
    }

    private void BlockAccountAndCompensate(Transaction transaction, String reason) {
        // Publish fraud.detected -> Account service will block account
        Map<String,Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId",transaction.getId());
        fraudEvent.put("accountNumber",transaction.getSenderAccountNumber());
        fraudEvent.put("reason",reason);
        kafkaTemplate.send(FRAUD_DETECTED_TOPIC,transaction.getId(),fraudEvent);
        log.warn("fraud.detected event published - account : {} will be blocked ,Kindly contact to the bank",transaction.getSenderAccountNumber());

        //SAGA COMPENSATION - refund Sender
        compensateTransaction(transaction,reason);

    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA COMPENSATION - refunding: {} amount: {}",
                transaction.getSenderAccountNumber(),
                transaction.getAmount()
                );
        // CREDIT MONEY BACK TO SENDER SYNCHRONOUSLY
        accountServiceClient.creditBalance(
                transaction.getSenderAccountNumber(),
                transaction.getAmount()
                );
        transaction.setTransactionStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);
        //Publish refund event - Notification service will alert user
        Map<String,Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId",transaction.getId());
        refundEvent.put("senderAccountNumber",transaction.getSenderAccountNumber());
        refundEvent.put("amount",transaction.getAmount());
        refundEvent.put("reason",reason);
        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC,transaction.getId(),refundEvent);
        log.info("SAGA COMPENSATION COMPLETE - {} refunded to {}",transaction.getAmount(),transaction.getSenderAccountNumber());
    }

    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(
                () -> new TransactionNotFoundException("Transaction not found:" + transactionId)
        );

        if(transaction.getTransactionStatus() != TransactionStatus.PROCESSING){
            log.warn("Transaction {} not PROCESSING - skipping",transactionId);
            return ;
        }

        completeTransaction(transaction);
    }
}
