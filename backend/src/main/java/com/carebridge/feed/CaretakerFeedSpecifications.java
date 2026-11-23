package com.carebridge.feed;

import com.carebridge.caretaker.CaretakerCertification;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.RateType;
import com.carebridge.caretaker.VerificationStatus;
import com.carebridge.user.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CaretakerFeedSpecifications {

    private CaretakerFeedSpecifications() {
    }

    public static Specification<CaretakerProfile> matchingFilters(
            boolean availableOnly,
            RateType rateType,
            BigDecimal maxBudget,
            String area,
            BigDecimal minRating,
            String keyword
    ) {
        return (root, query, cb) -> {
            Join<CaretakerProfile, User> userJoin = root.join("user", JoinType.INNER);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(userJoin.get("active")));

            if (availableOnly) {
                predicates.add(cb.isTrue(root.get("available")));
            }

            if (rateType != null) {
                predicates.add(cb.equal(root.get("rateType"), rateType));
            }

            if (maxBudget != null) {
                predicates.add(cb.isNotNull(root.get("rateAmount")));
                predicates.add(cb.lessThanOrEqualTo(root.get("rateAmount"), maxBudget));
            }

            if (area != null && !area.isBlank()) {
                String pattern = likePattern(area);
                predicates.add(cb.like(cb.lower(root.get("locationArea")), pattern, '\\'));
            }

            if (minRating != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("avgRating"), minRating));
            }

            if (keyword != null && !keyword.isBlank()) {
                String pattern = likePattern(keyword);

                Subquery<Long> approvedCertMatch = query.subquery(Long.class);
                Root<CaretakerCertification> certRoot = approvedCertMatch.from(CaretakerCertification.class);
                approvedCertMatch.select(certRoot.get("id")).where(
                        cb.equal(certRoot.get("caretakerProfile"), root),
                        cb.equal(certRoot.get("verificationStatus"), VerificationStatus.APPROVED),
                        cb.like(cb.lower(certRoot.get("title")), pattern, '\\')
                );

                predicates.add(cb.or(
                        cb.like(cb.lower(userJoin.get("fullName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("primaryQualification")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("bio"), "")), pattern, '\\'),
                        cb.like(cb.lower(root.get("locationArea")), pattern, '\\'),
                        cb.exists(approvedCertMatch)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String likePattern(String raw) {
        String escaped = raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
