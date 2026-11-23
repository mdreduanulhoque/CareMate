package com.carebridge.caretaker;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "caretaker_profiles")
public class CaretakerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "primary_qualification", nullable = false, length = 255)
    private String primaryQualification;

    @Column(name = "location_area", nullable = false, length = 160)
    private String locationArea;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "bio", length = 2000)
    private String bio;

    @Column(name = "rate_amount", precision = 10, scale = 2)
    private BigDecimal rateAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 20)
    private RateType rateType = RateType.DAILY;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "completed_hires_count", nullable = false)
    private int completedHiresCount = 0;

    @Column(name = "hire_again_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal hireAgainPercentage = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CaretakerProfile() {
    }

    public CaretakerProfile(User user, String primaryQualification, String locationArea) {
        this.user = user;
        this.primaryQualification = primaryQualification;
        this.locationArea = locationArea;
        this.rateType = RateType.DAILY;
        this.available = true;
        this.avgRating = BigDecimal.ZERO;
        this.completedHiresCount = 0;
        this.hireAgainPercentage = BigDecimal.ZERO;
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

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getPrimaryQualification() {
        return primaryQualification;
    }

    public void setPrimaryQualification(String primaryQualification) {
        this.primaryQualification = primaryQualification;
    }

    public String getLocationArea() {
        return locationArea;
    }

    public void setLocationArea(String locationArea) {
        this.locationArea = locationArea;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public BigDecimal getRateAmount() {
        return rateAmount;
    }

    public void setRateAmount(BigDecimal rateAmount) {
        this.rateAmount = rateAmount;
    }

    public RateType getRateType() {
        return rateType;
    }

    public void setRateType(RateType rateType) {
        this.rateType = rateType;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public int getCompletedHiresCount() {
        return completedHiresCount;
    }

    public BigDecimal getHireAgainPercentage() {
        return hireAgainPercentage;
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
