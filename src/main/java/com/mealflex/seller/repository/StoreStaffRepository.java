package com.mealflex.seller.repository;
import com.mealflex.seller.entity.StoreStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface StoreStaffRepository extends JpaRepository<StoreStaff,Long> {
 List<StoreStaff> findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long storeId);
 Optional<StoreStaff> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);
 Optional<StoreStaff> findByInvitationTokenHashAndStatus(String hash, String status);
    Optional<StoreStaff> findByStoreIdAndUserIdAndStatus(Long storeId, Long userId, String status);
    List<StoreStaff> findByUserIdAndStatus(Long userId, String status);
}
