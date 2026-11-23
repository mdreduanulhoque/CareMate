package com.carebridge.payment;

import com.carebridge.booking.ContractStatus;
import com.carebridge.booking.HireContract;
import com.carebridge.booking.HireContractRepository;
import com.carebridge.booking.HiringTransactionalService;
import com.carebridge.booking.dto.HireContractResponse;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.common.BadRequestException;
import com.carebridge.common.ConflictException;
import com.carebridge.common.ForbiddenException;
import com.carebridge.common.NotFoundException;
import com.carebridge.common.TransactionCommitHooks;
import com.carebridge.notification.RealtimeEventDispatcher;
import com.carebridge.notification.dto.AdminQueueAlertEvent;
import com.carebridge.notification.dto.UserNotificationEvent;
import com.carebridge.payment.dto.CustomerPaymentDoneRequest;
import com.carebridge.payment.dto.DisputeStatementRequest;
import com.carebridge.payment.dto.PaymentDisputeResponse;
import com.carebridge.payment.dto.RefusePaymentRequest;
import com.carebridge.payment.dto.ResolveDisputeRequest;
import com.carebridge.security.CareBridgeUserPrincipal;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PaymentAndDisputeService {

    private final HireContractRepository hireContractRepository;
    private final PaymentDisputeRepository paymentDisputeRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final UserRepository userRepository;
    private final HiringTransactionalService hiringTransactionalService;
    private final RealtimeEventDispatcher realtimeEventDispatcher;

    public PaymentAndDisputeService(
            HireContractRepository hireContractRepository,
            PaymentDisputeRepository paymentDisputeRepository,
            CaretakerProfileRepository caretakerProfileRepository,
            UserRepository userRepository,
            HiringTransactionalService hiringTransactionalService,
            RealtimeEventDispatcher realtimeEventDispatcher
    ) {
        this.hireContractRepository = hireContractRepository;
        this.paymentDisputeRepository = paymentDisputeRepository;
        this.caretakerProfileRepository = caretakerProfileRepository;
        this.userRepository = userRepository;
        this.hiringTransactionalService = hiringTransactionalService;
        this.realtimeEventDispatcher = realtimeEventDispatcher;
    }

    @Transactional
    public HireContractResponse markCustomerPaymentDone(
            CareBridgeUserPrincipal customerPrincipal,
            Long contractId,
            CustomerPaymentDoneRequest request
    ) {
        HireContract contract = loadContractOrThrow(contractId);
        if (!contract.getCustomer().getId().equals(customerPrincipal.getUserId())) {
            throw new ForbiddenException("Only the customer who created this hire contract can mark payment done.");
        }
        if (contract.getStatus() != ContractStatus.COMPLETED_PENDING_SETTLEMENT) {
            throw new ConflictException("Payment can only be marked done once the shift is in settlement.");
        }
        if (contract.isCustomerPaymentConfirmed()) {
            throw new ConflictException("Payment is already marked done and awaiting caretaker confirmation.");
        }

        String referenceNote = request != null ? normalizeOptional(request.paymentReferenceNote()) : null;
        contract.markCustomerPaymentDone(referenceNote);
        HireContract saved = hireContractRepository.saveAndFlush(contract);

        Long caretakerId = saved.getCaretaker().getId();
        User customer = saved.getCustomer();
        UserNotificationEvent alert = UserNotificationEvent.forContract(
                "PAYMENT_MARKED_DONE",
                saved.getId(),
                "Relative Marked Payment Done",
                referenceNote != null
                        ? customer.getFullName() + " marked payment done for contract #" + saved.getId()
                                + " (Ref: " + referenceNote + "). Please confirm receipt."
                        : customer.getFullName() + " marked payment done for contract #" + saved.getId()
                                + ". Please confirm receipt.",
                customer.getId(),
                customer.getFullName()
        );
        TransactionCommitHooks.runAfterCommit(
                () -> realtimeEventDispatcher.publishUserNotification(caretakerId, alert)
        );

        return hiringTransactionalService.getHireDetail(customerPrincipal, saved.getId());
    }

    @Transactional
    public HireContractResponse confirmCaretakerPaymentReceived(
            CareBridgeUserPrincipal caretakerPrincipal,
            Long contractId
    ) {
        requireApprovedCaretaker(caretakerPrincipal.getUserId());
        HireContract contract = loadContractOrThrow(contractId);
        if (!contract.getCaretaker().getId().equals(caretakerPrincipal.getUserId())) {
            throw new ForbiddenException("Only the assigned caretaker can confirm payment receipt.");
        }
        if (contract.getStatus() != ContractStatus.COMPLETED_PENDING_SETTLEMENT) {
            throw new ConflictException("Only contracts pending settlement can be confirmed as paid.");
        }
        if (!contract.isCustomerPaymentConfirmed()) {
            throw new ConflictException(
                    "Please wait until the relative marks payment as done before confirming receipt."
            );
        }

        contract.confirmCaretakerPaymentReceived();
        HireContract saved = hireContractRepository.saveAndFlush(contract);

        Long customerId = saved.getCustomer().getId();
        User caretaker = saved.getCaretaker();
        UserNotificationEvent alert = UserNotificationEvent.forContract(
                "PAYMENT_CONFIRMED_CLOSED",
                saved.getId(),
                "Payment Confirmed — Contract Closed",
                caretaker.getFullName() + " confirmed payment receipt for contract #" + saved.getId() + ".",
                caretaker.getId(),
                caretaker.getFullName()
        );
        TransactionCommitHooks.runAfterCommit(
                () -> realtimeEventDispatcher.publishUserNotification(customerId, alert)
        );

        return hiringTransactionalService.getHireDetail(caretakerPrincipal, saved.getId());
    }

    @Transactional
    public PaymentDisputeResponse refuseCaretakerPaymentAndOpenDispute(
            CareBridgeUserPrincipal caretakerPrincipal,
            Long contractId,
            RefusePaymentRequest request
    ) {
        User caretaker = requireApprovedCaretaker(caretakerPrincipal.getUserId());
        HireContract contract = loadContractOrThrow(contractId);
        if (!contract.getCaretaker().getId().equals(caretaker.getId())) {
            throw new ForbiddenException("Only the assigned caretaker can refuse or dispute payment.");
        }
        if (contract.getStatus() != ContractStatus.COMPLETED_PENDING_SETTLEMENT) {
            throw new ConflictException("Only contracts in settlement can be moved to payment dispute.");
        }
        if (!contract.isCustomerPaymentConfirmed()) {
            throw new ConflictException(
                    "A payment dispute opens only after the relative has marked payment as done."
            );
        }
        if (paymentDisputeRepository.findByContractIdWithDetails(contractId).isPresent()) {
            throw new ConflictException("A payment dispute is already open for this contract.");
        }

        String caretakerStatement = normalizeMandatory(request.caretakerStatement(), 5, "dispute statement");
        contract.markPaymentDisputed();
        HireContract savedContract = hireContractRepository.saveAndFlush(contract);

        PaymentDispute dispute = paymentDisputeRepository.saveAndFlush(new PaymentDispute(
                savedContract,
                caretaker,
                savedContract.getPaymentReferenceNote(),
                caretakerStatement
        ));

        Long customerId = savedContract.getCustomer().getId();
        AdminQueueAlertEvent adminAlert = AdminQueueAlertEvent.of(
                "PAYMENT_DISPUTE",
                savedContract.getId(),
                "Urgent Payment Dispute — Contract #" + savedContract.getId(),
                caretaker.getFullName() + " reported payment not received from "
                        + savedContract.getCustomer().getFullName() + ".",
                caretaker.getId(),
                caretaker.getFullName()
        );
        UserNotificationEvent customerAlert = UserNotificationEvent.forContract(
                "PAYMENT_DISPUTED",
                savedContract.getId(),
                "Payment Dispute Opened — Admin Notified",
                caretaker.getFullName() + " reported payment not received for contract #"
                        + savedContract.getId() + ". Join the 3-way mediation room to share evidence.",
                caretaker.getId(),
                caretaker.getFullName()
        );

        TransactionCommitHooks.runAfterCommit(() -> {
            realtimeEventDispatcher.publishAdminAlert(adminAlert);
            realtimeEventDispatcher.publishUserNotification(customerId, customerAlert);
        });

        return PaymentDisputeResponse.from(dispute);
    }

    @Transactional(readOnly = true)
    public List<PaymentDisputeResponse> listDisputes(
            CareBridgeUserPrincipal requester,
            DisputeStatus statusFilter
    ) {
        if (requester.getRole() == Role.ADMIN) {
            return paymentDisputeRepository.findAllWithDetailsByStatus(statusFilter).stream()
                    .map(PaymentDisputeResponse::from)
                    .toList();
        }
        if (requester.getRole() == Role.CARETAKER
                && !caretakerProfileRepository.existsByUserId(requester.getUserId())) {
            throw new ForbiddenException("Unverified caretakers cannot access payment disputes.");
        }
        return paymentDisputeRepository.findAllForParticipant(requester.getUserId(), statusFilter).stream()
                .map(PaymentDisputeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentDisputeResponse getDisputeByContract(CareBridgeUserPrincipal requester, Long contractId) {
        PaymentDispute dispute = paymentDisputeRepository.findByContractIdWithDetails(contractId)
                .orElseThrow(() -> new NotFoundException("Payment dispute not found for this contract."));
        requireDisputeViewerPermission(requester, dispute.getContract());
        return PaymentDisputeResponse.from(dispute);
    }

    @Transactional
    public PaymentDisputeResponse updateParticipantStatement(
            CareBridgeUserPrincipal requester,
            Long contractId,
            DisputeStatementRequest request
    ) {
        PaymentDispute dispute = paymentDisputeRepository.findByContractIdWithDetails(contractId)
                .orElseThrow(() -> new NotFoundException("Payment dispute not found for this contract."));

        HireContract contract = dispute.getContract();
        if (dispute.getStatus() == DisputeStatus.RESOLVED) {
            throw new ConflictException("Statements cannot be updated after the dispute is resolved.");
        }

        String text = normalizeMandatory(request.statement(), 5, "statement");
        Long userId = requester.getUserId();
        if (requester.getRole() == Role.CUSTOMER && contract.getCustomer().getId().equals(userId)) {
            dispute.updateCustomerStatement(text);
        } else if (requester.getRole() == Role.CARETAKER
                && contract.getCaretaker().getId().equals(userId)
                && caretakerProfileRepository.existsByUserId(userId)) {
            dispute.updateCaretakerStatement(text);
        } else {
            throw new ForbiddenException("Only the customer or caretaker on this contract can update their statement.");
        }

        PaymentDispute saved = paymentDisputeRepository.saveAndFlush(dispute);
        return PaymentDisputeResponse.from(saved);
    }

    @Transactional
    public PaymentDisputeResponse joinDisputeAsAdmin(CareBridgeUserPrincipal adminPrincipal, Long contractId) {
        User admin = userRepository.findById(adminPrincipal.getUserId())
                .filter(u -> u.isActive() && u.getRole() == Role.ADMIN)
                .orElseThrow(() -> new ForbiddenException("Admin privileges are required to mediate disputes."));

        PaymentDispute dispute = paymentDisputeRepository.findByContractIdWithDetails(contractId)
                .orElseThrow(() -> new NotFoundException("Payment dispute not found for this contract."));
        if (dispute.getStatus() == DisputeStatus.RESOLVED) {
            return PaymentDisputeResponse.from(dispute);
        }

        boolean wasOpen = dispute.getStatus() == DisputeStatus.OPEN;
        dispute.assignAdminUnderReview(admin);
        PaymentDispute saved = paymentDisputeRepository.saveAndFlush(dispute);

        if (wasOpen) {
            Long customerId = saved.getContract().getCustomer().getId();
            Long caretakerId = saved.getContract().getCaretaker().getId();
            UserNotificationEvent alert = UserNotificationEvent.forContract(
                    "DISPUTE_ADMIN_JOINED",
                    contractId,
                    "Admin Mediator Joined Dispute #" + contractId,
                    admin.getFullName() + " joined the 3-way dispute room to review payment evidence.",
                    admin.getId(),
                    admin.getFullName()
            );
            TransactionCommitHooks.runAfterCommit(() -> {
                realtimeEventDispatcher.publishUserNotification(customerId, alert);
                realtimeEventDispatcher.publishUserNotification(caretakerId, alert);
            });
        }

        return PaymentDisputeResponse.from(saved);
    }

    @Transactional
    public PaymentDisputeResponse resolveDispute(
            CareBridgeUserPrincipal adminPrincipal,
            Long contractId,
            ResolveDisputeRequest request
    ) {
        User admin = userRepository.findById(adminPrincipal.getUserId())
                .filter(u -> u.isActive() && u.getRole() == Role.ADMIN)
                .orElseThrow(() -> new ForbiddenException("Admin privileges are required to resolve disputes."));

        PaymentDispute dispute = paymentDisputeRepository.findByContractIdWithDetails(contractId)
                .orElseThrow(() -> new NotFoundException("Payment dispute not found for this contract."));
        if (dispute.getStatus() == DisputeStatus.RESOLVED) {
            throw new ConflictException("This payment dispute has already been resolved.");
        }

        String resolutionNote = normalizeMandatory(request.adminResolutionNote(), 5, "resolution note");
        HireContract contract = dispute.getContract();
        dispute.resolve(admin, resolutionNote, Instant.now());
        contract.resolvePaymentDispute();

        hireContractRepository.saveAndFlush(contract);
        PaymentDispute saved = paymentDisputeRepository.saveAndFlush(dispute);

        Long customerId = contract.getCustomer().getId();
        Long caretakerId = contract.getCaretaker().getId();
        UserNotificationEvent alert = UserNotificationEvent.forContract(
                "DISPUTE_RESOLVED",
                contractId,
                "Payment Dispute Resolved — Contract #" + contractId,
                "Admin resolution: " + resolutionNote,
                admin.getId(),
                admin.getFullName()
        );
        TransactionCommitHooks.runAfterCommit(() -> {
            realtimeEventDispatcher.publishUserNotification(customerId, alert);
            realtimeEventDispatcher.publishUserNotification(caretakerId, alert);
        });

        return PaymentDisputeResponse.from(saved);
    }

    private HireContract loadContractOrThrow(Long contractId) {
        return hireContractRepository.findByIdWithUsers(contractId)
                .orElseThrow(() -> new NotFoundException("Hire contract not found."));
    }

    private User requireApprovedCaretaker(Long caretakerUserId) {
        User user = userRepository.findById(caretakerUserId)
                .orElseThrow(() -> new NotFoundException("Caretaker account not found."));
        if (!user.isActive() || user.getRole() != Role.CARETAKER
                || !caretakerProfileRepository.existsByUserId(caretakerUserId)) {
            throw new ForbiddenException("Only active approved caretakers can perform this action.");
        }
        return user;
    }

    private void requireDisputeViewerPermission(CareBridgeUserPrincipal requester, HireContract contract) {
        if (requester.getRole() == Role.ADMIN) {
            return;
        }
        Long userId = requester.getUserId();
        boolean isCustomer = requester.getRole() == Role.CUSTOMER && contract.getCustomer().getId().equals(userId);
        boolean isApprovedCaretaker = requester.getRole() == Role.CARETAKER
                && contract.getCaretaker().getId().equals(userId)
                && caretakerProfileRepository.existsByUserId(userId);
        if (!isCustomer && !isApprovedCaretaker) {
            throw new ForbiddenException("You do not have permission to access this payment dispute.");
        }
    }

    private String normalizeOptional(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeMandatory(String raw, int minLen, String label) {
        if (raw == null || raw.trim().length() < minLen) {
            throw new BadRequestException("A clear " + label + " (at least " + minLen + " characters) is required.");
        }
        return raw.trim();
    }
}
