package com.carebridge.booking;

import com.carebridge.common.BadRequestException;
import com.carebridge.common.ConflictException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure domain rules for shift windows, overlap detection, and the max-2-bookings-per-day invariant in Asia/Dhaka.
 */
public final class BookingScheduleValidator {

    public static final ZoneId DHAKA_ZONE = ZoneId.of("Asia/Dhaka");
    public static final int MAX_BOOKINGS_PER_DAY = 2;

    private static final Duration MIN_SHIFT_DURATION = Duration.ofHours(1);
    private static final Duration MAX_SHIFT_DURATION = Duration.ofDays(30);
    private static final Duration PAST_START_GRACE = Duration.ofMinutes(5);

    private BookingScheduleValidator() {
    }

    public static void validateRequestedWindow(Instant start, Instant end, Instant now) {
        if (start == null || end == null) {
            throw new BadRequestException("Service start time and end time are required.");
        }
        if (!start.isBefore(end)) {
            throw new BadRequestException("Service end time must be strictly after the start time.");
        }
        if (!end.isAfter(now)) {
            throw new BadRequestException("Service end time must be in the future.");
        }
        if (start.isBefore(now.minus(PAST_START_GRACE))) {
            throw new BadRequestException("Service start time cannot be in the past.");
        }

        Duration duration = Duration.between(start, end);
        if (duration.compareTo(MIN_SHIFT_DURATION) < 0) {
            throw new BadRequestException("Minimum shift booking duration is 1 hour.");
        }
        if (duration.compareTo(MAX_SHIFT_DURATION) > 0) {
            throw new BadRequestException("A single contract cannot exceed 30 days. Please split into separate bookings.");
        }
    }

    public static void validateAcceptWindow(Instant start, Instant end, Instant now) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BadRequestException("Contract has an invalid service period.");
        }
        if (!end.isAfter(now)) {
            throw new ConflictException(
                    "This hire request's scheduled shift period has already passed and can no longer be accepted."
            );
        }
    }

    /**
     * Enumerates every Asia/Dhaka calendar date touched by the half-open interval [start, end).
     * Shifts ending cleanly at 00:00 Dhaka do not bleed into the next calendar day.
     */
    public static List<LocalDate> touchedDhakaDates(Instant start, Instant end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BadRequestException("Invalid shift window.");
        }
        LocalDate startDate = start.atZone(DHAKA_ZONE).toLocalDate();
        LocalDate endDate = end.minusNanos(1).atZone(DHAKA_ZONE).toLocalDate();

        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    public static Instant dhakaDayStart(LocalDate date) {
        return date.atStartOfDay(DHAKA_ZONE).toInstant();
    }

    public static Instant dhakaDayEnd(LocalDate date) {
        return date.plusDays(1).atStartOfDay(DHAKA_ZONE).toInstant();
    }

    public static boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    public static void enforceNoOverlapAndDailyCap(
            Instant requestedStart,
            Instant requestedEnd,
            List<HireContract> existingBlockingContracts
    ) {
        List<LocalDate> requestedDates = touchedDhakaDates(requestedStart, requestedEnd);
        Map<LocalDate, Integer> blockingCountByDate = new HashMap<>();

        for (HireContract existing : existingBlockingContracts) {
            if (overlaps(existing.getScheduledStartTime(), existing.getScheduledEndTime(), requestedStart, requestedEnd)) {
                throw new ConflictException(
                        "This caretaker already has a confirmed booking that overlaps your selected time slot. "
                                + "Please pick a non-overlapping shift window."
                );
            }

            List<LocalDate> existingDates = touchedDhakaDates(
                    existing.getScheduledStartTime(),
                    existing.getScheduledEndTime()
            );
            for (LocalDate touchedDate : existingDates) {
                blockingCountByDate.merge(touchedDate, 1, Integer::sum);
            }
        }

        for (LocalDate requestedDate : requestedDates) {
            int countOnDate = blockingCountByDate.getOrDefault(requestedDate, 0);
            if (countOnDate >= MAX_BOOKINGS_PER_DAY) {
                throw new ConflictException(
                        "This caretaker already has " + MAX_BOOKINGS_PER_DAY
                                + " confirmed bookings on " + requestedDate
                                + " (Dhaka time). Caretakers can accept at most 2 bookings per calendar day."
                );
            }
        }
    }

    public static int countBlockingOnDhakaDate(LocalDate dhakaDate, List<HireContract> blockingContracts) {
        int count = 0;
        for (HireContract contract : blockingContracts) {
            List<LocalDate> touched = touchedDhakaDates(
                    contract.getScheduledStartTime(),
                    contract.getScheduledEndTime()
            );
            if (touched.contains(dhakaDate)) {
                count++;
            }
        }
        return count;
    }
}
