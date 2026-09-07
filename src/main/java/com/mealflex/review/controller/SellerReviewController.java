package com.mealflex.review.controller;

import com.mealflex.review.dto.ReplyReviewRequest;
import com.mealflex.review.dto.ReviewResponse;
import com.mealflex.review.service.ReviewService;
import com.mealflex.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/seller/reviews")
@RequiredArgsConstructor
public class SellerReviewController {
    private final ReviewService service;

    @PutMapping("/{reviewId}/reply")
    public ResponseEntity<ReviewResponse> reply(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long reviewId,
                                                @Valid @RequestBody ReplyReviewRequest request) {
        return ResponseEntity.ok(service.reply(principal.getId(), reviewId, request.getReply()));
    }
}
