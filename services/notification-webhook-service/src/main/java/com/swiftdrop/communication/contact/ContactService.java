package com.swiftdrop.communication.contact;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final ContactRepository contactRepository;
    private final Clock clock;

    public ContactService(ContactRepository contactRepository, Clock clock) {
        this.contactRepository = contactRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Contact syncContact(UserEventData userData, Instant occurredAt) {
        UUID userId = userData.getUserId();
        Optional<Contact> existingOpt = contactRepository.findById(userId);

        if (existingOpt.isPresent()) {
            Contact existing = existingOpt.get();
            if (occurredAt.isBefore(existing.getLastEventOccurredAt())) {
                log.info("Ignoring out-of-order event for user ID: {}", userId);
                return existing;
            }
            existing.setEmail(userData.getEmail());
            existing.setFullName(userData.getFullName());
            existing.setPhoneNumber(userData.getPhoneNumber());
            existing.setStatus(userData.getStatus());
            existing.setLastEventOccurredAt(occurredAt);
            existing.setUpdatedAt(clock.instant());
            return contactRepository.save(existing);
        } else {
            Instant now = clock.instant();
            Contact contact = new Contact(
                    userId,
                    userData.getEmail(),
                    userData.getFullName(),
                    userData.getPhoneNumber(),
                    userData.getStatus(),
                    occurredAt,
                    now,
                    now
            );
            return contactRepository.save(contact);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Contact> findContact(UUID userId) {
        return contactRepository.findById(userId);
    }
}
