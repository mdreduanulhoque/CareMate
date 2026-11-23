package com.carebridge.caretaker;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CaretakerProfileRepository
        extends JpaRepository<CaretakerProfile, Long>, JpaSpecificationExecutor<CaretakerProfile> {

    @Override
    @EntityGraph(attributePaths = "user")
    Page<CaretakerProfile> findAll(Specification<CaretakerProfile> spec, Pageable pageable);

    Optional<CaretakerProfile> findByUserId(Long userId);

    List<CaretakerProfile> findByUserIdIn(Collection<Long> userIds);

    boolean existsByUserId(Long userId);

    @Query("SELECT p FROM CaretakerProfile p JOIN FETCH p.user u WHERE u.id = :userId")
    Optional<CaretakerProfile> findByUserIdWithUser(@Param("userId") Long userId);

    @Query("SELECT p FROM CaretakerProfile p JOIN FETCH p.user u WHERE u.id = :userId AND u.active = true")
    Optional<CaretakerProfile> findActiveByUserIdWithUser(@Param("userId") Long userId);
}
