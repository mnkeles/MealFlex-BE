package com.mealflex.review.controller;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.review.dto.ReplyReviewRequest;
import com.mealflex.review.dto.ReviewResponse;
import com.mealflex.review.entity.Review;
import com.mealflex.review.repository.ReviewRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/v1/seller/reviews")
@RequiredArgsConstructor
public class SellerReviewController {

    private final ReviewRepository reviewRepository;
    private final NotificationRepository notificationRepository;

    @PutMapping("/{reviewId}/reply")
    @Transactional
    public ResponseEntity<ReviewResponse> reply(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReplyReviewRequest request) {
        Review review = reviewRepository.findByIdAndStoreSellerUserIdAndDeletedAtIsNull(reviewId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Yorum", reviewId));
        review.setSellerReply(request.getReply().trim());
        review.setSellerRepliedAt(Instant.now());
        reviewRepository.save(review);
        notificationRepository.save(Notification.builder()
                .user(review.getCustomer())
                .title("Yorumunuza yanıt verildi")
                .message(review.getStore().getName() + " yorumunuza yanıt verdi.")
                .referenceType("STORE")
                .referenceId(review.getStore().getId())
                .build());
        return ResponseEntity.ok(toResponse(review));
    }

    private ReviewResponse toResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .customerName(review.getCustomer().getFirstName() + " " + review.getCustomer().getLastName().charAt(0) + ".")
                .rating(review.getRating())
                .comment(review.getComment())
                .sellerReply(review.getSellerReply())
                .sellerRepliedAt(review.getSellerRepliedAt())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
