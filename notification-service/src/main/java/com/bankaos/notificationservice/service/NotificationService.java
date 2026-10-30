package com.bankaos.notificationservice.service;

import com.bankaos.notificationservice.client.AccountServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final JavaMailSender mailSender;
    @Value("${mail.username}")
    private String bankosEmailSender;

    private final AccountServiceClient accountServiceClient;
    public NotificationService(JavaMailSender mailSender, AccountServiceClient accountServiceClient) {
        this.mailSender = mailSender;
        this.accountServiceClient = accountServiceClient;
    }

    @KafkaListener(topics = "transaction.otp.generated")
    public void consumeOtpGeneratedEvent(
            @Payload Map<String,Object> payload
            ){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            String otp = (String) payload.get("otp");
            String transactionId = (String) payload.get("transactionId");
            String amount =  payload.get("amount").toString();
            String reason = (String) payload.get("reason");

            sendAlert(accountNumber,"TRANSACTION VERIFICATION REQUIRED"
                    , String.format("""
                                Suspicious activity detected on your account.
                                Reason: %s
                                A transaction of %s is pending verification.
                                Your OTP is : %s. Valid for 5 minutes.
                                If this wasn't you - ignore this message.
                            """,reason,amount,otp));
        }catch (Exception e){
            log.error("Error sending OTP notification: {}", e.getMessage());
        }
    }
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompletedEvent(
            @Payload Map<String,Object> payload
    ){
        try{
            String senderAccount = (String) payload.get("senderAccountNumber");
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            String amount =  payload.get("amount").toString();
            sendAlert(
                  senderAccount,
                  "DEBIT ALERT",
                    String.format("""
                            %s debited from account %s
                            """,amount,senderAccount)
            );
            sendAlert(
                    receiverAccount,
                    "CREDIT ALERT",
                    String.format("""
                            %s credited to account %s
                            """,amount,receiverAccount)
            );

        } catch (Exception e) {
            log.error("Error sending transaction notification: {}",e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetectEvent(
            @Payload Map<String ,Object> payload
    ){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");
            sendAlert(
                    accountNumber,
                    "SUSPICIOUS ACTIVITY DETECTED",
                    String.format("""
                            Your account %s has been blocked.
                            Reason: %s.
                            Please contact your bank immediately.
                            """,accountNumber,reason)
            );
        } catch (Exception e) {
            log.error("Error sending fraud alert: {}",e.getMessage());
        }
    }

    @KafkaListener(topics = "transaction.refunded")
    public void consumeTransactionRefunded(
            @Payload Map<String,Object> payload
    ){
        try {
            String accountNumber = (String) payload.get("senderAccountNumber");
            String amount = payload.get("amount").toString();
            String reason = (String) payload.get("reason");

            sendAlert(
                    accountNumber,
                    "REFUND PROCESSED",
                    String.format("""
                            Your transaction of %s was cancelled.
                            Reason: %s
                            %s has been refunded to account %s.
                            """,amount,reason,amount,accountNumber)
            );
        } catch (Exception e) {
            log.error("Error sending refund notification : {}",e.getMessage());
        }
    }
    @KafkaListener(topics = "payment.event")
    public void consumePaymentEvents(
            @Payload Map<String,Object> payload
    ){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            String amount = payload.get("amount").toString();
            String eventType = (String) payload.get("eventType");
            if(eventType.equals("PAYMENT_COMPLETED")){
                sendAlert(
                        accountNumber,
                        "PAYMENT SUCCESSFUL",
                        String.format("""
                            Payment of %s completed.
                            paymentRef: %s
                            """,amount,payload.get("PaymentRef"))
                );
            }else if(eventType.equals("PAYMENT_FAILED")){
                sendAlert(
                        accountNumber,
                        "PAYMENT FAILED",
                        String.format("""
                            Your Payment of %s failed.
                            PaymentRef: %s
                            FailUrl : %s
                            Pleas try again or contact support.
                            """,amount,payload.get("PaymentRef"),payload.get("failUrl"))
                );
            }
        } catch (Exception e) {
            log.error("Error sending payment notification : {}",e.getMessage());
        }
    }
    private void sendAlert(String accountNumber ,String subject, String message) {
//        log.info("-------------------NOTIFICATION--------------------");
//        log.info("Account: {}",accountNumber);
//        log.info("Subject: {}",subject);
//        log.info("Message: {}",message);

        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(bankosEmailSender);
        String receiverEmailAddress = accountServiceClient.getEmail(accountNumber);
        email.setTo(receiverEmailAddress);
        email.setSubject(subject);
        email.setText(message);
        mailSender.send(email);
        log.info("Email sent successfully");
    }
}
