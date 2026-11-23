package com.carebridge.booking;

import com.carebridge.booking.dto.CaretakerDayScheduleResponse;
import com.carebridge.booking.dto.ContractActionReasonRequest;
import com.carebridge.booking.dto.CreateHireRequest;
import com.carebridge.booking.dto.DeclineHireRequest;
import com.carebridge.booking.dto.HireContractResponse;
import com.carebridge.common.NotFoundException;
import com.carebridge.security.CareBridgeUserPrincipal;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Strictly NON-transactional concurrency coordinator that acquires a per-caretaker striped
 * ReentrantLock BEFORE entering HiringTransactionalService and releases it AFTER the DB
 * transaction commits or rolls back, preventing MySQL REPEATABLE READ phantom/stale counts.
 */
@Service
public class BookingConcurrencyCoordinator {

    private static final int LOCK_STRIPE_COUNT = 256;

    private final ReentrantLock[] lockStripes;
    private final HiringTransactionalService hiringTransactionalService;
    private final HireContractRepository hireContractRepository;

    public BookingConcurrencyCoordinator(
            HiringTransactionalService hiringTransactionalService,
            HireContractRepository hireContractRepository
    ) {
        this.hiringTransactionalService = hiringTransactionalService;
        this.hireContractRepository = hireContractRepository;
        this.lockStripes = new ReentrantLock[LOCK_STRIPE_COUNT];
        for (int i = 0; i < LOCK_STRIPE_COUNT; i++) {
            this.lockStripes[i] = new ReentrantLock(true);
        }
    }

    public HireContractResponse createHireRequest(
            CareBridgeUserPrincipal customerPrincipal,
            CreateHireRequest request
    ) {
        return executeWithCaretakerLock(
                request.caretakerUserId(),
                () -> hiringTransactionalService.createHireRequest(customerPrincipal, request)
        );
    }

    public HireContractResponse acceptHireRequest(
            CareBridgeUserPrincipal caretakerPrincipal,
            Long contractId
    ) {
        Long caretakerUserId = resolveCaretakerIdForContract(contractId);
        return executeWithCaretakerLock(
                caretakerUserId,
                () -> hiringTransactionalService.acceptHireRequest(caretakerPrincipal, contractId)
        );
    }

    public HireContractResponse declineHireRequest(
            CareBridgeUserPrincipal caretakerPrincipal,
            Long contractId,
            DeclineHireRequest request
    ) {
        Long caretakerUserId = resolveCaretakerIdForContract(contractId);
        return executeWithCaretakerLock(
                caretakerUserId,
                () -> hiringTransactionalService.declineHireRequest(caretakerPrincipal, contractId, request)
        );
    }

    public HireContractResponse cancelContract(
            CareBridgeUserPrincipal actorPrincipal,
            Long contractId,
            ContractActionReasonRequest request
    ) {
        Long caretakerUserId = resolveCaretakerIdForContract(contractId);
        return executeWithCaretakerLock(
                caretakerUserId,
                () -> hiringTransactionalService.cancelContract(actorPrincipal, contractId, request)
        );
    }

    public HireContractResponse endContractEarly(
            CareBridgeUserPrincipal actorPrincipal,
            Long contractId,
            ContractActionReasonRequest request
    ) {
        Long caretakerUserId = resolveCaretakerIdForContract(contractId);
        return executeWithCaretakerLock(
                caretakerUserId,
                () -> hiringTransactionalService.endContractEarly(actorPrincipal, contractId, request)
        );
    }

    public List<HireContractResponse> listMyHires(
            CareBridgeUserPrincipal requester,
            Set<ContractStatus> statusFilter
    ) {
        return hiringTransactionalService.listHires(requester, statusFilter);
    }

    public HireContractResponse getHireDetail(
            CareBridgeUserPrincipal requester,
            Long contractId
    ) {
        return hiringTransactionalService.getHireDetail(requester, contractId);
    }

    public CaretakerDayScheduleResponse getCaretakerDaySchedule(
            CareBridgeUserPrincipal requester,
            Long caretakerUserId,
            LocalDate dhakaDate
    ) {
        return hiringTransactionalService.getCaretakerDaySchedule(requester, caretakerUserId, dhakaDate);
    }

    private Long resolveCaretakerIdForContract(Long contractId) {
        return hireContractRepository.findCaretakerIdByContractId(contractId)
                .orElseThrow(() -> new NotFoundException("Hire contract not found."));
    }

    private <T> T executeWithCaretakerLock(Long caretakerUserId, Supplier<T> action) {
        ReentrantLock lock = lockFor(caretakerUserId);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock lockFor(Long caretakerUserId) {
        int stripeIndex = Math.floorMod(Long.hashCode(caretakerUserId), LOCK_STRIPE_COUNT);
        return lockStripes[stripeIndex];
    }
}
