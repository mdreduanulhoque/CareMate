package com.carebridge.payment;

import com.carebridge.booking.HireContract;
import com.carebridge.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "payment_disputes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_payment_disputes_contract",
                columnNames = {"contract_id"}
        )
)
public class PaymentDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, unique = true)
    private HireContract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raised_by_id", nullable = false)
    private User raisedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_admin_id")
    private User assignedAdmin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DisputeStatus status;

    @Column(name = "customer_statement", length = 2000)
    private String customerStatement;

    @Column(name = "caretaker_statement", length = 2000)
    private String caretakerStatement;

    @Column(name = "admin_resolution_note", length = 2000)
    private String adminResolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected PaymentDispute() {
    }

    public PaymentDispute(
            HireContract contract,
            User raisedBy,
            String customerStatement,
            String caretakerStatement
    ) {
        this.contract = contract;
        this.raisedBy = raisedBy;
        this.status = DisputeStatus.OPEN;
        this.customerStatement = customerStatement;
        this.caretakerStatement = caretakerStatement;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public void assignAdminUnderReview(User admin) {
        if (this.assignedAdmin == null) {
            this.assignedAdmin = admin;
        }
        if (this.status == DisputeStatus.OPEN) {
            this.status = DisputeStatus.UNDER_REVIEW;
        }
    }

    public void updateCustomerStatement(String statement) {
        this.customerStatement = statement;
    }

    public void updateCaretakerStatement(String statement) {
        this.caretakerStatement = statement;
    }

    public void resolve(User admin, String resolutionNote, Instant now) {
        this.assignedAdmin = admin;
        this.adminResolutionNote = resolutionNote;
        this.status = DisputeStatus.RESOLVED;
        this.resolvedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public HireContract getContract() {
        return contract;
    }

    public User getRaisedBy() {
        return raisedBy;
    }

    public User getAssignedAdmin() {
        return assignedAdmin;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public String getCustomerStatement() {
        return customerStatement;
    }

    public String getCaretakerStatement() {
        return caretakerStatement;
    }

    public String getAdminResolutionNote() {
        return adminResolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
