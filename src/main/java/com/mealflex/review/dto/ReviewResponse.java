package com.mealflex.review.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ReviewResponse {

    private Long id;
    private String customerName;
    private Integer rating;
    private String comment;
    private String sellerReply;
    private Instant sellerRepliedAt;
    private Instant createdAt;
}
