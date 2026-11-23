package com.carebridge.caretaker;

import com.carebridge.caretaker.dto.CaretakerApplicationRequest;
import com.carebridge.caretaker.dto.CaretakerApplicationResponse;
import com.carebridge.caretaker.dto.CaretakerApplicationStatusResponse;
import com.carebridge.caretaker.dto.CertificationResponse;
import com.carebridge.caretaker.dto.NewCertificationRequest;
import com.carebridge.security.CareBridgeUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/caretaker")
@PreAuthorize("hasRole('CARETAKER')")
public class CaretakerVerificationController {

    private final CaretakerVerificationService verificationService;

    public CaretakerVerificationController(CaretakerVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping("/applications/me")
    public ResponseEntity<CaretakerApplicationStatusResponse> getMyApplicationStatus(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal
    ) {
        return ResponseEntity.ok(verificationService.getMyApplicationStatus(principal.getUserId()));
    }

    @PostMapping("/applications")
    public ResponseEntity<CaretakerApplicationResponse> submitApplication(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @Valid @RequestBody CaretakerApplicationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(verificationService.submitApplication(principal.getUserId(), request));
    }

    @GetMapping("/certifications/me")
    public ResponseEntity<List<CertificationResponse>> getMyCertifications(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal
    ) {
        return ResponseEntity.ok(verificationService.getMyCertifications(principal.getUserId()));
    }

    @PostMapping("/certifications")
    public ResponseEntity<CertificationResponse> submitCertification(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @Valid @RequestBody NewCertificationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(verificationService.submitCertification(principal.getUserId(), request));
    }
}
