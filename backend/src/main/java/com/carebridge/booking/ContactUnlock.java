package com.carebridge.booking;

import com.carebridge.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "contact_unlocks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_contact_unlocks_pair",
                columnNames = {"customer_id", "caretaker_id"}
        )
)
public class ContactUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caretaker_id", nullable = false)
    private User caretaker;

    @Column(name = "unlocked_at", nullable = false, updatable = false)
    private Instant unlockedAt;

    protected ContactUnlock() {
    }

    public ContactUnlock(User customer, User caretaker) {
        this.customer = customer;
        this.caretaker = caretaker;
    }

    @PrePersist
    void onCreate() {
        if (this.unlockedAt == null) {
            this.unlockedAt = Instant.now();
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

    public Instant getUnlockedAt() {
        return unlockedAt;
    }
}
