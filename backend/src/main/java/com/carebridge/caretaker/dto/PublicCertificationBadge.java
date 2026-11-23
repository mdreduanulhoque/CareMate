package com.carebridge.caretaker.dto;

import com.carebridge.caretaker.CaretakerCertification;

import java.time.Instant;

public record PublicCertificationBadge(
        Long id,
        String title,
        String issuingOrganization,
        Instant verifiedAt
) {
    public static PublicCertificationBadge from(CaretakerCertification certification) {
        return new PublicCertificationBadge(
                certification.getId(),
                certification.getTitle(),
                certification.getIssuingOrganization(),
                certification.getReviewedAt()
        );
    }
}
