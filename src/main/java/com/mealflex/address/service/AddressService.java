package com.mealflex.address.service;

import com.mealflex.address.dto.AddressRequest;
import com.mealflex.address.dto.AddressResponse;
import com.mealflex.address.entity.Address;
import com.mealflex.address.repository.AddressRepository;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public List<AddressResponse> getMyAddresses(Long userId) {
        return addressRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(Long userId, AddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));

        validateCoordinates(request);
        boolean firstAddress = addressRepository.findByUserIdAndDeletedAtIsNull(userId).isEmpty();
        Address address = Address.builder()
                .user(user)
                .title(request.getTitle())
                .city(request.getCity())
                .district(request.getDistrict())
                .neighborhood(request.getNeighborhood())
                .street(request.getStreet())
                .buildingNo(request.getBuildingNo())
                .floor(request.getFloor())
                .apartmentNo(request.getApartmentNo())
                .fullAddress(request.getFullAddress())
                .directions(request.getDirections())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .defaultAddress(firstAddress)
                .build();

        address = addressRepository.save(address);
        return toResponse(address);
    }

    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Adres", addressId));

        validateCoordinates(request);
        address.setTitle(request.getTitle());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setNeighborhood(request.getNeighborhood());
        address.setStreet(request.getStreet());
        address.setBuildingNo(request.getBuildingNo());
        address.setFloor(request.getFloor());
        address.setApartmentNo(request.getApartmentNo());
        address.setFullAddress(request.getFullAddress());
        address.setDirections(request.getDirections());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());

        address = addressRepository.save(address);
        return toResponse(address);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Adres", addressId));
        address.setDeletedAt(java.time.Instant.now());
        addressRepository.save(address);
        if (address.isDefaultAddress()) {
            addressRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
                    .filter(candidate -> !candidate.getId().equals(addressId))
                    .findFirst().ifPresent(candidate -> { candidate.setDefaultAddress(true); addressRepository.save(candidate); });
        }
    }

    @Transactional
    public AddressResponse setDefaultAddress(Long userId, Long addressId) {
        Address selected = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Adres", addressId));
        addressRepository.findByUserIdAndDeletedAtIsNull(userId).forEach(address -> {
            boolean isSelected = address.getId().equals(selected.getId());
            if (address.isDefaultAddress() != isSelected) {
                address.setDefaultAddress(isSelected);
                addressRepository.save(address);
            }
        });
        return toResponse(selected);
    }

    private AddressResponse toResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .title(address.getTitle())
                .city(address.getCity())
                .district(address.getDistrict())
                .neighborhood(address.getNeighborhood())
                .street(address.getStreet())
                .buildingNo(address.getBuildingNo())
                .floor(address.getFloor())
                .apartmentNo(address.getApartmentNo())
                .fullAddress(address.getFullAddress())
                .directions(address.getDirections())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .defaultAddress(address.isDefaultAddress())
                .nearbyAddressWarning(hasNearbyAddress(address))
                .build();
    }

    private void validateCoordinates(AddressRequest request) {
        BigDecimal latitude = request.getLatitude(); BigDecimal longitude = request.getLongitude();
        if (latitude == null || longitude == null || latitude.compareTo(new BigDecimal("35.0")) < 0 || latitude.compareTo(new BigDecimal("43.0")) > 0 || longitude.compareTo(new BigDecimal("25.0")) < 0 || longitude.compareTo(new BigDecimal("46.0")) > 0) {
            throw new BusinessException("ADDRESS_COORDINATES_INVALID", "Harita konumu Türkiye sınırları içinde olmalıdır.");
        }
    }
    private boolean hasNearbyAddress(Address address) {
        return addressRepository.findByUserIdAndDeletedAtIsNull(address.getUser().getId()).stream().anyMatch(other -> !other.getId().equals(address.getId()) && distanceMeters(address.getLatitude(), address.getLongitude(), other.getLatitude(), other.getLongitude()) < 50d);
    }
    private double distanceMeters(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) { double r=6371000d,dLat=Math.toRadians(lat2.doubleValue()-lat1.doubleValue()),dLon=Math.toRadians(lon2.doubleValue()-lon1.doubleValue());double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(lat1.doubleValue()))*Math.cos(Math.toRadians(lat2.doubleValue()))*Math.sin(dLon/2)*Math.sin(dLon/2);return r*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a)); }
}
