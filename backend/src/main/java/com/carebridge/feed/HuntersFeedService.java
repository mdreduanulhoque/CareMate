package com.carebridge.feed;

import com.carebridge.booking.ContactUnlockRepository;
import com.carebridge.caretaker.CaretakerCertification;
import com.carebridge.caretaker.CaretakerCertificationRepository;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.caretaker.RateType;
import com.carebridge.caretaker.VerificationStatus;
import com.carebridge.caretaker.dto.PublicCertificationBadge;
import com.carebridge.common.BadRequestException;
import com.carebridge.common.ForbiddenException;
import com.carebridge.common.NotFoundException;
import com.carebridge.feed.dto.FeedCaretakerCardResponse;
import com.carebridge.feed.dto.FeedCaretakerDetailResponse;
import com.carebridge.feed.dto.FeedPageResponse;
import com.carebridge.security.CareBridgeUserPrincipal;
import com.carebridge.user.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HuntersFeedService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CaretakerProfileRepository profileRepository;
    private final CaretakerCertificationRepository certificationRepository;
    private final ContactUnlockRepository contactUnlockRepository;

    public HuntersFeedService(
            CaretakerProfileRepository profileRepository,
            CaretakerCertificationRepository certificationRepository,
            ContactUnlockRepository contactUnlockRepository
    ) {
        this.profileRepository = profileRepository;
        this.certificationRepository = certificationRepository;
        this.contactUnlockRepository = contactUnlockRepository;
    }

    @Transactional(readOnly = true)
    public FeedPageResponse searchFeed(
            CareBridgeUserPrincipal requester,
            boolean availableOnly,
            RateType rateType,
            BigDecimal maxBudget,
            String area,
            BigDecimal minRating,
            String keyword,
            String sortKey,
            int page,
            int size
    ) {
        enforceFeedAccess(requester);
        validateNumericFilters(maxBudget, minRating);

        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, resolveSort(sortKey));

        Page<CaretakerProfile> resultPage = profileRepository.findAll(
                CaretakerFeedSpecifications.matchingFilters(
                        availableOnly,
                        rateType,
                        maxBudget,
                        area,
                        minRating,
                        keyword
                ),
                pageable
        );

        List<CaretakerProfile> profiles = resultPage.getContent();
        Map<Long, List<String>> approvedCertTitlesByProfileId = loadApprovedCertTitles(profiles);

        List<FeedCaretakerCardResponse> cards = profiles.stream()
                .map(profile -> FeedCaretakerCardResponse.from(
                        profile,
                        approvedCertTitlesByProfileId.getOrDefault(profile.getId(), List.of())
                ))
                .toList();

        return new FeedPageResponse(
                cards,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                resultPage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public FeedCaretakerDetailResponse getCaretakerPublicDetail(
            CareBridgeUserPrincipal requester,
            Long caretakerUserId
    ) {
        enforceFeedAccess(requester);

        CaretakerProfile profile = profileRepository.findActiveByUserIdWithUser(caretakerUserId)
                .orElseThrow(() -> new NotFoundException("Verified caretaker profile not found."));

        List<PublicCertificationBadge> approvedCerts = certificationRepository
                .findByCaretakerProfileIdAndVerificationStatusOrderByReviewedAtDesc(
                        profile.getId(),
                        VerificationStatus.APPROVED
                )
                .stream()
                .map(PublicCertificationBadge::from)
                .toList();

        boolean contactUnlocked = requester.getRole() == Role.CUSTOMER
                && contactUnlockRepository.existsByCustomerIdAndCaretakerId(
                        requester.getUserId(),
                        profile.getUser().getId()
                );

        return FeedCaretakerDetailResponse.from(profile, approvedCerts, contactUnlocked);
    }

    /**
     * Enforces Section 2.4 RBAC matrix: Customers, Admins, and Approved Caretakers (read-only)
     * may view the Hunters Feed; Unverified Caretakers are blocked.
     */
    private void enforceFeedAccess(CareBridgeUserPrincipal requester) {
        if (requester.getRole() == Role.CARETAKER
                && !profileRepository.existsByUserId(requester.getUserId())) {
            throw new ForbiddenException(
                    "Unverified caretaker accounts cannot browse the Hunters Feed until Admin approval."
            );
        }
    }

    private void validateNumericFilters(BigDecimal maxBudget, BigDecimal minRating) {
        if (maxBudget != null && maxBudget.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Maximum budget must be greater than zero.");
        }
        if (minRating != null
                && (minRating.compareTo(BigDecimal.ZERO) < 0 || minRating.compareTo(new BigDecimal("5.0")) > 0)) {
            throw new BadRequestException("Minimum rating must be between 0 and 5.");
        }
    }

    private Sort resolveSort(String sortKey) {
        if (sortKey == null || sortKey.isBlank()) {
            return Sort.by(
                    Sort.Order.desc("avgRating"),
                    Sort.Order.desc("completedHiresCount"),
                    Sort.Order.desc("id")
            );
        }
        return switch (sortKey) {
            case "rate_asc" -> Sort.by(
                    Sort.Order.asc("rateAmount").nullsLast(),
                    Sort.Order.desc("avgRating")
            );
            case "rate_desc" -> Sort.by(
                    Sort.Order.desc("rateAmount").nullsLast(),
                    Sort.Order.desc("avgRating")
            );
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            default -> Sort.by(
                    Sort.Order.desc("avgRating"),
                    Sort.Order.desc("completedHiresCount"),
                    Sort.Order.desc("id")
            );
        };
    }

    private Map<Long, List<String>> loadApprovedCertTitles(List<CaretakerProfile> profiles) {
        if (profiles.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> profileIds = profiles.stream().map(CaretakerProfile::getId).toList();
        return certificationRepository
                .findByCaretakerProfileIdInAndVerificationStatusOrderByReviewedAtDesc(
                        profileIds,
                        VerificationStatus.APPROVED
                )
                .stream()
                .collect(Collectors.groupingBy(
                        cert -> cert.getCaretakerProfile().getId(),
                        Collectors.mapping(CaretakerCertification::getTitle, Collectors.toList())
                ));
    }
}
