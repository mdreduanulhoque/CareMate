package com.carebridge.caretaker;

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

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "caretaker_applications")
public class CaretakerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "application_number", nullable = false)
    private int applicationNumber;

    @Column(name = "dob", nullable = false)
    private LocalDate dob;

    @Column(name = "qualification", nullable = false, length = 255)
    private String qualification;

    @Column(name = "experience", length = 1500)
    private String experience;

    @Column(name = "documents_drive_link", nullable = false, length = 1000)
    private String documentsDriveLink;

    @Column(name = "drive_link_reachable")
    private Boolean driveLinkReachable;

    @Column(name = "drive_check_note", length = 255)
    private String driveCheckNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VerificationStatus status;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id")
    private User reviewedByAdmin;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected CaretakerApplication() {
    }

    public CaretakerApplication(
            User user,
            int applicationNumber,
            LocalDate dob,
            String qualification,
            String experience,
            String documentsDriveLink
    ) {
        this.user = user;
        this.applicationNumber = applicationNumber;
        this.dob = dob;
        this.qualification = qualification;
        this.experience = experience;
        this.documentsDriveLink = documentsDriveLink;
        this.status = VerificationStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        if (this.submittedAt == null) {
            this.submittedAt = Instant.now();
        }
    }

    public void markDriveCheckResult(boolean reachable, String note) {
        this.driveLinkReachable = reachable;
        this.driveCheckNote = note;
    }

    public void resetDriveCheckPending() {
        this.driveLinkReachable = null;
        this.driveCheckNote = null;
    }

    public void approve(User admin) {
        this.status = VerificationStatus.APPROVED;
        this.rejectionReason = null;
        this.reviewedByAdmin = admin;
        this.reviewedAt = Instant.now();
    }

    public void reject(User admin, String reason) {
        this.status = VerificationStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedByAdmin = admin;
        this.reviewedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public int getApplicationNumber() {
        return applicationNumber;
    }

    public LocalDate getDob() {
        return dob;
    }

    public String getQualification() {
        return qualification;
    }

    public String getExperience() {
        return experience;
    }

    public String getDocumentsDriveLink() {
        return documentsDriveLink;
    }

    public Boolean getDriveLinkReachable() {
        return driveLinkReachable;
    }

    public String getDriveCheckNote() {
        return driveCheckNote;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public User getReviewedByAdmin() {
        return reviewedByAdmin;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
