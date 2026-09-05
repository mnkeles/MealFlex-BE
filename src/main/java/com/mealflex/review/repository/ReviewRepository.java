package com.mealflex.review.repository;

import com.mealflex.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByStoreIdAndDeletedAtIsNull(Long storeId, Pageable pageable);

    boolean existsByCustomerIdAndSubscriptionId(Long customerId, Long subscriptionId);

    Optional<Review> findByIdAndStoreSellerUserIdAndDeletedAtIsNull(Long id, Long userId);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.store.id = :storeId AND r.deletedAt IS NULL")
    BigDecimal calculateAverageRating(@Param("storeId") Long storeId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.store.id = :storeId AND r.deletedAt IS NULL")
    int countByStoreId(@Param("storeId") Long storeId);

    @Query("""
        SELECT r.rating, COUNT(r) FROM Review r
        WHERE r.store.id = :storeId AND r.deletedAt IS NULL
        GROUP BY r.rating ORDER BY r.rating DESC
        """)
    List<Object[]> getRatingDistribution(@Param("storeId") Long storeId);
}
