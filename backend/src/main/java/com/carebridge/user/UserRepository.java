package com.carebridge.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByMobileNumber(String mobileNumber);

    boolean existsByMobileNumber(String mobileNumber);

    boolean existsByIdAndActiveTrue(Long id);

    List<User> findAllByOrderByCreatedAtDescIdDesc();
}
