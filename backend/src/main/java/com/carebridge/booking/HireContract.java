package com.carebridge.booking;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "hire_contracts")
public class HireContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caretaker_id", nullable = false)
    private User caretaker;

    @Column(name = "agreed_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal agreedRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 20)
    private ContractRateType rateType;

    @Column(name = "scheduled_start_time", nullable = false)
    private Instant scheduledStartTime;

    @Column(name = "scheduled_end_time", nullable = false)
    private Instant scheduledEndTime;

    @Column(name = "actual_end_time")
    private Instant actualEndTime;

    @Column(name = "cancellation_or_early_end_reason", length = 1000)
    private String cancellationOrEarlyEndReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by_id")
    private User initiatedBy;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ContractStatus status;

    @Column(name = "customer_payment_confirmed", nullable = false)
    private boolean customerPaymentConfirmed = false;

    @Column(name = "caretaker_payment_confirmed", nullable = false)
    private boolean caretakerPaymentConfirmed = false;

    @Column(name = "payment_reference_note", length = 500)
    private String paymentReferenceNote;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HireContract() {
    }

    public HireContract(
            User customer,
            User caretaker,
            BigDecimal agreedRate,
            ContractRateType rateType,
            Instant scheduledStartTime,
            Instant scheduledEndTime,
            String notes
    ) {
        this.customer = customer;
        this.caretaker = caretaker;
        this.agreedRate = agreedRate;
        this.rateType = rateType;
        this.scheduledStartTime = scheduledStartTime;
        this.scheduledEndTime = scheduledEndTime;
        this.notes = notes;
        this.status = ContractStatus.REQUESTED;
        this.customerPaymentConfirmed = false;
        this.caretakerPaymentConfirmed = false;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void accept(ContractStatus targetStatus) {
        this.status = targetStatus;
    }

    public void decline(User actor, String reason) {
        this.status = ContractStatus.DECLINED;
        this.initiatedBy = actor;
        this.cancellationOrEarlyEndReason = reason;
    }

    public void cancel(User actor, String reason) {
        this.status = ContractStatus.CANCELLED;
        this.initiatedBy = actor;
        this.cancellationOrEarlyEndReason = reason;
    }

    public void endEarly(User actor, String reason, Instant stoppedAt) {
        this.status = ContractStatus.COMPLETED_PENDING_SETTLEMENT;
        this.initiatedBy = actor;
        this.cancellationOrEarlyEndReason = reason;
        this.actualEndTime = stoppedAt;
    }

    public void activateFromScheduled() {
        this.status = ContractStatus.ACTIVE;
    }

    public void autoComplete(Instant completedAt) {
        this.status = ContractStatus.COMPLETED_PENDING_SETTLEMENT;
        if (this.actualEndTime == null) {
            this.actualEndTime = completedAt;
        }
    }

    public Long getId() {
        return id;
    }

    public User getCustomer() {
        return customer;
    }

    public User getCaretaker() {
        return caretaker;
    }

    public BigDecimal getAgreedRate() {
        return agreedRate;
    }

    public ContractRateType getRateType() {
        return rateType;
    }

    public Instant getScheduledStartTime() {
        return scheduledStartTime;
    }

    public Instant getScheduledEndTime() {
        return scheduledEndTime;
    }

    public Instant getActualEndTime() {
        return actualEndTime;
    }

    public String getCancellationOrEarlyEndReason() {
        return cancellationOrEarlyEndReason;
    }

    public User getInitiatedBy() {
        return initiatedBy;
    }

    public String getNotes() {
        return notes;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public boolean isCustomerPaymentConfirmed() {
        return customerPaymentConfirmed;
    }

    public boolean isCaretakerPaymentConfirmed() {
        return caretakerPaymentConfirmed;
    }

    public String getPaymentReferenceNote() {
        return paymentReferenceNote;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
