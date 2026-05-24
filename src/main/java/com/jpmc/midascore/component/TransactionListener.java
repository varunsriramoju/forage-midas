package com.jpmc.midascore.component;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRepository;

@Component
public class TransactionListener {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @Autowired
    private IncentiveService incentiveService;   // ← NEW

    @KafkaListener(topics = "${general.kafka-topic}", groupId = "midas-core-group")
    public void listen(Transaction transaction) {

        // 1. Find sender and recipient
        UserRecord sender = userRepository.findById(transaction.getSenderId());
        UserRecord recipient = userRepository.findById(transaction.getRecipientId());

        // 2. Validate users exist
        if (sender == null || recipient == null) {
            System.out.println("Invalid users, skipping.");
            return;
        }

        // 3. Validate sender balance
        if (sender.getBalance() < transaction.getAmount()) {
            System.out.println("Insufficient balance, skipping.");
            return;
        }

        // 4. Call incentive API AFTER validation
        float incentiveAmount = incentiveService.getIncentive(transaction);
        System.out.println("Incentive received: " + incentiveAmount);

        // 5. Update balances
        // Sender loses only the transaction amount (NOT the incentive)
        sender.setBalance(sender.getBalance() - transaction.getAmount());
        // Recipient gains transaction amount + incentive bonus
        recipient.setBalance(recipient.getBalance() + transaction.getAmount() + incentiveAmount);

        // 6. Save updated users
        userRepository.save(sender);
        userRepository.save(recipient);

        // 7. Save transaction record with incentive
        TransactionRecord record = new TransactionRecord(
                sender, recipient, transaction.getAmount(), incentiveAmount
        );
        transactionRecordRepository.save(record);

        // 8. Print all balances to find wilbur
        userRepository.findAll().forEach(user
                -> System.out.println("User: " + user.getName() + " | Balance: " + user.getBalance())
        );
    }
}
