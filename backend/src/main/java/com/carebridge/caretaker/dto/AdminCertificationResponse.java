package com.carebridge.caretaker.dto;

import com.carebridge.caretaker.CaretakerCertification;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.VerificationStatus;
import com.carebridge.user.User;

import java.time.Instant;

public record AdminCertificationResponse(
        Long id,
        Long caretakerProfileId,
        Long caretakerUserId,
        String caretakerFullName,
        String caretakerMobileNumber,
        String caretakerPrimaryQualification,
        String title,
        String issuingOrganization,
        String documentDriveLink,
        Boolean driveLinkReachable,
        String driveCheckNote,
        VerificationStatus verificationStatus,
        String rejectionReason,
        String reviewedByAdminName,
        Instant submittedAt,
        Instant reviewedAt
) {
    public static AdminCertificationResponse from(CaretakerCertification cert) {
        CaretakerProfile profile = cert.getCaretakerProfile();
        User caretaker = profile.getUser();
        User reviewer = cert.getReviewedByAdmin();
        return new AdminCertificationResponse(
                cert.getId(),
                profile.getId(),
                caretaker.getId(),
                caretaker.getFullName(),
                caretaker.getMobileNumber(),
                profile.getPrimaryQualification(),
                cert.getTitle(),
                cert.getIssuingOrganization(),
                cert.getDocumentDriveLink(),
                cert.getDriveLinkReachable(),
                cert.getDriveCheckNote(),
                cert.getVerificationStatus(),
                cert.getRejectionReason(),
                reviewer != null ? reviewer.getFullName() : null,
                cert.getSubmittedAt(),
                cert.getReviewedAt()
        );
    }
}
