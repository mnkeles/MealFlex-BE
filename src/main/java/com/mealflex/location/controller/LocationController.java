package com.mealflex.location.controller;

import com.mealflex.location.dto.LocationOptionResponse;
import com.mealflex.location.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/provinces")
    public ResponseEntity<List<LocationOptionResponse>> getProvinces() {
        return ResponseEntity.ok(locationService.getProvinces());
    }

    @GetMapping("/districts")
    public ResponseEntity<List<LocationOptionResponse>> getDistricts(@RequestParam Long provinceId) {
        return ResponseEntity.ok(locationService.getDistricts(provinceId));
    }

    @GetMapping("/neighborhoods")
    public ResponseEntity<List<LocationOptionResponse>> getNeighborhoods(@RequestParam Long districtId) {
        return ResponseEntity.ok(locationService.getNeighborhoods(districtId));
    }
}
