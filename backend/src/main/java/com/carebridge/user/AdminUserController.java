package com.carebridge.user;

import com.carebridge.caretaker.dto.AvailabilityUpdateRequest;
import com.carebridge.security.CareBridgeUserPrincipal;
import com.carebridge.user.dto.AdminUserSummaryResponse;
import com.carebridge.user.dto.UpdateUserStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserSummaryResponse>> listUsers(
            @RequestParam(name = "role", required = false) Role role,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return ResponseEntity.ok(adminUserService.listUsers(role, keyword));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<AdminUserSummaryResponse> updateUserStatus(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return ResponseEntity.ok(
                adminUserService.updateUserActiveStatus(principal.getUserId(), userId, request.active())
        );
    }

    @PatchMapping("/caretakers/{caretakerUserId}/availability")
    public ResponseEntity<AdminUserSummaryResponse> moderateCaretakerAvailability(
            @PathVariable Long caretakerUserId,
            @Valid @RequestBody AvailabilityUpdateRequest request
    ) {
        return ResponseEntity.ok(
                adminUserService.moderateCaretakerAvailability(caretakerUserId, request.available())
        );
    }
}
