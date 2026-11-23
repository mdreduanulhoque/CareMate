package com.carebridge.booking;

import com.carebridge.booking.dto.CaretakerDayScheduleResponse;
import com.carebridge.booking.dto.ContractActionReasonRequest;
import com.carebridge.booking.dto.CreateHireRequest;
import com.carebridge.booking.dto.DeclineHireRequest;
import com.carebridge.booking.dto.HireContractResponse;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.chat.NegotiationRoomId;
import com.carebridge.common.BadRequestException;
import com.carebridge.common.ConflictException;
import com.carebridge.common.ForbiddenException;
import com.carebridge.common.NotFoundException;
import com.carebridge.common.TransactionCommitHooks;
import com.carebridge.feedback.FeedbackAggregateService;
import com.carebridge.feedback.Review;
import com.carebridge.feedback.ReviewRepository;
import com.carebridge.notification.RealtimeEventDispatcher;
import com.carebridge.notification.dto.FeedAvailabilityEvent;
import com.carebridge.notification.dto.UserNotificationEvent;
import com.carebridge.security.CareBridgeUserPrincipal;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HiringTransactionalService {

    private static final Set<ContractStatus> COMPLETED_HISTORY_STATUSES = EnumSet.of(
            ContractStatus.COMPLETED_PENDING_SETTLEMENT,
            ContractStatus.CLOSED_PAID,
            ContractStatus.PAYMENT_DISPUTED,
            ContractStatus.DISPUTE_RESOLVED
    );

    private final HireContractRepository hireContractRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final ContactUnlockRepository contactUnlockRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final FeedbackAggregateService feedbackAggregateService;
    private final RealtimeEventDispatcher realtimeEventDispatcher;
    private final EntityManager entityManager;

    public HiringTransactionalService(
            HireContractRepository hireContractRepository,
            CaretakerProfileRepository caretakerProfileRepository,
            ContactUnlockRepository contactUnlockRepository,
            UserRepository userRepository,
            ReviewRepository reviewRepository,
            FeedbackAggregateService feedbackAggregateService,
            RealtimeEventDispatcher realtimeEventDispatcher,
            EntityManager entityManager
    ) {
        this.hireContractRepository = hireContractRepository;
        this.caretakerProfileRepository = caretakerProfileRepository;
        this.contactUnlockRepository = contactUnlockRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.feedbackAggregateService = feedbackAggregateService;
        this.realtimeEventDispatcher = realtimeEventDispatcher;
        this.entityManager = entityManager;
    }

    @Transactional
    public HireContractResponse createHireRequest(
            CareBridgeUserPrincipal customerPrincipal,
            CreateHireRequest request
    ) {
        User customer = requireActiveUserWithRole(customerPrincipal.getUserId(), Role.CUSTOMER);
        CaretakerProfile profile = caretakerProfileRepository.findActiveByUserIdWithUser(request.caretakerUserId())
                .orElseThrow(() -> new NotFoundException("Verified caretaker profile not found."));

        if (customer.getId().equals(profile.getUser().getId())) {
            throw new BadRequestException("You cannot submit a hire request to yourself.");
        }

        Instant now = Instant.now();
        BookingScheduleValidator.validateRequestedWindow(
                request.scheduledStartTime(),
                request.scheduledEndTime(),
                now
        );

        // Enforce non-overlapping and max-2-per-day invariants against confirmed blocking bookings
        List<HireContract> blockingInWindow = loadBlockingContractsForRequestedDates(
                profile.getUser().getId(),
                request.scheduledStartTime(),
                request.scheduledEndTime()
        );
        BookingScheduleValidator.enforceNoOverlapAndDailyCap(
                request.scheduledStartTime(),
                request.scheduledEndTime(),
                blockingInWindow
        );

        // Prevent the same customer from submitting duplicate overlapping pending requests
        boolean duplicatePending = hireContractRepository.existsOverlappingByCustomerAndCaretakerAndStatus(
                customer.getId(),
                profile.getUser().getId(),
                ContractStatus.REQUESTED,
                request.scheduledStartTime(),
                request.scheduledEndTime()
        );
        if (duplicatePending) {
            throw new ConflictException(
                    "You already have a pending hire request with this caretaker for an overlapping shift window."
            );
        }

        HireContract saved = hireContractRepository.saveAndFlush(new HireContract(
                customer,
                profile.getUser(),
                request.agreedRate(),
                request.rateType(),
                request.scheduledStartTime(),
                request.scheduledEndTime(),
                normalizeOptionalText(request.notes())
        ));

        Long caretakerId = profile.getUser().getId();
        UserNotificationEvent notification = UserNotificationEvent.forContract(
                "HIRE_REQUESTED",
                saved.getId(),
                "New Hire Request from " + customer.getFullName(),
                "Agreed rate: ৳" + saved.getAgreedRate().stripTrailingZeros().toPlainString()
                        + " (" + saved.getRateType().name().toLowerCase() + "). Tap to review and respond.",
                customer.getId(),
                customer.getFullName()
        );
        TransactionCommitHooks.runAfterCommit(
                () -> realtimeEventDispatcher.publishUserNotification(caretakerId, notification)
        );

        return toSingleResponse(customerPrincipal, saved, profile, now);
    }

    @Transactional
    public HireContractResponse acceptHireRequest(
            CareBridgeUserPrincipal caretakerPrincipal,
            Long contractId
    ) {
        requireApprovedCaretaker(caretakerPrincipal.getUserId());

        HireContract contract = hireContractRepository.findByIdWithUsers(contractId)
                .orElseThrow(() -> new NotFoundException("Hire contract not found."));

        if (!contract.getCaretaker().getId().equals(caretakerPrincipal.getUserId())) {
            throw new ForbiddenException("Only the assigned caretaker can accept this hire request.");
        }
        if (contract.getStatus() != ContractStatus.REQUESTED) {
            throw new ConflictException("Only pending hire requests can be accepted.");
        }

        CaretakerProfile profile = caretakerProfileRepository.findByUserIdWithUser(caretakerPrincipal.getUserId())
                .orElseThrow(() -> new ForbiddenException("Caretaker verification must be approved before accepting hires."));

        // Defense-in-depth cross-process optimistic locking on CaretakerProfile.version
        entityManager.lock(profile, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

        Instant now = Instant.now();
        BookingScheduleValidator.validateAcceptWindow(
                contract.getScheduledStartTime(),
                contract.getScheduledEndTime(),
                now
        );

        List<HireContract> blockingInWindow = loadBlockingContractsForRequestedDates(
                caretakerPrincipal.getUserId(),
                contract.getScheduledStartTime(),
                contract.getScheduledEndTime()
        );
        BookingScheduleValidator.enforceNoOverlapAndDailyCap(
                contract.getScheduledStartTime(),
                contract.getScheduledEndTime(),
                blockingInWindow
        );

        ContractStatus targetStatus = !now.isBefore(contract.getScheduledStartTime())
                ? ContractStatus.ACTIVE
                : ContractStatus.SCHEDULED;
        contract.accept(targetStatus);

        HireContract saved = hireContractRepository.saveAndFlush(contract);

        int todayBookingsCount = countTodayBlockingBookings(profile.getUser().getId());
        FeedAvailabilityEvent availabilityEvent = FeedAvailabilityEvent.from(profile, todayBookingsCount);
        Long customerId = saved.getCustomer().getId();
        UserNotificationEvent notification = UserNotificationEvent.forContract(
                "HIRE_ACCEPTED",
                saved.getId(),
                "Hire Request Accepted!",
                saved.getCaretaker().getFullName() + " accepted your hospital care booking ("
                        + (targetStatus == ContractStatus.ACTIVE ? "Active now" : "Scheduled") + ").",
                saved.getCaretaker().getId(),
                saved.getCaretaker().getFullName()
        );

        TransactionCommitHooks.runAfterCommit(() -> {
            realtimeEventDispatcher.publishFeedAvailability(availabilityEvent);
            realtimeEventDispatcher.publishUserNotification(customerId, notification);
        });

        return toSingleResponse(caretakerPrincipal, saved, profile, now);
    }

    @Transactional
    public HireContractResponse declineHireRequest(
            CareBridgeUserPrincipal caretakerPrincipal,
            Long contractId,
            DeclineHireRequest request
    ) {
        User caretaker = requireApprovedCaretaker(caretakerPrincipal.getUserId());

        HireContract contract = hireContractRepository.findByIdWithUsers(contractId)
                .orElseThrow(() -> new NotFoundException("Hire contract not found."));

        if (!contract.getCaretaker().getId().equals(caretaker.getId())) {
            throw new ForbiddenException("Only the assigned caretaker can decline this hire request.");
        }
        if (contract.getStatus() != ContractStatus.REQUESTED) {
            throw new ConflictException("Only pending hire requests can be declined.");
        }

        String reason = request != null ? normalizeOptionalText(request.reason()) : null;
        contract.decline(caretaker, reason);
        HireContract saved = hireContractRepository.saveAndFlush(contract);

        CaretakerProfile profile = caretakerProfileRepository.findByUserIdWithUser(caretaker.getId()).orElse(null);
        Long customerId = saved.getCustomer().getId();
        UserNotificationEvent notification = UserNotificationEvent.forContract(
                "HIRE_DECLINED",
                saved.getId(),
                "Hire Request Declined",
                reason != null
                        ? caretaker.getFullName() + " declined your request: " + reason
                        : caretaker.getFullName() + " declined your hire request.",
                caretaker.getId(),
                caretaker.getFullName()
        );

        TransactionCommitHooks.runAfterCommit(
                () -> realtimeEventDispatcher.publishUserNotification(customerId, notification)
        );

        return toSingleResponse(caretakerPrincipal, saved, profile, Instant.now());
    }

    @Transactional
    public HireContractResponse cancelContract(
            CareBridgeUserPrincipal actorPrincipal,
            Long contractId,
            ContractActionReasonRequest request
    ) {
        User actor = requireActiveUser(actorPrincipal.getUserId());
        HireContract contract = hireContractRepository.findByIdWithUsers(contractId)
                .orElseThrow(() -> new NotFoundException("Hire contract not found."));

        requireContractParticipantOrAdmin(actorPrincipal, contract);

        Instant now = Instant.now();
        ContractStatus previousStatus = contract.getStatus();
        boolean isAdmin = actor.getRole() == Role.ADMIN;

        if (previousStatus == ContractStatus.REQUESTED) {
            // Allowed for customer, caretaker, or admin
        } else if (previousStatus == ContractStatus.SCHEDULED) {
            if (!isAdmin && !now.isBefore(contract.getScheduledStartTime())) {
                throw new ConflictException(
                        "This shift has already started. Use 'End Service Early' to close an active shift."
                );
            }
        } else {
            throw new ConflictException("Only pending or upcoming scheduled bookings can be cancelled.");
        }

        String reason = normalizeMandatoryReason(request.reason(), "cancellation");
        boolean wasBlocking = ContractStatus.BLOCKING_STATUSES.contains(previousStatus);

        contract.cancel(actor, reason);
        HireContract saved = hireContractRepository.saveAndFlush(contract);

        CaretakerProfile profile = caretakerProfileRepository
                .findByUserIdWithUser(saved.getCaretaker().getId())
                .orElse(null);

        FeedAvailabilityEvent availabilityEvent = wasBlocking && profile != null
                ? FeedAvailabilityEvent.from(profile, countTodayBlockingBookings(profile.getUser().getId()))
                : null;

        UserNotificationEvent notification = UserNotificationEvent.forContract(
                "CONTRACT_CANCELLED",
                saved.getId(),
                "Booking Cancelled",
                actor.getFullName() + " cancelled booking #" + saved.getId() + ": " + reason,
                actor.getId(),
                actor.getFullName()
        );
        List<Long> recipients = resolveCounterpartRecipients(actor, saved);

        TransactionCommitHooks.runAfterCommit(() -> {
            if (availabilityEvent != null) {
                realtimeEventDispatcher.publishFeedAvailability(availabilityEvent);
            }
            for (Long recipientId : recipients) {
                realtimeEventDispatcher.publishUserNotification(recipientId, notification);
            }
        });

        return toSingleResponse(actorPrincipal, saved, profile, now);
    }

    @Transactional
    public HireContractResponse endContractEarly(
            CareBridgeUserPrincipal actorPrincipal,
            Long contractId,
            ContractActionReasonRequest request
    ) {
        User actor = requireActiveUser(actorPrincipal.getUserId());
        HireContract contract = hireContractRepository.findByIdWithUsers(contractId)
                .orElseThrow(() -> new NotFoundException("Hire contract not found."));

        requireContractParticipantOrAdmin(actorPrincipal, contract);

        Instant now = Instant.now();
        ContractStatus previousStatus = contract.getStatus();
        boolean isStartedScheduled = previousStatus == ContractStatus.SCHEDULED
                && !now.isBefore(contract.getScheduledStartTime());

        if (previousStatus == ContractStatus.SCHEDULED && now.isBefore(contract.getScheduledStartTime())) {
            throw new ConflictException(
                    "This shift has not started yet. Use 'Cancel Booking' to cancel before service starts."
            );
        }
        if (previousStatus != ContractStatus.ACTIVE && !isStartedScheduled) {
            throw new ConflictException("Only an active shift can be ended early.");
        }

        String reason = normalizeMandatoryReason(request.reason(), "early termination");
        contract.endEarly(actor, reason, now);
        HireContract saved = hireContractRepository.saveAndFlush(contract);

        // Immediately & idempotently generate dual pending feedback forms and refresh completed hires count
        feedbackAggregateService.ensurePendingReviewsForCompletedContract(saved);
        feedbackAggregateService.recalculateCaretakerMetrics(saved.getCaretaker().getId());

        CaretakerProfile profile = caretakerProfileRepository
                .findByUserIdWithUser(saved.getCaretaker().getId())
                .orElse(null);

        FeedAvailabilityEvent availabilityEvent = profile != null
                ? FeedAvailabilityEvent.from(profile, countTodayBlockingBookings(profile.getUser().getId()))
                : null;

        UserNotificationEvent notification = UserNotificationEvent.forContract(
                "CONTRACT_ENDED_EARLY",
                saved.getId(),
                "Service Ended Early — Feedback Ready",
                actor.getFullName() + " ended shift #" + saved.getId()
                        + " early (" + reason + "). Pending feedback forms are now ready.",
                actor.getId(),
                actor.getFullName()
        );
        List<Long> recipients = resolveCounterpartRecipients(actor, saved);

        TransactionCommitHooks.runAfterCommit(() -> {
            if (availabilityEvent != null) {
                realtimeEventDispatcher.publishFeedAvailability(availabilityEvent);
            }
            for (Long recipientId : recipients) {
                realtimeEventDispatcher.publishUserNotification(recipientId, notification);
            }
        });

        return toSingleResponse(actorPrincipal, saved, profile, now);
    }

    /**
     * Idempotent background worker transition promoting due SCHEDULED contracts to ACTIVE.
     */
    @Transactional
    public boolean activateScheduledContract(Long contractId, Instant now) {
        HireContract contract = hireContractRepository.findByIdWithUsers(contractId).orElse(null);
        if (contract == null
                || contract.getStatus() != ContractStatus.SCHEDULED
                || now.isBefore(contract.getScheduledStartTime())
                || !now.isBefore(contract.getScheduledEndTime())) {
            return false;
        }

        contract.activateFromScheduled();
        HireContract saved = hireContractRepository.saveAndFlush(contract);

        Long customerId = saved.getCustomer().getId();
        Long caretakerId = saved.getCaretaker().getId();
        UserNotificationEvent customerAlert = UserNotificationEvent.forContract(
                "CONTRACT_ACTIVATED",
                saved.getId(),
                "Hospital Care Shift Started",
                "Your booking #" + saved.getId() + " with " + saved.getCaretaker().getFullName() + " is now active.",
                caretakerId,
                saved.getCaretaker().getFullName()
        );
        UserNotificationEvent caretakerAlert = UserNotificationEvent.forContract(
                "CONTRACT_ACTIVATED",
                saved.getId(),
                "Shift Is Now Active",
                "Your booking #" + saved.getId() + " for " + saved.getCustomer().getFullName() + " has started.",
                customerId,
                saved.getCustomer().getFullName()
        );

        TransactionCommitHooks.runAfterCommit(() -> {
            realtimeEventDispatcher.publishUserNotification(customerId, customerAlert);
            realtimeEventDispatcher.publishUserNotification(caretakerId, caretakerAlert);
        });
        return true;
    }

    /**
     * Idempotent background worker transition completing expired ACTIVE/SCHEDULED shifts,
     * generating dual pending feedback forms, refreshing caretaker availability, and notifying both parties.
     */
    @Transactional
    public boolean autoCompleteExpiredContract(Long contractId, Instant now) {
        HireContract contract = hireContractRepository.findByIdWithUsers(contractId).orElse(null);
        if (contract == null
                || !ContractStatus.BLOCKING_STATUSES.contains(contract.getStatus())
                || now.isBefore(contract.getScheduledEndTime())) {
            return false;
        }

        contract.autoComplete(contract.getScheduledEndTime());
        HireContract saved = hireContractRepository.saveAndFlush(contract);

        feedbackAggregateService.ensurePendingReviewsForCompletedContract(saved);
        feedbackAggregateService.recalculateCaretakerMetrics(saved.getCaretaker().getId());

        CaretakerProfile profile = caretakerProfileRepository
                .findByUserIdWithUser(saved.getCaretaker().getId())
                .orElse(null);
        FeedAvailabilityEvent availabilityEvent = profile != null
                ? FeedAvailabilityEvent.from(profile, countTodayBlockingBookings(profile.getUser().getId()))
                : null;

        Long customerId = saved.getCustomer().getId();
        Long caretakerId = saved.getCaretaker().getId();
        UserNotificationEvent customerAlert = UserNotificationEvent.forContract(
                "CONTRACT_COMPLETED_FEEDBACK_READY",
                saved.getId(),
                "Shift Completed — Please Share Feedback",
                "Shift #" + saved.getId() + " with " + saved.getCaretaker().getFullName()
                        + " has ended. Your feedback form is ready whenever you have a moment.",
                caretakerId,
                saved.getCaretaker().getFullName()
        );
        UserNotificationEvent caretakerAlert = UserNotificationEvent.forContract(
                "CONTRACT_COMPLETED_FEEDBACK_READY",
                saved.getId(),
                "Shift Completed — Feedback Ready",
                "Shift #" + saved.getId() + " for " + saved.getCustomer().getFullName()
                        + " is complete. You can now review the customer and track settlement.",
                customerId,
                saved.getCustomer().getFullName()
        );

        TransactionCommitHooks.runAfterCommit(() -> {
            if (availabilityEvent != null) {
                realtimeEventDispatcher.publishFeedAvailability(availabilityEvent);
            }
            realtimeEventDispatcher.publishUserNotification(customerId, customerAlert);
            realtimeEventDispatcher.publishUserNotification(caretakerId, caretakerAlert);
        });
        return true;
    }

    @Transactional(readOnly = true)
    public List<HireContractResponse> listHires(
            CareBridgeUserPrincipal requester,
            Set<ContractStatus> statusFilter
    ) {
        boolean filterByStatuses = statusFilter != null && !statusFilter.isEmpty();
        CollectionForQuery queryStatuses = new CollectionForQuery(
                filterByStatuses ? statusFilter : EnumSet.of(ContractStatus.REQUESTED)
        );

        List<HireContract> contracts;
        if (requester.getRole() == Role.CUSTOMER) {
            contracts = hireContractRepository.findForCustomer(
                    requester.getUserId(),
                    filterByStatuses,
                    queryStatuses.statuses()
            );
        } else if (requester.getRole() == Role.CARETAKER) {
            requireApprovedCaretaker(requester.getUserId());
            contracts = hireContractRepository.findForCaretaker(
                    requester.getUserId(),
                    filterByStatuses,
                    queryStatuses.statuses()
            );
        } else if (requester.getRole() == Role.ADMIN) {
            contracts = hireContractRepository.findAllWithUsers(
                    filterByStatuses,
                    queryStatuses.statuses()
            );
        } else {
            throw new ForbiddenException("You do not have permission to view hire contracts.");
        }

        return mapBatchResponses(requester, contracts);
    }

    @Transactional(readOnly = true)
    public HireContractResponse getHireDetail(CareBridgeUserPrincipal requester, Long contractId) {
        HireContract contract = hireContractRepository.findByIdWithUsers(contractId)
                .orElseThrow(() -> new NotFoundException("Hire contract not found."));

        requireContractParticipantOrAdmin(requester, contract);
        CaretakerProfile profile = caretakerProfileRepository
                .findByUserIdWithUser(contract.getCaretaker().getId())
                .orElse(null);

        return toSingleResponse(requester, contract, profile, Instant.now());
    }

    @Transactional(readOnly = true)
    public CaretakerDayScheduleResponse getCaretakerDaySchedule(
            CareBridgeUserPrincipal requester,
            Long caretakerUserId,
            LocalDate dhakaDate
    ) {
        if (requester.getRole() == Role.CARETAKER && !caretakerProfileRepository.existsByUserId(requester.getUserId())) {
            throw new ForbiddenException("Unverified caretakers cannot view booking schedules.");
        }
        if (!caretakerProfileRepository.existsByUserId(caretakerUserId)) {
            throw new NotFoundException("Verified caretaker profile not found.");
        }

        LocalDate targetDate = dhakaDate != null
                ? dhakaDate
                : LocalDate.now(BookingScheduleValidator.DHAKA_ZONE);
        Instant dayStart = BookingScheduleValidator.dhakaDayStart(targetDate);
        Instant dayEnd = BookingScheduleValidator.dhakaDayEnd(targetDate);

        List<HireContract> blocking = hireContractRepository.findCaretakerContractsTouchingWindow(
                caretakerUserId,
                ContractStatus.BLOCKING_STATUSES,
                dayStart,
                dayEnd
        );

        int count = BookingScheduleValidator.countBlockingOnDhakaDate(targetDate, blocking);
        List<CaretakerDayScheduleResponse.BusySlot> slots = blocking.stream()
                .map(c -> new CaretakerDayScheduleResponse.BusySlot(
                        c.getScheduledStartTime(),
                        c.getScheduledEndTime(),
                        c.getStatus()
                ))
                .toList();

        return new CaretakerDayScheduleResponse(
                caretakerUserId,
                targetDate,
                count,
                BookingScheduleValidator.MAX_BOOKINGS_PER_DAY,
                count >= BookingScheduleValidator.MAX_BOOKINGS_PER_DAY,
                slots
        );
    }

    @Transactional(readOnly = true)
    public int countTodayBlockingBookings(Long caretakerUserId) {
        LocalDate todayDhaka = LocalDate.now(BookingScheduleValidator.DHAKA_ZONE);
        List<HireContract> blocking = hireContractRepository.findCaretakerContractsTouchingWindow(
                caretakerUserId,
                ContractStatus.BLOCKING_STATUSES,
                BookingScheduleValidator.dhakaDayStart(todayDhaka),
                BookingScheduleValidator.dhakaDayEnd(todayDhaka)
        );
        return BookingScheduleValidator.countBlockingOnDhakaDate(todayDhaka, blocking);
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> countTodayBlockingBookingsByCaretakerIds(List<Long> caretakerUserIds) {
        if (caretakerUserIds == null || caretakerUserIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LocalDate todayDhaka = LocalDate.now(BookingScheduleValidator.DHAKA_ZONE);
        List<HireContract> blocking = hireContractRepository.findCaretakersContractsTouchingWindow(
                caretakerUserIds,
                ContractStatus.BLOCKING_STATUSES,
                BookingScheduleValidator.dhakaDayStart(todayDhaka),
                BookingScheduleValidator.dhakaDayEnd(todayDhaka)
        );

        Map<Long, Integer> counts = new HashMap<>();
        for (HireContract contract : blocking) {
            if (BookingScheduleValidator.touchedDhakaDates(
                    contract.getScheduledStartTime(),
                    contract.getScheduledEndTime()
            ).contains(todayDhaka)) {
                counts.merge(contract.getCaretaker().getId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private List<HireContract> loadBlockingContractsForRequestedDates(
            Long caretakerUserId,
            Instant requestedStart,
            Instant requestedEnd
    ) {
        List<LocalDate> touchedDates = BookingScheduleValidator.touchedDhakaDates(requestedStart, requestedEnd);
        Instant windowStart = BookingScheduleValidator.dhakaDayStart(touchedDates.getFirst());
        Instant windowEnd = BookingScheduleValidator.dhakaDayEnd(touchedDates.getLast());

        return hireContractRepository.findCaretakerContractsTouchingWindow(
                caretakerUserId,
                ContractStatus.BLOCKING_STATUSES,
                windowStart,
                windowEnd
        );
    }

    private List<HireContractResponse> mapBatchResponses(
            CareBridgeUserPrincipal requester,
            List<HireContract> contracts
    ) {
        if (contracts.isEmpty()) {
            return List.of();
        }

        List<Long> caretakerIds = contracts.stream()
                .map(c -> c.getCaretaker().getId())
                .distinct()
                .toList();
        Map<Long, CaretakerProfile> profileByCaretakerId = caretakerProfileRepository.findByUserIdIn(caretakerIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity(), (a, b) -> a));

        List<Long> customerIds = contracts.stream()
                .map(c -> c.getCustomer().getId())
                .distinct()
                .toList();
        Map<Long, Integer> completedByCustomerId = new HashMap<>();
        for (Object[] row : hireContractRepository.countCompletedHiresByCustomerIds(customerIds, COMPLETED_HISTORY_STATUSES)) {
            completedByCustomerId.put((Long) row[0], ((Number) row[1]).intValue());
        }
        Map<Long, FeedbackAggregateService.ReviewSummaryStats> customerStatsById =
                feedbackAggregateService.summarizeCustomerReviewMetrics(customerIds);

        List<Long> contractIds = contracts.stream().map(HireContract::getId).toList();
        Map<Long, Review> viewerReviewsByContractId = requester.getRole() == Role.ADMIN
                ? Collections.emptyMap()
                : reviewRepository.findByContractIdInAndReviewerId(contractIds, requester.getUserId())
                        .stream()
                        .collect(Collectors.toMap(r -> r.getContract().getId(), Function.identity(), (a, b) -> a));

        Set<String> unlockedPairs = loadUnlockedPairsForRequester(requester, contracts);
        Instant now = Instant.now();

        return contracts.stream()
                .map(contract -> {
                    Long customerId = contract.getCustomer().getId();
                    Long caretakerId = contract.getCaretaker().getId();
                    String pairKey = customerId + ":" + caretakerId;
                    boolean unlocked = unlockedPairs.contains(pairKey);
                    String roomId = unlocked ? NegotiationRoomId.format(customerId, caretakerId) : null;

                    return buildResponse(
                            requester,
                            contract,
                            profileByCaretakerId.get(caretakerId),
                            unlocked,
                            roomId,
                            completedByCustomerId.getOrDefault(customerId, 0),
                            customerStatsById.get(customerId),
                            viewerReviewsByContractId.get(contract.getId()),
                            now
                    );
                })
                .toList();
    }

    private Set<String> loadUnlockedPairsForRequester(
            CareBridgeUserPrincipal requester,
            List<HireContract> contracts
    ) {
        if (requester.getRole() == Role.CUSTOMER) {
            return contactUnlockRepository.findAllForCustomer(requester.getUserId()).stream()
                    .map(cu -> cu.getCustomer().getId() + ":" + cu.getCaretaker().getId())
                    .collect(Collectors.toSet());
        }
        if (requester.getRole() == Role.CARETAKER) {
            return contactUnlockRepository.findAllForCaretaker(requester.getUserId()).stream()
                    .map(cu -> cu.getCustomer().getId() + ":" + cu.getCaretaker().getId())
                    .collect(Collectors.toSet());
        }
        return contracts.stream()
                .filter(c -> contactUnlockRepository.existsByCustomerIdAndCaretakerId(
                        c.getCustomer().getId(), c.getCaretaker().getId()))
                .map(c -> c.getCustomer().getId() + ":" + c.getCaretaker().getId())
                .collect(Collectors.toSet());
    }

    private HireContractResponse toSingleResponse(
            CareBridgeUserPrincipal requester,
            HireContract contract,
            CaretakerProfile caretakerProfile,
            Instant now
    ) {
        Long customerId = contract.getCustomer().getId();
        Long caretakerId = contract.getCaretaker().getId();
        boolean unlocked = contactUnlockRepository.existsByCustomerIdAndCaretakerId(customerId, caretakerId);
        String roomId = unlocked ? NegotiationRoomId.format(customerId, caretakerId) : null;

        int completedCount = 0;
        List<Object[]> rows = hireContractRepository.countCompletedHiresByCustomerIds(
                List.of(customerId),
                COMPLETED_HISTORY_STATUSES
        );
        if (!rows.isEmpty()) {
            completedCount = ((Number) rows.getFirst()[1]).intValue();
        }
        FeedbackAggregateService.ReviewSummaryStats customerStats = feedbackAggregateService
                .summarizeCustomerReviewMetrics(List.of(customerId))
                .get(customerId);
        Review viewerReview = requester.getRole() == Role.ADMIN
                ? null
                : reviewRepository.findByContractIdInAndReviewerId(List.of(contract.getId()), requester.getUserId())
                        .stream()
                        .findFirst()
                        .orElse(null);

        return buildResponse(
                requester,
                contract,
                caretakerProfile,
                unlocked,
                roomId,
                completedCount,
                customerStats,
                viewerReview,
                now
        );
    }

    private HireContractResponse buildResponse(
            CareBridgeUserPrincipal requester,
            HireContract contract,
            CaretakerProfile caretakerProfile,
            boolean contactUnlocked,
            String negotiationRoomId,
            int customerCompletedHiresCount,
            FeedbackAggregateService.ReviewSummaryStats customerStats,
            Review viewerReview,
            Instant now
    ) {
        ContractStatus status = contract.getStatus();
        boolean confirmedOrLater = status != ContractStatus.REQUESTED
                && status != ContractStatus.DECLINED
                && status != ContractStatus.CANCELLED;

        boolean exposeCaretakerMobile = requester.getRole() != Role.CUSTOMER
                || contactUnlocked
                || confirmedOrLater;
        boolean exposeCustomerMobile = requester.getRole() != Role.CARETAKER
                || contactUnlocked
                || confirmedOrLater;

        return HireContractResponse.from(
                contract,
                caretakerProfile,
                contactUnlocked,
                negotiationRoomId,
                exposeCaretakerMobile,
                exposeCustomerMobile,
                customerCompletedHiresCount,
                customerStats,
                viewerReview,
                now
        );
    }

    private void requireContractParticipantOrAdmin(CareBridgeUserPrincipal requester, HireContract contract) {
        if (requester.getRole() == Role.ADMIN) {
            return;
        }
        Long userId = requester.getUserId();
        boolean isCustomer = requester.getRole() == Role.CUSTOMER && contract.getCustomer().getId().equals(userId);
        boolean isApprovedCaretaker = requester.getRole() == Role.CARETAKER
                && contract.getCaretaker().getId().equals(userId)
                && caretakerProfileRepository.existsByUserId(userId);

        if (!isCustomer && !isApprovedCaretaker) {
            throw new ForbiddenException("You do not have permission to access or modify this hire contract.");
        }
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User account not found."));
        if (!user.isActive()) {
            throw new ForbiddenException("Your account is currently inactive.");
        }
        return user;
    }

    private User requireActiveUserWithRole(Long userId, Role expectedRole) {
        User user = requireActiveUser(userId);
        if (user.getRole() != expectedRole) {
            throw new ForbiddenException("Your role does not allow performing this action.");
        }
        return user;
    }

    private User requireApprovedCaretaker(Long caretakerUserId) {
        User user = requireActiveUserWithRole(caretakerUserId, Role.CARETAKER);
        if (!caretakerProfileRepository.existsByUserId(caretakerUserId)) {
            throw new ForbiddenException("Your caretaker account must be approved by an Admin first.");
        }
        return user;
    }

    private List<Long> resolveCounterpartRecipients(User actor, HireContract contract) {
        Long customerId = contract.getCustomer().getId();
        Long caretakerId = contract.getCaretaker().getId();
        if (actor.getRole() == Role.ADMIN) {
            return List.of(customerId, caretakerId);
        }
        if (actor.getId().equals(customerId)) {
            return List.of(caretakerId);
        }
        return List.of(customerId);
    }

    private String normalizeOptionalText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeMandatoryReason(String rawReason, String actionLabel) {
        if (rawReason == null || rawReason.trim().length() < 3) {
            throw new BadRequestException("A brief " + actionLabel + " reason (at least 3 characters) is required.");
        }
        return rawReason.trim();
    }

    private record CollectionForQuery(Set<ContractStatus> statuses) {
    }
}
