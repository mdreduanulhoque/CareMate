package com.carebridge.booking.dto;

import com.carebridge.booking.ContactUnlock;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.RateType;
import com.carebridge.chat.NegotiationRoomId;
import com.carebridge.user.User;

import java.math.BigDecimal;
import java.time.Instant;

public record ContactUnlockResponse(
        Long unlockId,
        String roomId,
        Long customerId,
        Long caretakerUserId,
        String caretakerFullName,
        String caretakerMobileNumber,
        String caretakerProfileImageUrl,
        String caretakerPrimaryQualification,
        String caretakerLocationArea,
        boolean caretakerAvailable,
        BigDecimal caretakerRateAmount,
        RateType caretakerRateType,
        Instant unlockedAt,
        boolean newlyUnlocked
) {
    public static ContactUnlockResponse from(
            ContactUnlock unlock,
            CaretakerProfile profile,
            boolean newlyUnlocked
    ) {
        User customer = unlock.getCustomer();
        User caretaker = unlock.getCaretaker();
        return new ContactUnlockResponse(
                unlock.getId(),
                NegotiationRoomId.format(customer.getId(), caretaker.getId()),
                customer.getId(),
                caretaker.getId(),
                caretaker.getFullName(),
                caretaker.getMobileNumber(),
                profile.getProfileImageUrl(),
                profile.getPrimaryQualification(),
                profile.getLocationArea(),
                profile.isAvailable(),
                profile.getRateAmount(),
                profile.getRateType(),
                unlock.getUnlockedAt(),
                newlyUnlocked
        );
    }
}
