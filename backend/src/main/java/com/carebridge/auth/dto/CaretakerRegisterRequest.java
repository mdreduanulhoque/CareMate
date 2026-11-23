package com.carebridge.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Caretaker account creation payload (credential application submission is attached in Phase 2).
 */
public record CaretakerRegisterRequest(
        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters.")
        String fullName,

        @NotBlank(message = "Mobile number is required.")
        @Size(max = 20, message = "Mobile number is too long.")
        String mobileNumber,

        @NotBlank(message = "Address is required.")
        @Size(min = 3, max = 255, message = "Address must be between 3 and 255 characters.")
        String address,

        @Email(message = "Please enter a valid email address.")
        @Size(max = 160, message = "Email must be 160 characters or fewer.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
        String password
) {
}
