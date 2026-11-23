package com.carebridge.auth;

import com.carebridge.auth.dto.AuthResponse;
import com.carebridge.auth.dto.CaretakerRegisterRequest;
import com.carebridge.auth.dto.CustomerRegisterRequest;
import com.carebridge.auth.dto.LoginRequest;
import com.carebridge.auth.dto.UserSummaryResponse;
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

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register/customer")
    public ResponseEntity<AuthResponse> registerCustomer(
            @Valid @RequestBody CustomerRegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerCustomer(request));
    }

    @PostMapping("/auth/register/caretaker")
    public ResponseEntity<AuthResponse> registerCaretaker(
            @Valid @RequestBody CaretakerRegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerCaretaker(request));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<UserSummaryResponse> currentUser(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal
    ) {
        return ResponseEntity.ok(authService.getCurrentUser(principal.getUserId()));
    }

    @GetMapping("/admin/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> adminStatus(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal
    ) {
        return ResponseEntity.ok(Map.of(
                "status", "AUTHORIZED",
                "adminId", principal.getUserId(),
                "adminName", principal.getFullName()
        ));
    }
}
