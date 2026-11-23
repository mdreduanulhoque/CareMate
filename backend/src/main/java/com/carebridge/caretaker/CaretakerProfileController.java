package com.carebridge.caretaker;

import com.carebridge.caretaker.dto.AvailabilityUpdateRequest;
import com.carebridge.caretaker.dto.CaretakerOwnProfileResponse;
import com.carebridge.caretaker.dto.UpdateCaretakerProfileRequest;
import com.carebridge.security.CareBridgeUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/caretaker/profile")
@PreAuthorize("hasRole('CARETAKER')")
public class CaretakerProfileController {

    private final CaretakerProfileService profileService;

    public CaretakerProfileController(CaretakerProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ResponseEntity<CaretakerOwnProfileResponse> getMyProfile(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal
    ) {
        return ResponseEntity.ok(profileService.getMyProfile(principal.getUserId()));
    }

    @PutMapping("/me")
    public ResponseEntity<CaretakerOwnProfileResponse> updateMyProfile(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @Valid @RequestBody UpdateCaretakerProfileRequest request
    ) {
        return ResponseEntity.ok(profileService.updateMyProfile(principal.getUserId(), request));
    }

    @PatchMapping("/availability")
    public ResponseEntity<CaretakerOwnProfileResponse> updateAvailability(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @RequestBody AvailabilityUpdateRequest request
    ) {
        return ResponseEntity.ok(
                profileService.updateAvailability(principal.getUserId(), request.available())
        );
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CaretakerOwnProfileResponse> uploadAvatar(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(profileService.uploadAvatar(principal.getUserId(), file));
    }
}
