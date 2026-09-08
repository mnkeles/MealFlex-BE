package com.mealflex.demand.service;

import com.mealflex.address.entity.Address;
import com.mealflex.address.repository.AddressRepository;
import com.mealflex.demand.entity.ServiceDemand;
import com.mealflex.demand.repository.ServiceDemandRepository;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ServiceDemandServiceTest {
    @Test void sameAddressIsRegisteredIdempotentlyAndReportIsAggregated() {
        ServiceDemandRepository demands = mock(ServiceDemandRepository.class);
        AddressRepository addresses = mock(AddressRepository.class);
        ServiceDemandService service = new ServiceDemandService(demands, addresses);
        User user = User.builder().build(); user.setId(1L);
        Address address = Address.builder().user(user).city("Ankara").district("Yenimahalle").neighborhood("Demetevler").build(); address.setId(2L);
        when(demands.findByUserIdAndAddressId(1L,2L)).thenReturn(Optional.empty());
        when(addresses.findByIdAndUserId(2L,1L)).thenReturn(Optional.of(address));
        when(demands.save(any())).thenAnswer(invocation -> { ServiceDemand value=invocation.getArgument(0); value.setId(3L); return value; });
        when(demands.aggregateActiveDemand()).thenReturn(List.<Object[]>of(new Object[]{"Ankara","Yenimahalle","Demetevler",4L}));

        assertThat(service.register(1L,2L).district()).isEqualTo("Yenimahalle");
        assertThat(service.report().getFirst().requestCount()).isEqualTo(4);
        verify(demands).save(any(ServiceDemand.class));
    }
}
