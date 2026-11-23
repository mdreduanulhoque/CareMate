package com.carebridge.chat;

import com.carebridge.booking.ContactUnlockRepository;
import com.carebridge.booking.ContractStatus;
import com.carebridge.booking.HireContract;
import com.carebridge.booking.HireContractRepository;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.security.CareBridgeUserPrincipal;
import com.carebridge.user.Role;
import com.carebridge.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Single source of truth for chat room membership & unlock/dispute authorization across REST and STOMP.
 */
@Service
public class ChatRoomAuthorizationService {

    private static final Pattern POSITIVE_ID_PATTERN = Pattern.compile("^[1-9]\\d{0,18}$");

    private final ContactUnlockRepository contactUnlockRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final HireContractRepository hireContractRepository;
    private final UserRepository userRepository;

    public ChatRoomAuthorizationService(
            ContactUnlockRepository contactUnlockRepository,
            CaretakerProfileRepository caretakerProfileRepository,
            HireContractRepository hireContractRepository,
            UserRepository userRepository
    ) {
        this.contactUnlockRepository = contactUnlockRepository;
        this.caretakerProfileRepository = caretakerProfileRepository;
        this.hireContractRepository = hireContractRepository;
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
    public HireContract authorizeDisputeRoom(
            CareBridgeUserPrincipal principal,
            String rawContractId,
            boolean requireActiveForSend
    ) {
        requireActivePrincipal(principal);

        if (rawContractId == null || !POSITIVE_ID_PATTERN.matcher(rawContractId.trim()).matches()) {
            throw new AccessDeniedException("Invalid dispute mediation room identifier.");
        }

        long contractId;
        try {
            contractId = Long.parseLong(rawContractId.trim());
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("Invalid dispute mediation room identifier.");
        }

        HireContract contract = hireContractRepository.findByIdWithUsers(contractId)
                .orElseThrow(() -> new AccessDeniedException("This dispute mediation room is not active or accessible."));

        ContractStatus status = contract.getStatus();
        boolean statusAllowed = status == ContractStatus.PAYMENT_DISPUTED
                || (!requireActiveForSend && status == ContractStatus.DISPUTE_RESOLVED);
        if (!statusAllowed) {
            throw new AccessDeniedException(
                    requireActiveForSend && status == ContractStatus.DISPUTE_RESOLVED
                            ? "This dispute has been resolved and the mediation room is now read-only."
                            : "This dispute mediation room is not active or accessible."
            );
        }

        Long actingUserId = principal.getUserId();
        boolean isAdmin = principal.getRole() == Role.ADMIN;
        boolean isCustomerParticipant = principal.getRole() == Role.CUSTOMER
                && contract.getCustomer().getId().equals(actingUserId);
        boolean isApprovedCaretakerParticipant = principal.getRole() == Role.CARETAKER
                && contract.getCaretaker().getId().equals(actingUserId)
                && caretakerProfileRepository.existsByUserId(actingUserId);

        if (!isAdmin && !isCustomerParticipant && !isApprovedCaretakerParticipant) {
            throw new AccessDeniedException("You do not have permission to access this 3-way dispute room.");
        }

        return contract;
    }

    @Transactional(readOnly = true)
    public void requireActivePrincipal(CareBridgeUserPrincipal principal) {
        if (principal == null || !principal.isEnabled()
                || !userRepository.existsByIdAndActiveTrue(principal.getUserId())) {
            throw new AccessDeniedException("Authenticated active user session is required.");
        }
    }
}
