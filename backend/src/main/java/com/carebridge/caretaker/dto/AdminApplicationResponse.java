package com.carebridge.caretaker.dto;

import com.carebridge.caretaker.CaretakerApplication;
import com.carebridge.caretaker.VerificationStatus;
import com.carebridge.user.User;

import java.time.Instant;
import java.time.LocalDate;

public record AdminApplicationResponse(
        Long id,
        Long userId,
        String applicantFullName,
        String applicantMobileNumber,
        String applicantEmail,
        String applicantAddress,
        int applicationNumber,
        LocalDate dob,
        String qualification,
        String experience,
        String documentsDriveLink,
        Boolean driveLinkReachable,
        String driveCheckNote,
        VerificationStatus status,
        String rejectionReason,
        String reviewedByAdminName,
        Instant submittedAt,
        Instant reviewedAt,
        int priorAttemptsCount
) {
    public static AdminApplicationResponse from(CaretakerApplication app) {
        User applicant = app.getUser();
        User reviewer = app.getReviewedByAdmin();
        return new AdminApplicationResponse(
                app.getId(),
                applicant.getId(),
                applicant.getFullName(),
                applicant.getMobileNumber(),
                applicant.getEmail(),
                applicant.getAddress(),
                app.getApplicationNumber(),
                app.getDob(),
                app.getQualification(),
                app.getExperience(),
                app.getDocumentsDriveLink(),
                app.getDriveLinkReachable(),
                app.getDriveCheckNote(),
                app.getStatus(),
                app.getRejectionReason(),
                reviewer != null ? reviewer.getFullName() : null,
                app.getSubmittedAt(),
                app.getReviewedAt(),
                Math.max(0, app.getApplicationNumber() - 1)
        );
    }
}
