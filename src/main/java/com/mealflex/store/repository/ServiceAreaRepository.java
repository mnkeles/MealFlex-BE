package com.mealflex.store.repository;

import com.mealflex.store.entity.ServiceArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceAreaRepository extends JpaRepository<ServiceArea, Long> {

    List<ServiceArea> findByStoreId(Long storeId);

    boolean existsByCityAndDistrictAndStoreId(String city, String district, Long storeId);

    boolean existsByStoreIdAndCityIgnoreCaseAndDistrictIgnoreCase(Long storeId, String city, String district);

    void deleteByStoreId(Long storeId);
}
