package com.carebridge.user.dto;

import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.user.Role;
import com.carebridge.user.User;

import java.time.Instant;

public record AdminUserSummaryResponse(
        Long id,
        String fullName,
        String mobileNumber,
        String email,
        String address,
        Role role,
        boolean active,
        boolean caretakerApproved,
        Boolean caretakerAvailable,
        String primaryQualification,
        Instant createdAt
) {
    public static AdminUserSummaryResponse from(User user, CaretakerProfile profile) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getMobileNumber(),
                user.getEmail(),
                user.getAddress(),
                user.getRole(),
                user.isActive(),
                profile != null,
                profile != null ? profile.isAvailable() : null,
                profile != null ? profile.getPrimaryQualification() : null,
                user.getCreatedAt()
        );
    }
}
