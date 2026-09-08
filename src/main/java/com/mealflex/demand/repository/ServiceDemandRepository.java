package com.mealflex.demand.repository;

import com.mealflex.demand.entity.ServiceDemand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ServiceDemandRepository extends JpaRepository<ServiceDemand, Long> {
    Optional<ServiceDemand> findByUserIdAndAddressId(Long userId, Long addressId);
    @Query("""
        select d.city, d.district, d.neighborhood, count(d)
        from ServiceDemand d where d.status='ACTIVE'
        group by d.city, d.district, d.neighborhood
        order by count(d) desc
        """)
    List<Object[]> aggregateActiveDemand();
}
