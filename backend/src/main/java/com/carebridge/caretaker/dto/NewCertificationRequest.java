package com.carebridge.caretaker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewCertificationRequest(
        @NotBlank(message = "Certification title is required.")
        @Size(min = 2, max = 180, message = "Title must be between 2 and 180 characters.")
        String title,

        @NotBlank(message = "Issuing organization is required.")
        @Size(min = 2, max = 180, message = "Issuing organization must be between 2 and 180 characters.")
        String issuingOrganization,

        @NotBlank(message = "Google Drive document link is required.")
        @Size(max = 1000, message = "Google Drive link must be 1000 characters or fewer.")
        String documentDriveLink
) {
}
