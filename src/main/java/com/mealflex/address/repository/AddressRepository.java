package com.mealflex.address.repository;

import com.mealflex.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdAndDeletedAtIsNull(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);
}
