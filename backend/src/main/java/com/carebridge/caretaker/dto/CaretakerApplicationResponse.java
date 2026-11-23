package com.carebridge.caretaker.dto;

import com.carebridge.caretaker.CaretakerApplication;
import com.carebridge.caretaker.VerificationStatus;

import java.time.Instant;
import java.time.LocalDate;

public record CaretakerApplicationResponse(
        Long id,
        int applicationNumber,
        LocalDate dob,
        String qualification,
        String experience,
        String documentsDriveLink,
        Boolean driveLinkReachable,
        String driveCheckNote,
        VerificationStatus status,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt
) {
    public static CaretakerApplicationResponse from(CaretakerApplication application) {
        return new CaretakerApplicationResponse(
                application.getId(),
                application.getApplicationNumber(),
                application.getDob(),
                application.getQualification(),
                application.getExperience(),
                application.getDocumentsDriveLink(),
                application.getDriveLinkReachable(),
                application.getDriveCheckNote(),
                application.getStatus(),
                application.getRejectionReason(),
                application.getSubmittedAt(),
                application.getReviewedAt()
        );
    }
}
