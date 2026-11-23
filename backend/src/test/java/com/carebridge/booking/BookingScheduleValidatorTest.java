package com.carebridge.booking;

import com.carebridge.common.BadRequestException;
import com.carebridge.common.ConflictException;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingScheduleValidatorTest {

    private static Instant dhaka(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, BookingScheduleValidator.DHAKA_ZONE)
                .toInstant();
    }

    private static HireContract contract(Instant start, Instant end) {
        User customer = new User("Customer", "01711000001", null, "hash", "Dhaka", Role.CUSTOMER);
        User caretaker = new User("Caretaker", "01811000001", null, "hash", "Dhaka", Role.CARETAKER);
        HireContract hc = new HireContract(
                customer,
                caretaker,
                new BigDecimal("1500.00"),
                ContractRateType.DAILY,
                start,
                end,
                null
        );
        hc.accept(ContractStatus.SCHEDULED);
        return hc;
    }

    @Test
    void enumeratesTouchedDhakaCalendarDatesAccuratelyAcrossMidnightBoundaries() {
        // Same-day shift
        assertThat(BookingScheduleValidator.touchedDhakaDates(
                dhaka(2026, 12, 10, 8, 0),
                dhaka(2026, 12, 10, 14, 0)
        )).containsExactly(LocalDate.of(2026, 12, 10));

        // Ends cleanly at 00:00 next Dhaka day -> must NOT spill into next calendar date
        assertThat(BookingScheduleValidator.touchedDhakaDates(
                dhaka(2026, 12, 10, 16, 0),
                dhaka(2026, 12, 11, 0, 0)
        )).containsExactly(LocalDate.of(2026, 12, 10));

        // Overnight shift crossing midnight touches both dates
        assertThat(BookingScheduleValidator.touchedDhakaDates(
                dhaka(2026, 12, 10, 20, 0),
                dhaka(2026, 12, 11, 8, 0)
        )).containsExactly(LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 11));
    }

    @Test
    void allowsAdjacentNonOverlappingShiftsAndBlocksAnyOverlap() {
        HireContract morning = contract(dhaka(2026, 12, 10, 8, 0), dhaka(2026, 12, 10, 14, 0));

        // Back-to-back afternoon shift starting right at 14:00 is allowed (2nd booking of the day)
        assertThatCode(() -> BookingScheduleValidator.enforceNoOverlapAndDailyCap(
                dhaka(2026, 12, 10, 14, 0),
                dhaka(2026, 12, 10, 20, 0),
                List.of(morning)
        )).doesNotThrowAnyException();

        // 1-minute overlap at 13:59 must throw ConflictException
        assertThatThrownBy(() -> BookingScheduleValidator.enforceNoOverlapAndDailyCap(
                dhaka(2026, 12, 10, 13, 59),
                dhaka(2026, 12, 10, 18, 0),
                List.of(morning)
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    void blocksThirdBookingOnSameDhakaDayIncludingOvernightCarryover() {
        HireContract overnight = contract(dhaka(2026, 12, 10, 20, 0), dhaka(2026, 12, 11, 6, 0));
        HireContract middayDay2 = contract(dhaka(2026, 12, 11, 10, 0), dhaka(2026, 12, 11, 14, 0));

        // Day 2 already has 2 bookings (overnight + midday), so evening shift on Day 2 must fail
        assertThatThrownBy(() -> BookingScheduleValidator.enforceNoOverlapAndDailyCap(
                dhaka(2026, 12, 11, 16, 0),
                dhaka(2026, 12, 11, 20, 0),
                List.of(overnight, middayDay2)
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("2026-12-11")
                .hasMessageContaining("2");

        // Day 1 only has 1 booking (overnight), so morning shift on Day 1 is still allowed
        assertThatCode(() -> BookingScheduleValidator.enforceNoOverlapAndDailyCap(
                dhaka(2026, 12, 10, 9, 0),
                dhaka(2026, 12, 10, 15, 0),
                List.of(overnight, middayDay2)
        )).doesNotThrowAnyException();
    }

    @Test
    void validatesShiftDurationAndPastStartBounds() {
        Instant now = dhaka(2026, 12, 10, 10, 0);

        // Less than 1 hour rejected
        assertThatThrownBy(() -> BookingScheduleValidator.validateRequestedWindow(
                now.plusSeconds(600),
                now.plusSeconds(3000),
                now
        )).isInstanceOf(BadRequestException.class);

        // Inverted window rejected
        assertThatThrownBy(() -> BookingScheduleValidator.validateRequestedWindow(
                now.plusSeconds(7200),
                now.plusSeconds(3600),
                now
        )).isInstanceOf(BadRequestException.class);

        // Far-past start rejected
        assertThatThrownBy(() -> BookingScheduleValidator.validateRequestedWindow(
                now.minusSeconds(3600),
                now.plusSeconds(3600),
                now
        )).isInstanceOf(BadRequestException.class);

        // Over 30 days rejected
        assertThatThrownBy(() -> BookingScheduleValidator.validateRequestedWindow(
                now.plusSeconds(3600),
                now.plusSeconds(3600 + 31L * 86400L),
                now
        )).isInstanceOf(BadRequestException.class);

        // Immediate start within 5-min grace for a 4-hour shift allowed
        assertThatCode(() -> BookingScheduleValidator.validateRequestedWindow(
                now.minusSeconds(120),
                now.plusSeconds(4 * 3600),
                now
        )).doesNotThrowAnyException();
    }
}
