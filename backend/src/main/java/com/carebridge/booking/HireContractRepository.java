package com.carebridge.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HireContractRepository extends JpaRepository<HireContract, Long> {

    @Query("""
            SELECT hc FROM HireContract hc
            JOIN FETCH hc.customer
            JOIN FETCH hc.caretaker
            LEFT JOIN FETCH hc.initiatedBy
            WHERE hc.id = :id
            """)
    Optional<HireContract> findByIdWithUsers(@Param("id") Long id);

    @Query("SELECT hc.caretaker.id FROM HireContract hc WHERE hc.id = :id")
    Optional<Long> findCaretakerIdByContractId(@Param("id") Long id);

    @Query("""
            SELECT hc FROM HireContract hc
            WHERE hc.caretaker.id = :caretakerId
              AND hc.status IN :statuses
              AND hc.scheduledStartTime < :windowEnd
              AND hc.scheduledEndTime > :windowStart
            ORDER BY hc.scheduledStartTime ASC
            """)
    List<HireContract> findCaretakerContractsTouchingWindow(
            @Param("caretakerId") Long caretakerId,
            @Param("statuses") Collection<ContractStatus> statuses,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    @Query("""
            SELECT hc FROM HireContract hc
            WHERE hc.caretaker.id IN :caretakerIds
              AND hc.status IN :statuses
              AND hc.scheduledStartTime < :windowEnd
              AND hc.scheduledEndTime > :windowStart
            """)
    List<HireContract> findCaretakersContractsTouchingWindow(
            @Param("caretakerIds") Collection<Long> caretakerIds,
            @Param("statuses") Collection<ContractStatus> statuses,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    @Query("""
            SELECT CASE WHEN COUNT(hc) > 0 THEN TRUE ELSE FALSE END
            FROM HireContract hc
            WHERE hc.customer.id = :customerId
              AND hc.caretaker.id = :caretakerId
              AND hc.status = :status
              AND hc.scheduledStartTime < :requestedEnd
              AND hc.scheduledEndTime > :requestedStart
            """)
    boolean existsOverlappingByCustomerAndCaretakerAndStatus(
            @Param("customerId") Long customerId,
            @Param("caretakerId") Long caretakerId,
            @Param("status") ContractStatus status,
            @Param("requestedStart") Instant requestedStart,
            @Param("requestedEnd") Instant requestedEnd
    );

    @Query("""
            SELECT hc FROM HireContract hc
            JOIN FETCH hc.customer
            JOIN FETCH hc.caretaker
            LEFT JOIN FETCH hc.initiatedBy
            WHERE hc.customer.id = :customerId
              AND (:filterByStatuses = FALSE OR hc.status IN :statuses)
            ORDER BY hc.createdAt DESC, hc.id DESC
            """)
    List<HireContract> findForCustomer(
            @Param("customerId") Long customerId,
            @Param("filterByStatuses") boolean filterByStatuses,
            @Param("statuses") Collection<ContractStatus> statuses
    );

    @Query("""
            SELECT hc FROM HireContract hc
            JOIN FETCH hc.customer
            JOIN FETCH hc.caretaker
            LEFT JOIN FETCH hc.initiatedBy
            WHERE hc.caretaker.id = :caretakerId
              AND (:filterByStatuses = FALSE OR hc.status IN :statuses)
            ORDER BY hc.createdAt DESC, hc.id DESC
            """)
    List<HireContract> findForCaretaker(
            @Param("caretakerId") Long caretakerId,
            @Param("filterByStatuses") boolean filterByStatuses,
            @Param("statuses") Collection<ContractStatus> statuses
    );

    @Query("""
            SELECT hc FROM HireContract hc
            JOIN FETCH hc.customer
            JOIN FETCH hc.caretaker
            LEFT JOIN FETCH hc.initiatedBy
            WHERE (:filterByStatuses = FALSE OR hc.status IN :statuses)
            ORDER BY hc.createdAt DESC, hc.id DESC
            """)
    List<HireContract> findAllWithUsers(
            @Param("filterByStatuses") boolean filterByStatuses,
            @Param("statuses") Collection<ContractStatus> statuses
    );

    @Query("""
            SELECT hc.customer.id, COUNT(hc)
            FROM HireContract hc
            WHERE hc.customer.id IN :customerIds
              AND hc.status IN :completedStatuses
            GROUP BY hc.customer.id
            """)
    List<Object[]> countCompletedHiresByCustomerIds(
            @Param("customerIds") Collection<Long> customerIds,
            @Param("completedStatuses") Collection<ContractStatus> completedStatuses
    );

    @Query("""
            SELECT hc.id FROM HireContract hc
            WHERE hc.status = com.carebridge.booking.ContractStatus.SCHEDULED
              AND hc.scheduledStartTime <= :now
              AND hc.scheduledEndTime > :now
            """)
    List<Long> findScheduledIdsReadyToActivate(@Param("now") Instant now);

    @Query("""
            SELECT hc.id FROM HireContract hc
            WHERE hc.status IN :activeOrScheduled
              AND hc.scheduledEndTime <= :now
            """)
    List<Long> findContractIdsDueForAutoCompletion(
            @Param("activeOrScheduled") Collection<ContractStatus> activeOrScheduled,
            @Param("now") Instant now
    );

    long countByCaretakerIdAndStatusIn(Long caretakerId, Collection<ContractStatus> statuses);
}
