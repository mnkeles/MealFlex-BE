package com.mealflex.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReviewRequest {

    @NotNull(message = "Abonelik ID zorunludur.")
    private Long subscriptionId;

    @NotNull(message = "Puan zorunludur.")
    @Min(value = 1, message = "Puan en az 1 olmalıdır.")
    @Max(value = 5, message = "Puan en fazla 5 olmalıdır.")
    private Integer rating;

    private String comment;
}
