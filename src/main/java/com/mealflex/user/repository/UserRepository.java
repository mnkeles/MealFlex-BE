package com.mealflex.user.repository;

import com.mealflex.user.entity.Role;
import com.mealflex.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    java.util.List<User> findTop10ByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String email, String firstName, String lastName);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByRole(Role role, Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE (:role IS NULL OR u.role = :role)
        AND (:search IS NULL
             OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(COALESCE(u.phone, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        """)
    Page<User> searchForAdmin(
            @Param("role") Role role,
            @Param("search") String search,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForSubscriptionRequest(@Param("id") Long id);
}
