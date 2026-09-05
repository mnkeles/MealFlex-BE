package com.mealflex.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateComplaintRequest {

    private Long subscriptionId;
    private Long deliveryId;

    @NotBlank(message = "Şikâyet sebebi zorunludur.")
    private String reason;

    @NotBlank(message = "Açıklama zorunludur.")
    private String description;
}
