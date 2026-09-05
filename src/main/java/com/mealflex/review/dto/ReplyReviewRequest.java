package com.mealflex.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReplyReviewRequest {
    @NotBlank(message = "Yanıt boş olamaz.")
    @Size(max = 2000, message = "Yanıt en fazla 2000 karakter olabilir.")
    private String reply;
}
