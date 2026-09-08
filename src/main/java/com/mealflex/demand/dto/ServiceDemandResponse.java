package com.mealflex.demand.dto;

import java.time.Instant;

public record ServiceDemandResponse(Long id, Long addressId, String city, String district,
                                    String neighborhood, String status, Instant createdAt) {}
