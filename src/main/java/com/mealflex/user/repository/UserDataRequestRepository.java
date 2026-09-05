package com.mealflex.user.repository;
import com.mealflex.user.entity.UserDataRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface UserDataRequestRepository extends JpaRepository<UserDataRequest,Long> { List<UserDataRequest> findByUserIdOrderByRequestedAtDesc(Long userId); }
