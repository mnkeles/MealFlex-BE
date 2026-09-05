package com.mealflex.address.service;

import com.mealflex.address.dto.AddressRequest;
import com.mealflex.address.entity.Address;
import com.mealflex.address.repository.AddressRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock private AddressRepository addressRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AddressService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email("customer@example.com").password("hash")
                .firstName("Test").lastName("Müşteri").build();
        user.setId(5L);
    }

    @Test
    void firstAddressBecomesDefaultAndStoresConfirmedCoordinates() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(addressRepository.findByUserIdAndDeletedAtIsNull(5L)).thenReturn(List.of());
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        var response = service.createAddress(5L, request("Ev", "39.9334", "32.8597"));

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.isDefaultAddress()).isTrue();
        assertThat(response.getLatitude()).isEqualByComparingTo("39.9334");
        assertThat(response.getLongitude()).isEqualByComparingTo("32.8597");
    }

    @Test
    void nearbyAddressIsFlaggedAndInvalidCoordinateIsRejected() {
        Address first = address(10L, "Ev", "39.9334", "32.8597", true);
        Address nearby = address(11L, "Ofis", "39.9335", "32.8598", false);
        when(addressRepository.findByUserIdAndDeletedAtIsNull(5L)).thenReturn(List.of(first, nearby));

        var addresses = service.getMyAddresses(5L);

        assertThat(addresses).filteredOn(item -> item.getId().equals(11L))
                .singleElement().satisfies(item -> assertThat(item.isNearbyAddressWarning()).isTrue());
        assertThrows(BusinessException.class, () -> service.createAddress(5L, request("Geçersiz", "10", "10")));
        verify(addressRepository, never()).save(any());
    }

    @Test
    void defaultAddressChangesAndDeletedDefaultPromotesAnotherAddress() {
        Address first = address(10L, "Ev", "39.9334", "32.8597", true);
        Address second = address(11L, "Ofis", "39.9400", "32.8600", false);
        when(addressRepository.findByIdAndUserId(11L, 5L)).thenReturn(Optional.of(second));
        when(addressRepository.findByUserIdAndDeletedAtIsNull(5L)).thenReturn(List.of(first, second));

        var selected = service.setDefaultAddress(5L, 11L);

        assertThat(selected.isDefaultAddress()).isTrue();
        assertThat(first.isDefaultAddress()).isFalse();
        verify(addressRepository, times(2)).save(any(Address.class));

        reset(addressRepository);
        first.setDefaultAddress(true);
        second.setDefaultAddress(false);
        when(addressRepository.findByIdAndUserId(10L, 5L)).thenReturn(Optional.of(first));
        when(addressRepository.findByUserIdAndDeletedAtIsNull(5L)).thenReturn(List.of(first, second));

        service.deleteAddress(5L, 10L);

        assertThat(first.getDeletedAt()).isNotNull();
        assertThat(second.isDefaultAddress()).isTrue();
        verify(addressRepository, times(2)).save(any(Address.class));
    }

    private AddressRequest request(String title, String latitude, String longitude) {
        AddressRequest request = new AddressRequest();
        request.setTitle(title);
        request.setCity("Ankara");
        request.setDistrict("Yenimahalle");
        request.setNeighborhood("İvedik OSB");
        request.setLatitude(new BigDecimal(latitude));
        request.setLongitude(new BigDecimal(longitude));
        return request;
    }

    private Address address(Long id, String title, String latitude, String longitude, boolean defaultAddress) {
        Address address = Address.builder().user(user).title(title).city("Ankara").district("Yenimahalle")
                .neighborhood("İvedik OSB").latitude(new BigDecimal(latitude)).longitude(new BigDecimal(longitude))
                .defaultAddress(defaultAddress).build();
        address.setId(id);
        return address;
    }
}
