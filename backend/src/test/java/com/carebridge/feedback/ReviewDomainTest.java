package com.carebridge.feedback;

import com.carebridge.booking.ContractRateType;
import com.carebridge.booking.HireContract;
import com.carebridge.common.BadRequestException;
import com.carebridge.common.ConflictException;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewDomainTest {

    private static Review pendingCustomerReview() {
        User customer = new User("Customer", "01711111111", null, "hash", "Dhaka", Role.CUSTOMER);
        User caretaker = new User("Caretaker", "01811111111", null, "hash", "Dhaka", Role.CARETAKER);
        Instant start = Instant.parse("2026-12-01T06:00:00Z");
        Instant end = Instant.parse("2026-12-01T14:00:00Z");
        HireContract contract = new HireContract(
                customer,
                caretaker,
                new BigDecimal("2000.00"),
                ContractRateType.DAILY,
                start,
                end,
                null
        );
        return new Review(contract, customer, caretaker, Role.CUSTOMER);
    }

    @Test
    void supportsInitialSubmitAndEditWithinSevenDaysWithoutExtendingWindow() {
        Review review = pendingCustomerReview();
        Instant t0 = Instant.parse("2026-12-02T10:00:00Z");

        review.submitInitial(4, "Attentive bedside nurse.", false, t0);
        assertThat(review.isSubmitted()).isTrue();
        assertThat(review.isEdited()).isFalse();
        assertThat(review.isLocked()).isFalse();
        assertThat(review.getSubmittedAt()).isEqualTo(t0);
        assertThat(review.getEditableUntil()).isEqualTo(t0.plus(7, ChronoUnit.DAYS));
        assertThat(review.isCurrentlyEditable(t0.plus(6, ChronoUnit.DAYS))).isTrue();

        // Duplicate submit fails
        assertThatThrownBy(() -> review.submitInitial(5, "Again", true, t0.plusSeconds(60)))
                .isInstanceOf(ConflictException.class);

        // Edit on Day 3 updates fields, sets edited=true, preserves submittedAt & editableUntil
        Instant tEdit = t0.plus(3, ChronoUnit.DAYS);
        review.editSubmitted(5, "Updated: truly compassionate care.", true, tEdit);
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getWouldEngageAgain()).isTrue();
        assertThat(review.isEdited()).isTrue();
        assertThat(review.getSubmittedAt()).isEqualTo(t0);
        assertThat(review.getUpdatedAt()).isEqualTo(tEdit);
        assertThat(review.getEditableUntil()).isEqualTo(t0.plus(7, ChronoUnit.DAYS));
    }

    @Test
    void permanentlyLocksEditsOnceSevenDayWindowExpires() {
        Review review = pendingCustomerReview();
        Instant t0 = Instant.parse("2026-12-02T10:00:00Z");
        review.submitInitial(5, "Great service.", true, t0);

        Instant exactlySevenDaysLater = t0.plus(7, ChronoUnit.DAYS);
        assertThat(review.isCurrentlyEditable(exactlySevenDaysLater)).isFalse();
        assertThat(review.isEffectivelyLocked(exactlySevenDaysLater)).isTrue();

        assertThatThrownBy(() -> review.editSubmitted(1, "Late retaliation attempt", false, exactlySevenDaysLater))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("7-day");
        assertThat(review.isLocked()).isTrue();
    }

    @Test
    void rejectsInvalidRatingsAndEditingBeforeSubmission() {
        Review review = pendingCustomerReview();
        Instant now = Instant.now();

        assertThatThrownBy(() -> review.editSubmitted(5, "Too early", true, now))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> review.submitInitial(0, "Invalid", true, now))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> review.submitInitial(6, "Invalid", true, now))
                .isInstanceOf(BadRequestException.class);
    }
}
