package com.carebridge.booking;

import com.carebridge.booking.dto.CreateHireRequest;
import com.carebridge.booking.dto.HireContractResponse;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.common.ConflictException;
import com.carebridge.security.CareBridgeUserPrincipal;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyIntegrationTest {

    @Autowired
    private BookingConcurrencyCoordinator bookingCoordinator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CaretakerProfileRepository caretakerProfileRepository;

    @Autowired
    private HireContractRepository hireContractRepository;

    @Test
    void concurrentOverlappingAcceptsAllowExactlyOneWinnerAndRejectAllOthers() throws Exception {
        User caretaker = createApprovedCaretaker("Concurrent Nurse Overlap", "01562000001");
        CareBridgeUserPrincipal caretakerPrincipal = new CareBridgeUserPrincipal(caretaker);

        LocalDate targetDay = LocalDate.now(BookingScheduleValidator.DHAKA_ZONE).plusDays(5);
        Instant start = dhaka(targetDay, 9, 0);
        Instant end = dhaka(targetDay, 15, 0);

        int competingRequests = 6;
        List<Long> contractIds = new ArrayList<>();
        for (int i = 0; i < competingRequests; i++) {
            User customer = createCustomer("Overlap Customer " + i, "0156200010" + i);
            HireContractResponse created = bookingCoordinator.createHireRequest(
                    new CareBridgeUserPrincipal(customer),
                    new CreateHireRequest(
                            caretaker.getId(),
                            new BigDecimal("2000.00"),
                            ContractRateType.FIXED,
                            start,
                            end,
                            "Competing shift #" + i
                    )
            );
            contractIds.add(created.id());
        }

        ConcurrencyResult result = runConcurrentAccepts(caretakerPrincipal, contractIds);
        assertThat(result.successes()).isEqualTo(1);
        assertThat(result.conflicts()).isEqualTo(competingRequests - 1);
        assertThat(result.unexpectedErrors()).isEmpty();

        List<HireContract> confirmed = hireContractRepository.findCaretakerContractsTouchingWindow(
                caretaker.getId(),
                ContractStatus.BLOCKING_STATUSES,
                BookingScheduleValidator.dhakaDayStart(targetDay),
                BookingScheduleValidator.dhakaDayEnd(targetDay)
        );
        assertThat(confirmed).hasSize(1);
    }

    @Test
    void concurrentThreeSameDayNonOverlappingAcceptsAllowExactlyTwoAndBlockThird() throws Exception {
        User caretaker = createApprovedCaretaker("Concurrent Nurse DailyCap", "01562000002");
        CareBridgeUserPrincipal caretakerPrincipal = new CareBridgeUserPrincipal(caretaker);

        LocalDate targetDay = LocalDate.now(BookingScheduleValidator.DHAKA_ZONE).plusDays(6);

        Instant[][] shifts = new Instant[][]{
                {dhaka(targetDay, 6, 0), dhaka(targetDay, 9, 0)},
                {dhaka(targetDay, 11, 0), dhaka(targetDay, 14, 0)},
                {dhaka(targetDay, 17, 0), dhaka(targetDay, 20, 0)}
        };

        List<Long> contractIds = new ArrayList<>();
        for (int i = 0; i < shifts.length; i++) {
            User customer = createCustomer("DailyCap Customer " + i, "0156200020" + i);
            HireContractResponse created = bookingCoordinator.createHireRequest(
                    new CareBridgeUserPrincipal(customer),
                    new CreateHireRequest(
                            caretaker.getId(),
                            new BigDecimal("1500.00"),
                            ContractRateType.FIXED,
                            shifts[i][0],
                            shifts[i][1],
                            "Shift #" + (i + 1)
                    )
            );
            contractIds.add(created.id());
        }

        ConcurrencyResult result = runConcurrentAccepts(caretakerPrincipal, contractIds);
        assertThat(result.successes()).isEqualTo(2);
        assertThat(result.conflicts()).isEqualTo(1);
        assertThat(result.unexpectedErrors()).isEmpty();

        List<HireContract> confirmed = hireContractRepository.findCaretakerContractsTouchingWindow(
                caretaker.getId(),
                ContractStatus.BLOCKING_STATUSES,
                BookingScheduleValidator.dhakaDayStart(targetDay),
                BookingScheduleValidator.dhakaDayEnd(targetDay)
        );
        assertThat(confirmed).hasSize(2);
    }

    @Test
    void concurrentOvernightAndNextDayAcceptsRespectTwoPerDayCapOnSharedDhakaDate() throws Exception {
        User caretaker = createApprovedCaretaker("Concurrent Nurse Midnight", "01562000003");
        CareBridgeUserPrincipal caretakerPrincipal = new CareBridgeUserPrincipal(caretaker);

        LocalDate day1 = LocalDate.now(BookingScheduleValidator.DHAKA_ZONE).plusDays(8);
        LocalDate day2 = day1.plusDays(1);

        // Pre-book 1 confirmed midday shift on Day 2
        User baseCustomer = createCustomer("Day2 Base Customer", "01562000301");
        HireContractResponse baseReq = bookingCoordinator.createHireRequest(
                new CareBridgeUserPrincipal(baseCustomer),
                new CreateHireRequest(
                        caretaker.getId(),
                        new BigDecimal("1800.00"),
                        ContractRateType.FIXED,
                        dhaka(day2, 9, 0),
                        dhaka(day2, 13, 0),
                        "Confirmed Day 2 midday shift"
                )
        );
        bookingCoordinator.acceptHireRequest(caretakerPrincipal, baseReq.id());

        // Two competing requests that BOTH touch Day 2 without overlapping each other or the midday shift:
        // Request A: Day 1 22:00 -> Day 2 06:00 (spans midnight into Day 2)
        // Request B: Day 2 15:00 -> Day 2 19:00
        User customerA = createCustomer("Overnight Customer", "01562000302");
        HireContractResponse reqA = bookingCoordinator.createHireRequest(
                new CareBridgeUserPrincipal(customerA),
                new CreateHireRequest(
                        caretaker.getId(),
                        new BigDecimal("2500.00"),
                        ContractRateType.FIXED,
                        dhaka(day1, 22, 0),
                        dhaka(day2, 6, 0),
                        "Overnight shift"
                )
        );

        User customerB = createCustomer("Day2 Evening Customer", "01562000303");
        HireContractResponse reqB = bookingCoordinator.createHireRequest(
                new CareBridgeUserPrincipal(customerB),
                new CreateHireRequest(
                        caretaker.getId(),
                        new BigDecimal("1700.00"),
                        ContractRateType.FIXED,
                        dhaka(day2, 15, 0),
                        dhaka(day2, 19, 0),
                        "Day 2 evening shift"
                )
        );

        ConcurrencyResult result = runConcurrentAccepts(caretakerPrincipal, List.of(reqA.id(), reqB.id()));
        assertThat(result.successes()).isEqualTo(1);
        assertThat(result.conflicts()).isEqualTo(1);
        assertThat(result.unexpectedErrors()).isEmpty();
    }

    private ConcurrencyResult runConcurrentAccepts(
            CareBridgeUserPrincipal caretakerPrincipal,
            List<Long> contractIds
    ) throws Exception {
        int threads = contractIds.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyGate = new CountDownLatch(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Throwable> unexpectedErrors = new ArrayList<>();

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Long contractId : contractIds) {
                futures.add(executor.submit(() -> {
                    readyGate.countDown();
                    try {
                        startGate.await(5, TimeUnit.SECONDS);
                        bookingCoordinator.acceptHireRequest(caretakerPrincipal, contractId);
                        successes.incrementAndGet();
                    } catch (ConflictException expectedConflict) {
                        conflicts.incrementAndGet();
                    } catch (Throwable ex) {
                        synchronized (unexpectedErrors) {
                            unexpectedErrors.add(ex);
                        }
                    }
                }));
            }

            assertThat(readyGate.await(5, TimeUnit.SECONDS)).isTrue();
            startGate.countDown();

            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        return new ConcurrencyResult(successes.get(), conflicts.get(), unexpectedErrors);
    }

    private User createCustomer(String name, String mobile) {
        return userRepository.saveAndFlush(new User(
                name,
                mobile,
                null,
                "$2a$12$dummyhashfortestonly123456789012345678901234567890123",
                "Dhaka, Bangladesh",
                Role.CUSTOMER
        ));
    }

    private User createApprovedCaretaker(String name, String mobile) {
        User user = userRepository.saveAndFlush(new User(
                name,
                mobile,
                null,
                "$2a$12$dummyhashfortestonly123456789012345678901234567890123",
                "Dhanmondi, Dhaka",
                Role.CARETAKER
        ));
        caretakerProfileRepository.saveAndFlush(new CaretakerProfile(user, "BSc in Nursing", "Dhanmondi, Dhaka"));
        return user;
    }

    private Instant dhaka(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                hour,
                minute,
                0,
                0,
                BookingScheduleValidator.DHAKA_ZONE
        ).toInstant();
    }

    private record ConcurrencyResult(int successes, int conflicts, List<Throwable> unexpectedErrors) {
    }
}
