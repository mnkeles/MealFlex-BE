package com.mealflex.complaint.repository;

import com.mealflex.complaint.entity.Complaint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Page<Complaint> findByCustomerId(Long customerId, Pageable pageable);

    Page<Complaint> findByStoreId(Long storeId, Pageable pageable);
}
