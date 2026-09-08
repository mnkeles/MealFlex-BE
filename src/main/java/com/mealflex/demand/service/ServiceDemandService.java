package com.mealflex.demand.service;

import com.mealflex.address.entity.Address;
import com.mealflex.address.repository.AddressRepository;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.demand.dto.*;
import com.mealflex.demand.entity.ServiceDemand;
import com.mealflex.demand.repository.ServiceDemandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceDemandService {
    private final ServiceDemandRepository demands;
    private final AddressRepository addresses;

    @Transactional
    public ServiceDemandResponse register(Long userId, Long addressId) {
        ServiceDemand existing = demands.findByUserIdAndAddressId(userId, addressId).orElse(null);
        if (existing != null) {
            if (!"ACTIVE".equals(existing.getStatus())) { existing.setStatus("ACTIVE"); demands.save(existing); }
            return response(existing);
        }
        Address address = addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Adres", addressId));
        return response(demands.save(ServiceDemand.builder().user(address.getUser()).address(address)
                .city(address.getCity()).district(address.getDistrict()).neighborhood(address.getNeighborhood()).build()));
    }

    @Transactional(readOnly = true)
    public List<ServiceDemandSummaryResponse> report() {
        return demands.aggregateActiveDemand().stream().map(row -> new ServiceDemandSummaryResponse(
                String.valueOf(row[0]), String.valueOf(row[1]), row[2] == null ? "" : String.valueOf(row[2]),
                ((Number) row[3]).longValue())).toList();
    }

    private ServiceDemandResponse response(ServiceDemand demand) {
        return new ServiceDemandResponse(demand.getId(), demand.getAddress().getId(), demand.getCity(), demand.getDistrict(),
                demand.getNeighborhood(), demand.getStatus(), demand.getCreatedAt());
    }
}
