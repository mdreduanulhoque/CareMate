package com.carebridge.caretaker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CaretakerApplicationRepository extends JpaRepository<CaretakerApplication, Long> {

    List<CaretakerApplication> findByUserIdOrderByApplicationNumberDesc(Long userId);

    boolean existsByUserIdAndStatus(Long userId, VerificationStatus status);

    @Query("SELECT COALESCE(MAX(a.applicationNumber), 0) FROM CaretakerApplication a WHERE a.user.id = :userId")
    int findMaxApplicationNumberByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT a FROM CaretakerApplication a
            JOIN FETCH a.user
            LEFT JOIN FETCH a.reviewedByAdmin
            WHERE (:status IS NULL OR a.status = :status)
            ORDER BY a.submittedAt DESC
            """)
    List<CaretakerApplication> findAllWithUsersByStatus(@Param("status") VerificationStatus status);

    @Query("""
            SELECT a FROM CaretakerApplication a
            JOIN FETCH a.user
            LEFT JOIN FETCH a.reviewedByAdmin
            WHERE a.id = :id
            """)
    Optional<CaretakerApplication> findByIdWithUsers(@Param("id") Long id);

    @Query("""
            SELECT a FROM CaretakerApplication a
            JOIN FETCH a.user
            LEFT JOIN FETCH a.reviewedByAdmin
            WHERE a.user.id = :userId
            ORDER BY a.applicationNumber DESC
            """)
    List<CaretakerApplication> findUserHistoryWithAdmin(@Param("userId") Long userId);
}
