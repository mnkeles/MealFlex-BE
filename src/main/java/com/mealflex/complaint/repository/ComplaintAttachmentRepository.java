package com.mealflex.complaint.repository;
import com.mealflex.complaint.entity.ComplaintAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ComplaintAttachmentRepository extends JpaRepository<ComplaintAttachment,Long>{List<ComplaintAttachment> findByComplaintIdOrderByCreatedAtAsc(Long complaintId);}
