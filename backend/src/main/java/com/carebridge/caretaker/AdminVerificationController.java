package com.carebridge.caretaker;

import com.carebridge.caretaker.dto.AdminApplicationResponse;
import com.carebridge.caretaker.dto.AdminCertificationResponse;
import com.carebridge.caretaker.dto.RejectionDecisionRequest;
import com.carebridge.security.CareBridgeUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVerificationController {

    private final AdminVerificationService adminVerificationService;

    public AdminVerificationController(AdminVerificationService adminVerificationService) {
        this.adminVerificationService = adminVerificationService;
    }

    @GetMapping("/applications")
    public ResponseEntity<List<AdminApplicationResponse>> listApplications(
            @RequestParam(name = "status", required = false) VerificationStatus status
    ) {
        return ResponseEntity.ok(adminVerificationService.listApplications(status));
    }

    @GetMapping("/applications/user/{userId}/history")
    public ResponseEntity<List<AdminApplicationResponse>> getApplicantHistory(@PathVariable Long userId) {
        return ResponseEntity.ok(adminVerificationService.getApplicantHistory(userId));
    }

    @PostMapping("/applications/{applicationId}/approve")
    public ResponseEntity<AdminApplicationResponse> approveApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal CareBridgeUserPrincipal principal
    ) {
        return ResponseEntity.ok(
                adminVerificationService.approveApplication(applicationId, principal.getUserId())
        );
    }

    @PostMapping("/applications/{applicationId}/reject")
    public ResponseEntity<AdminApplicationResponse> rejectApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @Valid @RequestBody RejectionDecisionRequest request
    ) {
        return ResponseEntity.ok(
                adminVerificationService.rejectApplication(
                        applicationId,
                        principal.getUserId(),
                        request.rejectionReason()
                )
        );
    }

    @PostMapping("/applications/{applicationId}/recheck-drive")
    public ResponseEntity<AdminApplicationResponse> recheckApplicationDriveLink(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(adminVerificationService.recheckApplicationDriveLink(applicationId));
    }

    @GetMapping("/certifications")
    public ResponseEntity<List<AdminCertificationResponse>> listCertifications(
            @RequestParam(name = "status", required = false) VerificationStatus status
    ) {
        return ResponseEntity.ok(adminVerificationService.listCertifications(status));
    }

    @PostMapping("/certifications/{certificationId}/approve")
    public ResponseEntity<AdminCertificationResponse> approveCertification(
            @PathVariable Long certificationId,
            @AuthenticationPrincipal CareBridgeUserPrincipal principal
    ) {
        return ResponseEntity.ok(
                adminVerificationService.approveCertification(certificationId, principal.getUserId())
        );
    }

    @PostMapping("/certifications/{certificationId}/reject")
    public ResponseEntity<AdminCertificationResponse> rejectCertification(
            @PathVariable Long certificationId,
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @Valid @RequestBody RejectionDecisionRequest request
    ) {
        return ResponseEntity.ok(
                adminVerificationService.rejectCertification(
                        certificationId,
                        principal.getUserId(),
                        request.rejectionReason()
                )
        );
    }

    @PostMapping("/certifications/{certificationId}/recheck-drive")
    public ResponseEntity<AdminCertificationResponse> recheckCertificationDriveLink(
            @PathVariable Long certificationId
    ) {
        return ResponseEntity.ok(adminVerificationService.recheckCertificationDriveLink(certificationId));
    }
}
