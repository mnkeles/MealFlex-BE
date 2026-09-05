package com.mealflex.subscription.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SubscriptionEventResponse {

    private Long id;
    private String action;
    private String oldValue;
    private String newValue;
    private Instant timestamp;
}
