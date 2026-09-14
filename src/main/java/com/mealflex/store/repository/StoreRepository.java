package com.mealflex.store.repository;

import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;


import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /** Kapasite kontrolü gibi eşzamanlı okuma-yaz senaryolarında mağaza satırını kilitler. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Store s WHERE s.id = :id")
    Optional<Store> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT s FROM Store s
        WHERE s.deletedAt IS NULL
        AND (:status IS NULL OR s.status = :status)
        AND (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        """)
    Page<Store> searchForAdmin(
            @Param("status") StoreStatus status,
            @Param("search") String search,
            Pageable pageable);

    Optional<Store> findByIdAndSellerUserIdAndDeletedAtIsNull(Long id, Long userId);

    List<Store> findAllBySellerUserIdAndDeletedAtIsNull(Long userId);

    boolean existsBySellerUserId(Long userId);

    @Query("""
        SELECT s FROM Store s
        JOIN ServiceArea sa ON sa.store = s
        WHERE sa.city = :city AND sa.district = :district
        AND s.status = :status
        AND s.deletedAt IS NULL
        """)
    Page<Store> findByServiceAreaAndStatus(
            @Param("city") String city,
            @Param("district") String district,
            @Param("status") StoreStatus status,
            Pageable pageable);

    @Query("""
        SELECT s FROM Store s
        JOIN ServiceArea sa ON sa.store = s
        WHERE sa.city = :city AND sa.district = :district
        AND s.status = 'ACTIVE'
        AND s.deletedAt IS NULL
        AND (LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))
             OR EXISTS (SELECT m FROM Menu m WHERE m.store = s AND LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%'))))
        """)
    Page<Store> searchByNameOrMenuName(
            @Param("city") String city,
            @Param("district") String district,
            @Param("search") String search,
            Pageable pageable);
}
