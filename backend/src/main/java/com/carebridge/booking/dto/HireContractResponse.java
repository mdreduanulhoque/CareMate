package com.carebridge.booking.dto;

import com.carebridge.booking.ContractRateType;
import com.carebridge.booking.ContractStatus;
import com.carebridge.booking.HireContract;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.user.Role;
import com.carebridge.user.User;

import java.math.BigDecimal;
import java.time.Instant;

public record HireContractResponse(
        Long id,
        Long customerId,
        String customerFullName,
        String customerAddress,
        String customerMobileNumber,
        Instant customerMemberSince,
        int customerCompletedHiresCount,
        BigDecimal customerAvgRating,
        BigDecimal customerWorkAgainPercentage,
        int customerReviewCount,
        Long caretakerUserId,
        String caretakerFullName,
        String caretakerMobileNumber,
        String caretakerProfileImageUrl,
        String caretakerPrimaryQualification,
        String caretakerLocationArea,
        BigDecimal agreedRate,
        ContractRateType rateType,
        Instant scheduledStartTime,
        Instant scheduledEndTime,
        Instant actualEndTime,
        String cancellationOrEarlyEndReason,
        Long initiatedByUserId,
        String initiatedByName,
        Role initiatedByRole,
        String notes,
        ContractStatus status,
        boolean customerPaymentConfirmed,
        boolean caretakerPaymentConfirmed,
        String paymentReferenceNote,
        String negotiationRoomId,
        boolean contactUnlocked,
        boolean canCancel,
        boolean canEndEarly,
        Instant createdAt,
        Instant updatedAt
) {
    public static HireContractResponse from(
            HireContract contract,
            CaretakerProfile caretakerProfile,
            boolean contactUnlocked,
            String negotiationRoomId,
            boolean exposeCaretakerMobile,
            boolean exposeCustomerMobile,
            int customerCompletedHiresCount,
            Instant now
    ) {
        User customer = contract.getCustomer();
        User caretaker = contract.getCaretaker();
        User actor = contract.getInitiatedBy();
        ContractStatus status = contract.getStatus();

        boolean hasStarted = !now.isBefore(contract.getScheduledStartTime());
        boolean canCancel = status == ContractStatus.REQUESTED
                || (status == ContractStatus.SCHEDULED && !hasStarted);
        boolean canEndEarly = status == ContractStatus.ACTIVE
                || (status == ContractStatus.SCHEDULED && hasStarted);

        return new HireContractResponse(
                contract.getId(),
                customer.getId(),
                customer.getFullName(),
                customer.getAddress(),
                exposeCustomerMobile ? customer.getMobileNumber() : null,
                customer.getCreatedAt(),
                customerCompletedHiresCount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                caretaker.getId(),
                caretaker.getFullName(),
                exposeCaretakerMobile ? caretaker.getMobileNumber() : null,
                caretakerProfile != null ? caretakerProfile.getProfileImageUrl() : null,
                caretakerProfile != null ? caretakerProfile.getPrimaryQualification() : null,
                caretakerProfile != null ? caretakerProfile.getLocationArea() : caretaker.getAddress(),
                contract.getAgreedRate(),
                contract.getRateType(),
                contract.getScheduledStartTime(),
                contract.getScheduledEndTime(),
                contract.getActualEndTime(),
                contract.getCancellationOrEarlyEndReason(),
                actor != null ? actor.getId() : null,
                actor != null ? actor.getFullName() : null,
                actor != null ? actor.getRole() : null,
                contract.getNotes(),
                status,
                contract.isCustomerPaymentConfirmed(),
                contract.isCaretakerPaymentConfirmed(),
                contract.getPaymentReferenceNote(),
                negotiationRoomId,
                contactUnlocked,
                canCancel,
                canEndEarly,
                contract.getCreatedAt(),
                contract.getUpdatedAt()
        );
    }
}
