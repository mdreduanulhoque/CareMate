package com.carebridge.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Mobile number is required.")
        @Size(max = 20, message = "Mobile number is too long.")
        String mobileNumber,

        @NotBlank(message = "Password is required.")
        @Size(max = 72, message = "Password is too long.")
        String password
) {
}
