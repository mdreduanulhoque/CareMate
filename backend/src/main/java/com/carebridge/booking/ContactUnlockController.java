package com.carebridge.booking;

import com.carebridge.booking.dto.ContactUnlockResponse;
import com.carebridge.security.CareBridgeUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Click-to-reveal contact unlock endpoints (Customer-only per Section 2.4 RBAC).
 */
@RestController
@RequestMapping("/api/contacts")
@PreAuthorize("hasRole('CUSTOMER')")
public class ContactUnlockController {

    private final ContactUnlockService contactUnlockService;

    public ContactUnlockController(ContactUnlockService contactUnlockService) {
        this.contactUnlockService = contactUnlockService;
    }

    @PostMapping("/unlock/{caretakerUserId}")
    public ResponseEntity<ContactUnlockResponse> unlockContact(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long caretakerUserId
    ) {
        return ResponseEntity.ok(contactUnlockService.unlockContact(principal.getUserId(), caretakerUserId));
    }

    @GetMapping("/unlock/{caretakerUserId}")
    public ResponseEntity<ContactUnlockResponse> getUnlockedContact(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long caretakerUserId
    ) {
        return ResponseEntity.ok(contactUnlockService.getUnlockedContact(principal.getUserId(), caretakerUserId));
    }
}
