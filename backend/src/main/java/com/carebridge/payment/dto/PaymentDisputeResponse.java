package com.carebridge.payment.dto;

import com.carebridge.booking.ContractRateType;
import com.carebridge.booking.ContractStatus;
import com.carebridge.booking.HireContract;
import com.carebridge.payment.DisputeStatus;
import com.carebridge.payment.PaymentDispute;
import com.carebridge.user.User;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDisputeResponse(
        Long id,
        Long contractId,
        ContractStatus contractStatus,
        BigDecimal agreedRate,
        ContractRateType rateType,
        Instant scheduledStartTime,
        Instant scheduledEndTime,
        Instant actualEndTime,
        String paymentReferenceNote,
        Long customerId,
        String customerFullName,
        String customerMobileNumber,
        Long caretakerUserId,
        String caretakerFullName,
        String caretakerMobileNumber,
        Long raisedByUserId,
        String raisedByName,
        Long assignedAdminId,
        String assignedAdminName,
        DisputeStatus status,
        String customerStatement,
        String caretakerStatement,
        String adminResolutionNote,
        boolean canSendMessages,
        Instant createdAt,
        Instant resolvedAt
) {
    public static PaymentDisputeResponse from(PaymentDispute dispute) {
        HireContract contract = dispute.getContract();
        User customer = contract.getCustomer();
        User caretaker = contract.getCaretaker();
        User raisedBy = dispute.getRaisedBy();
        User assignedAdmin = dispute.getAssignedAdmin();
        boolean canSend = contract.getStatus() == ContractStatus.PAYMENT_DISPUTED
                && dispute.getStatus() != DisputeStatus.RESOLVED;

        return new PaymentDisputeResponse(
                dispute.getId(),
                contract.getId(),
                contract.getStatus(),
                contract.getAgreedRate(),
                contract.getRateType(),
                contract.getScheduledStartTime(),
                contract.getScheduledEndTime(),
                contract.getActualEndTime(),
                contract.getPaymentReferenceNote(),
                customer.getId(),
                customer.getFullName(),
                customer.getMobileNumber(),
                caretaker.getId(),
                caretaker.getFullName(),
                caretaker.getMobileNumber(),
                raisedBy.getId(),
                raisedBy.getFullName(),
                assignedAdmin != null ? assignedAdmin.getId() : null,
                assignedAdmin != null ? assignedAdmin.getFullName() : null,
                dispute.getStatus(),
                dispute.getCustomerStatement(),
                dispute.getCaretakerStatement(),
                dispute.getAdminResolutionNote(),
                canSend,
                dispute.getCreatedAt(),
                dispute.getResolvedAt()
        );
    }
}
