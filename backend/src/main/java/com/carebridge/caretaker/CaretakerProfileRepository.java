package com.carebridge.caretaker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaretakerProfileRepository extends JpaRepository<CaretakerProfile, Long> {

    Optional<CaretakerProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
