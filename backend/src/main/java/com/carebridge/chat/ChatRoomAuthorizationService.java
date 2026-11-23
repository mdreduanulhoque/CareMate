package com.carebridge.chat;

import com.carebridge.booking.ContactUnlockRepository;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.security.CareBridgeUserPrincipal;
import com.carebridge.user.Role;
import com.carebridge.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single source of truth for chat room membership & unlock authorization across both REST and STOMP.
 */
@Service
public class ChatRoomAuthorizationService {

    private final ContactUnlockRepository contactUnlockRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final UserRepository userRepository;

    public ChatRoomAuthorizationService(
            ContactUnlockRepository contactUnlockRepository,
            CaretakerProfileRepository caretakerProfileRepository,
            UserRepository userRepository
    ) {
        this.contactUnlockRepository = contactUnlockRepository;
        this.caretakerProfileRepository = caretakerProfileRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public NegotiationRoomId.Participants authorizeNegotiationRoom(
            CareBridgeUserPrincipal principal,
            String rawRoomId
    ) {
        requireActivePrincipal(principal);

        NegotiationRoomId.Participants participants = NegotiationRoomId.tryParse(rawRoomId)
                .orElseThrow(() -> new AccessDeniedException("Invalid or unauthorized negotiation room."));

        Long actingUserId = principal.getUserId();
        boolean isCustomerParticipant = actingUserId.equals(participants.customerId())
                && principal.getRole() == Role.CUSTOMER;
        boolean isApprovedCaretakerParticipant = actingUserId.equals(participants.caretakerId())
                && principal.getRole() == Role.CARETAKER
                && caretakerProfileRepository.existsByUserId(actingUserId);

        if (!isCustomerParticipant && !isApprovedCaretakerParticipant) {
            throw new AccessDeniedException("You do not have permission to access this negotiation room.");
        }

        boolean unlocked = contactUnlockRepository.existsByCustomerIdAndCaretakerId(
                participants.customerId(),
                participants.caretakerId()
        );
        if (!unlocked) {
            throw new AccessDeniedException("Contact must be unlocked before accessing this negotiation room.");
        }

        return participants;
    }

    @Transactional(readOnly = true)
    public void authorizeDisputeRoom(CareBridgeUserPrincipal principal, String rawContractId) {
        requireActivePrincipal(principal);
        // Dispute contracts are activated in Phase 7; deny all dispute room access until an active dispute exists.
        throw new AccessDeniedException("This dispute mediation room is not active or accessible.");
    }

    @Transactional(readOnly = true)
    public void requireActivePrincipal(CareBridgeUserPrincipal principal) {
        if (principal == null || !principal.isEnabled()
                || !userRepository.existsByIdAndActiveTrue(principal.getUserId())) {
            throw new AccessDeniedException("Authenticated active user session is required.");
        }
    }
}
