package com.mealflex.user.repository;
import com.mealflex.user.entity.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord,Long> { List<ConsentRecord> findByUserIdOrderByAcceptedAtDesc(Long userId); }
