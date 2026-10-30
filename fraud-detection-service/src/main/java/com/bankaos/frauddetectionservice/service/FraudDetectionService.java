package com.bankaos.frauddetectionservice.service;

import com.bankaos.frauddetectionservice.event.TransactionInitiatedEvent;
import com.bankaos.frauddetectionservice.model.FraudCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class FraudDetectionService {
    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);
    private static final String VERIFICATION_REQUIRED_TOPIC = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT  = "fraud.check.clean";
    private final KafkaTemplate<String,Map<String,Object>> kafkaTemplate;
    private final RedisTemplate<String,String> redisTemplate;
    @Value("${fraud.max-transactions-per-minute}")
    private int maxTransactionsPerMinute;
    @Value("${fraud.suspicious-amount-multiplier}")
    private double suspiciousAmountMultiplier;
    @Value("${fraud.max-balance-percentage}")
    private double maxBalancePercentage;

    public FraudDetectionService(KafkaTemplate<String, Map<String, Object>> kafkaTemplate, RedisTemplate<String, String> redisTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
    }

    public void checkTransaction(TransactionInitiatedEvent event) {
        String transactionId = event.getTransactionId();
        String senderAccountNumber = event.getSenderAccountNumber();
        BigDecimal amount = event.getAmount();
        BigDecimal senderBalance = event.getOriginalBalance();
        log.info("Checking transaction: {} account: {} amount: {} balance: {}",transactionId,senderAccountNumber,amount,senderBalance);

        FraudCheckResult result = performFraudChecks(senderAccountNumber,amount,senderBalance);
        if(result.isFraud()){
            log.info("Suspicious activity detected - account: {}"
                    + "reason: {} - requesting OTP verification",senderAccountNumber,result.getReason());
            Map<String,Object> verificationEvent = new HashMap<>();
            verificationEvent.put("transactionId",transactionId);
            verificationEvent.put("accountNumber",senderAccountNumber);
            verificationEvent.put("amount",amount);
            verificationEvent.put("reason",result.getReason());
            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC,transactionId,verificationEvent);
        }else{
            // Transaction is clean
            log.info("TransactionCleanEvent");
            Map<String,Object> transactionCleanEvent = new HashMap<>();
            transactionCleanEvent.put("transactionId",transactionId);
            transactionCleanEvent.put("isFraud",false);
            transactionCleanEvent.put("reason",null);
            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT,transactionId,transactionCleanEvent);
        }

    }

    private FraudCheckResult performFraudChecks(String accountNumber, BigDecimal amount, BigDecimal senderBalance) {
                // Pattern 1 : Velocity Check
                if(isVelocityExceeded(accountNumber)){
                    return new FraudCheckResult(true,"Too many transactions in 60 seconds - Velocity Limit exceeded");
                }

                // Pattern 2 : Amount check
                if(isAmountSuspicious(accountNumber,amount)){
                    return new FraudCheckResult(true,"Unusual transaction amount - exceed s" + suspiciousAmountMultiplier + "x your average");
                }

                // Pattern 3 : Balance Check
                if(senderBalance.compareTo(BigDecimal.ZERO) > 0 && isBalanceCheckFailed(senderBalance,amount)){
                    return new FraudCheckResult(true,"Transaction exceed "  + (maxBalancePercentage * 100) + "% of account balance");

                }

                return new FraudCheckResult(false,null);


    }

    private boolean isBalanceCheckFailed(BigDecimal senderBalance, BigDecimal amount) {
        BigDecimal maxAllowed = senderBalance.multiply(
                BigDecimal.valueOf(maxBalancePercentage)
        );
        log.info("Balance check - amount: {} maxAllowed: {} suspicious: {}" ,amount,maxAllowed,amount.compareTo(maxAllowed) > 0);
        return amount.compareTo(maxAllowed) > 0;
    }

    private boolean isAmountSuspicious(String accountNumber, BigDecimal amount) {
        String key = "fraud:account" + accountNumber;
        Object avgObj = redisTemplate.opsForHash().get(key,"average");
        Object countObj = redisTemplate.opsForHash().get(key,"count");
        if(avgObj == null || countObj == null){
            redisTemplate.opsForHash().put(key,"average",amount.toString());
            redisTemplate.opsForHash().put(key,"count","1");
            return false;
        }

        BigDecimal avgAmount = new BigDecimal(avgObj.toString());
        long count = Long.parseLong(countObj.toString());
        BigDecimal threshold = avgAmount.multiply(BigDecimal.valueOf(suspiciousAmountMultiplier));

        //Update running average
        BigDecimal newAvg = avgAmount.multiply(BigDecimal.valueOf(count))
                .add(amount)
                .divide(BigDecimal.valueOf(count + 1),2,RoundingMode.HALF_UP);
        redisTemplate.opsForHash().put(key,"average",newAvg.toString());
        redisTemplate.opsForHash().put(key,"count",String.valueOf(count + 1));
        log.info("Amount check - amount: {} threshold: {} suspicious: {}",amount , threshold, amount.compareTo(threshold) > 0);

        return amount.compareTo(threshold) > 0 ;
    }

    private boolean isVelocityExceeded(String accountNumber) {
        String key = "fraud:velocity" + accountNumber;
        Long  count = redisTemplate.opsForValue().increment(key);
        if(count != null && count == 1){
            redisTemplate.expire(key,60, TimeUnit.SECONDS); //Delete this Redis key after 60 seconds.
        }
        log.info("Velocity check - account: {} count: {}/{}",
                accountNumber,count,maxTransactionsPerMinute);
        return count != null && count > maxTransactionsPerMinute;

    }


}
