package com.carebridge.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentDisputeRepository extends JpaRepository<PaymentDispute, Long> {

    @Query("""
            SELECT pd FROM PaymentDispute pd
            JOIN FETCH pd.contract hc
            JOIN FETCH hc.customer
            JOIN FETCH hc.caretaker
            JOIN FETCH pd.raisedBy
            LEFT JOIN FETCH pd.assignedAdmin
            WHERE hc.id = :contractId
            """)
    Optional<PaymentDispute> findByContractIdWithDetails(@Param("contractId") Long contractId);

    @Query("""
            SELECT pd FROM PaymentDispute pd
            JOIN FETCH pd.contract hc
            JOIN FETCH hc.customer
            JOIN FETCH hc.caretaker
            JOIN FETCH pd.raisedBy
            LEFT JOIN FETCH pd.assignedAdmin
            WHERE (:status IS NULL OR pd.status = :status)
            ORDER BY pd.createdAt DESC, pd.id DESC
            """)
    List<PaymentDispute> findAllWithDetailsByStatus(@Param("status") DisputeStatus status);

    @Query("""
            SELECT pd FROM PaymentDispute pd
            JOIN FETCH pd.contract hc
            JOIN FETCH hc.customer
            JOIN FETCH hc.caretaker
            JOIN FETCH pd.raisedBy
            LEFT JOIN FETCH pd.assignedAdmin
            WHERE (hc.customer.id = :userId OR hc.caretaker.id = :userId)
              AND (:status IS NULL OR pd.status = :status)
            ORDER BY pd.createdAt DESC, pd.id DESC
            """)
    List<PaymentDispute> findAllForParticipant(
            @Param("userId") Long userId,
            @Param("status") DisputeStatus status
    );
}
