package com.carebridge.caretaker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CaretakerApplicationRequest(
        @NotNull(message = "Date of birth is required.")
        @Past(message = "Date of birth must be in the past.")
        LocalDate dob,

        @NotBlank(message = "Relevant qualification is required.")
        @Size(min = 2, max = 255, message = "Qualification must be between 2 and 255 characters.")
        String qualification,

        @Size(max = 1500, message = "Experience summary must be 1500 characters or fewer.")
        String experience,

        @NotBlank(message = "Google Drive document link is required.")
        @Size(max = 1000, message = "Google Drive link must be 1000 characters or fewer.")
        String documentsDriveLink
) {
}
