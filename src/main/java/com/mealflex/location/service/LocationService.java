package com.mealflex.location.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.location.dto.LocationOptionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocationService {

    private final RestClient client;
    private volatile List<LocationOptionResponse> provinces;
    private final Map<Long, List<LocationOptionResponse>> districts = new ConcurrentHashMap<>();
    private final Map<Long, List<LocationOptionResponse>> neighborhoods = new ConcurrentHashMap<>();

    public LocationService() {
        this(RestClient.builder()
                .baseUrl("https://api.turkiyeapi.dev/v2")
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build());
    }

    LocationService(RestClient client) {
        this.client = client;
    }

    public List<LocationOptionResponse> getProvinces() {
        List<LocationOptionResponse> cached = provinces;
        if (cached != null) return cached;
        cached = fetch("/provinces?limit=100&fields=id,name");
        provinces = cached;
        return cached;
    }

    public List<LocationOptionResponse> getDistricts(Long provinceId) {
        return districts.computeIfAbsent(provinceId,
                id -> fetch("/districts?provinceId=" + id + "&limit=1000&fields=id,name"));
    }

    public List<LocationOptionResponse> getNeighborhoods(Long districtId) {
        return neighborhoods.computeIfAbsent(districtId,
                id -> fetch("/districts/" + id + "/neighborhoods?limit=1000&fields=id,name"));
    }

    private List<LocationOptionResponse> fetch(String path) {
        try {
            JsonNode response = client.get().uri(path).retrieve().body(JsonNode.class);
            if (response == null || !response.path("data").isArray()) {
                throw new BusinessException("LOCATION_DATA_INVALID",
                        "Konum verisi beklenen biçimde alınamadı.", HttpStatus.BAD_GATEWAY);
            }
            List<LocationOptionResponse> result = new ArrayList<>();
            for (JsonNode item : response.path("data")) {
                result.add(new LocationOptionResponse(item.path("id").asLong(), item.path("name").asText()));
            }
            return result.stream()
                    .distinct()
                    .sorted(Comparator.comparing(LocationOptionResponse::name,
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (RestClientException exception) {
            throw new BusinessException("LOCATION_SERVICE_UNAVAILABLE",
                    "İl, ilçe ve mahalle bilgileri şu anda alınamıyor. Lütfen tekrar deneyin.",
                    HttpStatus.BAD_GATEWAY);
        }
    }

}
