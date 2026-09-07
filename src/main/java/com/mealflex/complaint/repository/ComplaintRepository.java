package com.mealflex.complaint.repository;

import com.mealflex.complaint.entity.Complaint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    @Query("SELECT c FROM Complaint c JOIN FETCH c.store st JOIN FETCH c.customer "
            + "WHERE c.createdAt >= :startedAt AND c.createdAt < :endedAt "
            + "AND (:storeId IS NULL OR st.id = :storeId)")
    List<Complaint> findForAdminOperations(@Param("startedAt") Instant startedAt,
            @Param("endedAt") Instant endedAt, @Param("storeId") Long storeId);

    Page<Complaint> findByCustomerId(Long customerId, Pageable pageable);

    Page<Complaint> findByStoreId(Long storeId, Pageable pageable);
}
