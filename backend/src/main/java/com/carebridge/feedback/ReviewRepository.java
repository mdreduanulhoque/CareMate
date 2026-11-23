package com.carebridge.feedback;

import com.carebridge.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByContractIdAndReviewerId(Long contractId, Long reviewerId);

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.contract
            JOIN FETCH r.reviewer
            JOIN FETCH r.reviewee
            WHERE r.id = :id
            """)
    Optional<Review> findByIdWithUsersAndContract(@Param("id") Long id);

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.contract
            JOIN FETCH r.reviewer
            JOIN FETCH r.reviewee
            WHERE r.reviewer.id = :reviewerId AND r.submitted = FALSE
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Review> findPendingByReviewerId(@Param("reviewerId") Long reviewerId);

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.contract
            JOIN FETCH r.reviewer
            JOIN FETCH r.reviewee
            WHERE r.reviewer.id = :reviewerId
            ORDER BY r.submitted ASC, r.createdAt DESC, r.id DESC
            """)
    List<Review> findAllByReviewerId(@Param("reviewerId") Long reviewerId);

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.contract
            JOIN FETCH r.reviewer
            JOIN FETCH r.reviewee
            WHERE r.contract.id = :contractId
            ORDER BY r.id ASC
            """)
    List<Review> findAllByContractId(@Param("contractId") Long contractId);

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.contract
            JOIN FETCH r.reviewer
            JOIN FETCH r.reviewee
            WHERE r.contract.id IN :contractIds AND r.reviewer.id = :reviewerId
            """)
    List<Review> findByContractIdInAndReviewerId(
            @Param("contractIds") Collection<Long> contractIds,
            @Param("reviewerId") Long reviewerId
    );

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.contract
            JOIN FETCH r.reviewer
            JOIN FETCH r.reviewee
            WHERE r.reviewee.id = :revieweeId
              AND r.reviewerRole = :reviewerRole
              AND r.submitted = TRUE
            ORDER BY r.submittedAt DESC, r.id DESC
            """)
    List<Review> findSubmittedForRevieweeByReviewerRole(
            @Param("revieweeId") Long revieweeId,
            @Param("reviewerRole") Role reviewerRole
    );

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.contract
            JOIN FETCH r.reviewer
            JOIN FETCH r.reviewee
            WHERE r.submitted = TRUE
            ORDER BY r.submittedAt DESC, r.id DESC
            """)
    List<Review> findAllSubmittedWithUsers();

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.reviewee
            WHERE r.submitted = TRUE
              AND r.locked = FALSE
              AND r.editableUntil <= :now
            """)
    List<Review> findExpiredUnlockedSubmittedReviews(@Param("now") Instant now);

    @Query("""
            SELECT r.reviewee.id,
                   COUNT(r),
                   AVG(r.rating),
                   SUM(CASE WHEN r.wouldEngageAgain = TRUE THEN 1 ELSE 0 END)
            FROM Review r
            WHERE r.reviewee.id IN :revieweeIds
              AND r.reviewerRole = :reviewerRole
              AND r.submitted = TRUE
            GROUP BY r.reviewee.id
            """)
    List<Object[]> summarizeSubmittedByRevieweeIds(
            @Param("revieweeIds") Collection<Long> revieweeIds,
            @Param("reviewerRole") Role reviewerRole
    );
}
