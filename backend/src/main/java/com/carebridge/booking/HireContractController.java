package com.carebridge.booking;

import com.carebridge.booking.dto.CaretakerDayScheduleResponse;
import com.carebridge.booking.dto.ContractActionReasonRequest;
import com.carebridge.booking.dto.CreateHireRequest;
import com.carebridge.booking.dto.DeclineHireRequest;
import com.carebridge.booking.dto.HireContractResponse;
import com.carebridge.security.CareBridgeUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/hires")
public class HireContractController {

    private final BookingConcurrencyCoordinator bookingCoordinator;

    public HireContractController(BookingConcurrencyCoordinator bookingCoordinator) {
        this.bookingCoordinator = bookingCoordinator;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<HireContractResponse> createHireRequest(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @Valid @RequestBody CreateHireRequest request
    ) {
        HireContractResponse created = bookingCoordinator.createHireRequest(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER', 'ADMIN')")
    public ResponseEntity<List<HireContractResponse>> listHires(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @RequestParam(name = "status", required = false) List<ContractStatus> statuses
    ) {
        Set<ContractStatus> filter = (statuses == null || statuses.isEmpty())
                ? null
                : EnumSet.copyOf(statuses);
        return ResponseEntity.ok(bookingCoordinator.listMyHires(principal, filter));
    }

    @GetMapping("/{contractId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER', 'ADMIN')")
    public ResponseEntity<HireContractResponse> getHireDetail(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId
    ) {
        return ResponseEntity.ok(bookingCoordinator.getHireDetail(principal, contractId));
    }

    @GetMapping("/caretaker/{caretakerUserId}/schedule")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER', 'ADMIN')")
    public ResponseEntity<CaretakerDayScheduleResponse> getCaretakerDaySchedule(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long caretakerUserId,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dhakaDate
    ) {
        return ResponseEntity.ok(
                bookingCoordinator.getCaretakerDaySchedule(principal, caretakerUserId, dhakaDate)
        );
    }

    @PostMapping("/{contractId}/accept")
    @PreAuthorize("hasRole('CARETAKER')")
    public ResponseEntity<HireContractResponse> acceptHireRequest(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId
    ) {
        return ResponseEntity.ok(bookingCoordinator.acceptHireRequest(principal, contractId));
    }

    @PostMapping("/{contractId}/decline")
    @PreAuthorize("hasRole('CARETAKER')")
    public ResponseEntity<HireContractResponse> declineHireRequest(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId,
            @Valid @RequestBody(required = false) DeclineHireRequest request
    ) {
        return ResponseEntity.ok(bookingCoordinator.declineHireRequest(principal, contractId, request));
    }

    @PostMapping("/{contractId}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER', 'ADMIN')")
    public ResponseEntity<HireContractResponse> cancelContract(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId,
            @Valid @RequestBody ContractActionReasonRequest request
    ) {
        return ResponseEntity.ok(bookingCoordinator.cancelContract(principal, contractId, request));
    }

    @PostMapping("/{contractId}/end-early")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER', 'ADMIN')")
    public ResponseEntity<HireContractResponse> endContractEarly(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId,
            @Valid @RequestBody ContractActionReasonRequest request
    ) {
        return ResponseEntity.ok(bookingCoordinator.endContractEarly(principal, contractId, request));
    }
}
