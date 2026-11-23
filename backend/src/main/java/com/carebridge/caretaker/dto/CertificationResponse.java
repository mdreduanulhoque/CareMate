package com.carebridge.caretaker.dto;

import com.carebridge.caretaker.CaretakerCertification;
import com.carebridge.caretaker.VerificationStatus;

import java.time.Instant;

public record CertificationResponse(
        Long id,
        String title,
        String issuingOrganization,
        String documentDriveLink,
        Boolean driveLinkReachable,
        String driveCheckNote,
        VerificationStatus verificationStatus,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt
) {
    public static CertificationResponse from(CaretakerCertification cert) {
        return new CertificationResponse(
                cert.getId(),
                cert.getTitle(),
                cert.getIssuingOrganization(),
                cert.getDocumentDriveLink(),
                cert.getDriveLinkReachable(),
                cert.getDriveCheckNote(),
                cert.getVerificationStatus(),
                cert.getRejectionReason(),
                cert.getSubmittedAt(),
                cert.getReviewedAt()
        );
    }
}
