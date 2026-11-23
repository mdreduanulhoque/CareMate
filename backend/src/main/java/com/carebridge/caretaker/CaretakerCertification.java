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

@Entity
@Table(name = "caretaker_certifications")
public class CaretakerCertification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caretaker_profile_id", nullable = false)
    private CaretakerProfile caretakerProfile;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "issuing_organization", nullable = false, length = 180)
    private String issuingOrganization;

    @Column(name = "document_drive_link", nullable = false, length = 1000)
    private String documentDriveLink;

    @Column(name = "drive_link_reachable")
    private Boolean driveLinkReachable;

    @Column(name = "drive_check_note", length = 255)
    private String driveCheckNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id")
    private User reviewedByAdmin;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected CaretakerCertification() {
    }

    public CaretakerCertification(
            CaretakerProfile caretakerProfile,
            String title,
            String issuingOrganization,
            String documentDriveLink
    ) {
        this.caretakerProfile = caretakerProfile;
        this.title = title;
        this.issuingOrganization = issuingOrganization;
        this.documentDriveLink = documentDriveLink;
        this.verificationStatus = VerificationStatus.PENDING;
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
        this.verificationStatus = VerificationStatus.APPROVED;
        this.rejectionReason = null;
        this.reviewedByAdmin = admin;
        this.reviewedAt = Instant.now();
    }

    public void reject(User admin, String reason) {
        this.verificationStatus = VerificationStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedByAdmin = admin;
        this.reviewedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CaretakerProfile getCaretakerProfile() {
        return caretakerProfile;
    }

    public String getTitle() {
        return title;
    }

    public String getIssuingOrganization() {
        return issuingOrganization;
    }

    public String getDocumentDriveLink() {
        return documentDriveLink;
    }

    public Boolean getDriveLinkReachable() {
        return driveLinkReachable;
    }

    public String getDriveCheckNote() {
        return driveCheckNote;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
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
