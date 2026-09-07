package com.mealflex.review.controller;

import com.mealflex.review.dto.CreateReviewRequest;
import com.mealflex.review.dto.ReviewResponse;
import com.mealflex.review.service.ReviewService;
import com.mealflex.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Yorum ve puanlama")
public class ReviewController {
    private final ReviewService service;

    @GetMapping("/store/{storeId}")
    @Operation(summary = "Mağaza yorumlarını listele")
    public ResponseEntity<Page<ReviewResponse>> getStoreReviews(@PathVariable Long storeId, Pageable pageable) {
        return ResponseEntity.ok(service.storeReviews(storeId, pageable));
    }

    @PostMapping
    @Operation(summary = "Yorum yap")
    public ResponseEntity<ReviewResponse> createReview(@AuthenticationPrincipal UserPrincipal principal,
                                                        @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.getId(), request));
    }
}
