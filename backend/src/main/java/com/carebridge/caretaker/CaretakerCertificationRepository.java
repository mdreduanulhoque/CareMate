package com.carebridge.caretaker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CaretakerCertificationRepository extends JpaRepository<CaretakerCertification, Long> {

    List<CaretakerCertification> findByCaretakerProfileIdOrderBySubmittedAtDesc(Long caretakerProfileId);

    List<CaretakerCertification> findByCaretakerProfileIdAndVerificationStatusOrderByReviewedAtDesc(
            Long caretakerProfileId,
            VerificationStatus verificationStatus
    );

    List<CaretakerCertification> findByCaretakerProfileIdInAndVerificationStatusOrderByReviewedAtDesc(
            Collection<Long> caretakerProfileIds,
            VerificationStatus verificationStatus
    );

    @Query("""
            SELECT c FROM CaretakerCertification c
            JOIN FETCH c.caretakerProfile p
            JOIN FETCH p.user
            LEFT JOIN FETCH c.reviewedByAdmin
            WHERE (:status IS NULL OR c.verificationStatus = :status)
            ORDER BY c.submittedAt DESC
            """)
    List<CaretakerCertification> findAllWithCaretakerByStatus(@Param("status") VerificationStatus status);

    @Query("""
            SELECT c FROM CaretakerCertification c
            JOIN FETCH c.caretakerProfile p
            JOIN FETCH p.user
            LEFT JOIN FETCH c.reviewedByAdmin
            WHERE c.id = :id
            """)
    Optional<CaretakerCertification> findByIdWithCaretaker(@Param("id") Long id);
}
