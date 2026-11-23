package com.carebridge.auth.dto;

import com.carebridge.user.Role;
import com.carebridge.user.User;

import java.time.Instant;

public record UserSummaryResponse(
        Long id,
        String fullName,
        String mobileNumber,
        String email,
        String address,
        Role role,
        boolean active,
        boolean caretakerApproved,
        Instant createdAt
) {
    public static UserSummaryResponse from(User user, boolean caretakerApproved) {
        return new UserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getMobileNumber(),
                user.getEmail(),
                user.getAddress(),
                user.getRole(),
                user.isActive(),
                caretakerApproved,
                user.getCreatedAt()
        );
    }
}
