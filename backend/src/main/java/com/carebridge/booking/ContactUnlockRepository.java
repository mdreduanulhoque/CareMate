package com.carebridge.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContactUnlockRepository extends JpaRepository<ContactUnlock, Long> {

    boolean existsByCustomerIdAndCaretakerId(Long customerId, Long caretakerId);

    @Query("""
            SELECT cu FROM ContactUnlock cu
            JOIN FETCH cu.customer
            JOIN FETCH cu.caretaker
            WHERE cu.customer.id = :customerId AND cu.caretaker.id = :caretakerId
            """)
    Optional<ContactUnlock> findWithUsersByCustomerIdAndCaretakerId(
            @Param("customerId") Long customerId,
            @Param("caretakerId") Long caretakerId
    );

    @Query("""
            SELECT cu FROM ContactUnlock cu
            JOIN FETCH cu.customer
            JOIN FETCH cu.caretaker
            WHERE cu.customer.id = :customerId
            ORDER BY cu.unlockedAt DESC
            """)
    List<ContactUnlock> findAllForCustomer(@Param("customerId") Long customerId);

    @Query("""
            SELECT cu FROM ContactUnlock cu
            JOIN FETCH cu.customer
            JOIN FETCH cu.caretaker
            WHERE cu.caretaker.id = :caretakerId
            ORDER BY cu.unlockedAt DESC
            """)
    List<ContactUnlock> findAllForCaretaker(@Param("caretakerId") Long caretakerId);
}
