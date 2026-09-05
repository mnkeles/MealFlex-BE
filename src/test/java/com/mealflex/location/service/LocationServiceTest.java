package com.mealflex.location.service;

import com.mealflex.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LocationServiceTest {

    private final RestClient.Builder clientBuilder = RestClient.builder().baseUrl("https://location.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
    private final LocationService service = new LocationService(clientBuilder.build());

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void districtsAndNeighborhoodsAreBoundToTheirSelectedParentIds() {
        server.expect(requestTo("https://location.test/districts?provinceId=6&limit=1000&fields=id,name"))
                .andRespond(withSuccess("{\"data\":[{\"id\":111,\"name\":\"Yenimahalle\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://location.test/districts/111/neighborhoods?limit=1000&fields=id,name"))
                .andRespond(withSuccess("{\"data\":[{\"id\":501,\"name\":\"İvedik OSB\"}]}", MediaType.APPLICATION_JSON));

        var districts = service.getDistricts(6L);
        var neighborhoods = service.getNeighborhoods(111L);

        assertThat(districts).extracting(item -> item.name()).containsExactly("Yenimahalle");
        assertThat(neighborhoods).extracting(item -> item.name()).containsExactly("İvedik OSB");
    }

    @Test
    void upstreamFailureBecomesSafeLocationServiceError() {
        server.expect(requestTo("https://location.test/provinces?limit=100&fields=id,name"))
                .andRespond(withServerError());

        BusinessException error = assertThrows(BusinessException.class, service::getProvinces);

        assertThat(error.getCode()).isEqualTo("LOCATION_SERVICE_UNAVAILABLE");
    }
}
