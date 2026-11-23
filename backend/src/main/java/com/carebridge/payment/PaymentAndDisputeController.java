package com.carebridge.payment;

import com.carebridge.booking.dto.HireContractResponse;
import com.carebridge.payment.dto.CustomerPaymentDoneRequest;
import com.carebridge.payment.dto.DisputeStatementRequest;
import com.carebridge.payment.dto.PaymentDisputeResponse;
import com.carebridge.payment.dto.RefusePaymentRequest;
import com.carebridge.payment.dto.ResolveDisputeRequest;
import com.carebridge.security.CareBridgeUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PaymentAndDisputeController {

    private final PaymentAndDisputeService paymentAndDisputeService;

    public PaymentAndDisputeController(PaymentAndDisputeService paymentAndDisputeService) {
        this.paymentAndDisputeService = paymentAndDisputeService;
    }

    @PostMapping("/api/payments/contracts/{contractId}/customer-done")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<HireContractResponse> markCustomerPaymentDone(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId,
            @Valid @RequestBody(required = false) CustomerPaymentDoneRequest request
    ) {
        return ResponseEntity.ok(
                paymentAndDisputeService.markCustomerPaymentDone(principal, contractId, request)
        );
    }

    @PostMapping("/api/payments/contracts/{contractId}/caretaker-confirm")
    @PreAuthorize("hasRole('CARETAKER')")
    public ResponseEntity<HireContractResponse> confirmCaretakerPaymentReceived(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId
    ) {
        return ResponseEntity.ok(
                paymentAndDisputeService.confirmCaretakerPaymentReceived(principal, contractId)
        );
    }

    @PostMapping("/api/payments/contracts/{contractId}/caretaker-refuse")
    @PreAuthorize("hasRole('CARETAKER')")
    public ResponseEntity<PaymentDisputeResponse> refuseCaretakerPayment(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId,
            @Valid @RequestBody RefusePaymentRequest request
    ) {
        return ResponseEntity.ok(
                paymentAndDisputeService.refuseCaretakerPaymentAndOpenDispute(principal, contractId, request)
        );
    }

    @GetMapping("/api/disputes")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER', 'ADMIN')")
    public ResponseEntity<List<PaymentDisputeResponse>> listDisputes(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @RequestParam(name = "status", required = false) DisputeStatus status
    ) {
        return ResponseEntity.ok(paymentAndDisputeService.listDisputes(principal, status));
    }

    @GetMapping("/api/disputes/contract/{contractId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER', 'ADMIN')")
    public ResponseEntity<PaymentDisputeResponse> getDisputeByContract(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId
    ) {
        return ResponseEntity.ok(paymentAndDisputeService.getDisputeByContract(principal, contractId));
    }

    @PutMapping("/api/disputes/contract/{contractId}/statement")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER')")
    public ResponseEntity<PaymentDisputeResponse> updateStatement(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId,
            @Valid @RequestBody DisputeStatementRequest request
    ) {
        return ResponseEntity.ok(
                paymentAndDisputeService.updateParticipantStatement(principal, contractId, request)
        );
    }

    @PostMapping("/api/disputes/contract/{contractId}/join")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentDisputeResponse> joinDisputeAsAdmin(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId
    ) {
        return ResponseEntity.ok(paymentAndDisputeService.joinDisputeAsAdmin(principal, contractId));
    }

    @PostMapping("/api/disputes/contract/{contractId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentDisputeResponse> resolveDispute(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId,
            @Valid @RequestBody ResolveDisputeRequest request
    ) {
        return ResponseEntity.ok(
                paymentAndDisputeService.resolveDispute(principal, contractId, request)
        );
    }
}
