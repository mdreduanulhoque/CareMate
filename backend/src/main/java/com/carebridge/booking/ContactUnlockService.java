package com.carebridge.booking;

import com.carebridge.booking.dto.ContactUnlockResponse;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.chat.NegotiationRoomId;
import com.carebridge.common.ForbiddenException;
import com.carebridge.common.NotFoundException;
import com.carebridge.notification.RealtimeEventDispatcher;
import com.carebridge.notification.dto.UserNotificationEvent;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
public class ContactUnlockService {

    private final ContactUnlockRepository contactUnlockRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final UserRepository userRepository;
    private final RealtimeEventDispatcher realtimeEventDispatcher;
    private final TransactionTemplate transactionTemplate;

    public ContactUnlockService(
            ContactUnlockRepository contactUnlockRepository,
            CaretakerProfileRepository caretakerProfileRepository,
            UserRepository userRepository,
            RealtimeEventDispatcher realtimeEventDispatcher,
            TransactionTemplate transactionTemplate
    ) {
        this.contactUnlockRepository = contactUnlockRepository;
        this.caretakerProfileRepository = caretakerProfileRepository;
        this.userRepository = userRepository;
        this.realtimeEventDispatcher = realtimeEventDispatcher;
        this.transactionTemplate = transactionTemplate;
    }

    public ContactUnlockResponse unlockContact(Long customerUserId, Long caretakerUserId) {
        UnlockOutcome outcome;
        try {
            outcome = transactionTemplate.execute(status -> findOrCreateUnlock(customerUserId, caretakerUserId));
        } catch (DataIntegrityViolationException raceEx) {
            // Concurrent double-tap hit uq_contact_unlocks_pair; read the committed unlock cleanly.
            outcome = transactionTemplate.execute(status ->
                    new UnlockOutcome(loadExistingUnlockOrThrow(customerUserId, caretakerUserId), null, null)
            );
        }

        if (outcome == null || outcome.response() == null) {
            throw new IllegalStateException("Contact unlock transaction completed without a response.");
        }

        if (outcome.response().newlyUnlocked() && outcome.notificationTargetId() != null) {
            realtimeEventDispatcher.publishUserNotification(
                    outcome.notificationTargetId(),
                    outcome.notificationEvent()
            );
        }

        return outcome.response();
    }

    @Transactional(readOnly = true)
    public ContactUnlockResponse getUnlockedContact(Long customerUserId, Long caretakerUserId) {
        requireActiveCustomer(customerUserId);
        return loadExistingUnlockOrThrow(customerUserId, caretakerUserId);
    }

    private UnlockOutcome findOrCreateUnlock(Long customerUserId, Long caretakerUserId) {
        User customer = requireActiveCustomer(customerUserId);
        CaretakerProfile profile = caretakerProfileRepository.findActiveByUserIdWithUser(caretakerUserId)
                .orElseThrow(() -> new NotFoundException("Verified caretaker profile not found."));

        Optional<ContactUnlock> existing = contactUnlockRepository
                .findWithUsersByCustomerIdAndCaretakerId(customer.getId(), caretakerUserId);
        if (existing.isPresent()) {
            return new UnlockOutcome(
                    ContactUnlockResponse.from(existing.get(), profile, false),
                    null,
                    null
            );
        }

        ContactUnlock saved = contactUnlockRepository.saveAndFlush(new ContactUnlock(customer, profile.getUser()));
        ContactUnlockResponse response = ContactUnlockResponse.from(saved, profile, true);
        String roomId = NegotiationRoomId.format(customer.getId(), caretakerUserId);

        UserNotificationEvent notification = UserNotificationEvent.of(
                "CONTACT_UNLOCKED",
                roomId,
                "New Negotiation Inquiry",
                customer.getFullName() + " unlocked your contact to discuss a hospital care shift.",
                customer.getId(),
                customer.getFullName()
        );
        return new UnlockOutcome(response, caretakerUserId, notification);
    }

    private ContactUnlockResponse loadExistingUnlockOrThrow(Long customerUserId, Long caretakerUserId) {
        ContactUnlock unlock = contactUnlockRepository
                .findWithUsersByCustomerIdAndCaretakerId(customerUserId, caretakerUserId)
                .orElseThrow(() -> new NotFoundException("Contact has not been unlocked for this caretaker yet."));
        CaretakerProfile profile = caretakerProfileRepository.findActiveByUserIdWithUser(caretakerUserId)
                .orElseThrow(() -> new NotFoundException("Verified caretaker profile not found."));
        return ContactUnlockResponse.from(unlock, profile, false);
    }

    private User requireActiveCustomer(Long customerUserId) {
        User user = userRepository.findById(customerUserId)
                .orElseThrow(() -> new NotFoundException("Customer account not found."));
        if (!user.isActive() || user.getRole() != Role.CUSTOMER) {
            throw new ForbiddenException("Only active relative (customer) accounts can unlock caretaker contacts.");
        }
        return user;
    }

    private record UnlockOutcome(
            ContactUnlockResponse response,
            Long notificationTargetId,
            UserNotificationEvent notificationEvent
    ) {
    }
}
