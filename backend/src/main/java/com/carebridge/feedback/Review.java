package com.carebridge.feedback;

import com.carebridge.booking.HireContract;
import com.carebridge.common.BadRequestException;
import com.carebridge.common.ConflictException;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reviews_contract_reviewer",
                columnNames = {"contract_id", "reviewer_id"}
        )
)
public class Review {

    public static final Duration EDIT_WINDOW = Duration.ofDays(7);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private HireContract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewee_id", nullable = false)
    private User reviewee;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_role", nullable = false, length = 20)
    private Role reviewerRole;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "would_engage_again")
    private Boolean wouldEngageAgain;

    @Column(name = "is_submitted", nullable = false)
    private boolean submitted = false;

    @Column(name = "is_edited", nullable = false)
    private boolean edited = false;

    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "editable_until")
    private Instant editableUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
    }

    public Review(HireContract contract, User reviewer, User reviewee, Role reviewerRole) {
        this.contract = contract;
        this.reviewer = reviewer;
        this.reviewee = reviewee;
        this.reviewerRole = reviewerRole;
        this.submitted = false;
        this.edited = false;
        this.locked = false;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void submitInitial(int rating, String comment, boolean wouldEngageAgain, Instant now) {
        if (this.submitted) {
            throw new ConflictException("This review has already been submitted. Use edit within 7 days to update it.");
        }
        validateRating(rating);
        Instant ts = now.truncatedTo(ChronoUnit.MICROS);
        this.rating = rating;
        this.comment = comment;
        this.wouldEngageAgain = wouldEngageAgain;
        this.submitted = true;
        this.edited = false;
        this.locked = false;
        this.submittedAt = ts;
        this.updatedAt = ts;
        this.editableUntil = ts.plus(EDIT_WINDOW);
    }

    public void editSubmitted(int rating, String comment, boolean wouldEngageAgain, Instant now) {
        if (!this.submitted) {
            throw new BadRequestException("Submit your initial review before editing it.");
        }
        Instant ts = now.truncatedTo(ChronoUnit.MICROS);
        if (!isCurrentlyEditable(ts)) {
            this.locked = true;
            throw new ConflictException(
                    "This review is permanently locked because the 7-day editing window has expired."
            );
        }
        validateRating(rating);
        this.rating = rating;
        this.comment = comment;
        this.wouldEngageAgain = wouldEngageAgain;
        this.edited = true;
        this.updatedAt = ts;
    }

    public void markLocked() {
        this.locked = true;
    }

    public boolean isCurrentlyEditable(Instant now) {
        return this.submitted
                && !this.locked
                && this.editableUntil != null
                && now.isBefore(this.editableUntil);
    }

    public boolean isEffectivelyLocked(Instant now) {
        return this.submitted && !isCurrentlyEditable(now);
    }

    private void validateRating(int ratingValue) {
        if (ratingValue < 1 || ratingValue > 5) {
            throw new BadRequestException("Star rating must be between 1 and 5.");
        }
    }

    public Long getId() {
        return id;
    }

    public HireContract getContract() {
        return contract;
    }

    public User getReviewer() {
        return reviewer;
    }

    public User getReviewee() {
        return reviewee;
    }

    public Role getReviewerRole() {
        return reviewerRole;
    }

    public Integer getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Boolean getWouldEngageAgain() {
        return wouldEngageAgain;
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public boolean isEdited() {
        return edited;
    }

    public boolean isLocked() {
        return locked;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getEditableUntil() {
        return editableUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
