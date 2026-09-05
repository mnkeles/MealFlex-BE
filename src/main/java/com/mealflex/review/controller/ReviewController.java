package com.mealflex.review.controller;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.review.dto.CreateReviewRequest;
import com.mealflex.review.dto.ReviewResponse;
import com.mealflex.review.entity.Review;
import com.mealflex.review.repository.ReviewRepository;
import com.mealflex.security.UserPrincipal;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Yorum ve puanlama")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    @GetMapping("/store/{storeId}")
    @Operation(summary = "Mağaza yorumlarını listele")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<ReviewResponse>> getStoreReviews(
            @PathVariable Long storeId, Pageable pageable) {
        Page<ReviewResponse> reviews = reviewRepository.findByStoreIdAndDeletedAtIsNull(storeId, pageable)
                .map(r -> ReviewResponse.builder()
                        .id(r.getId())
                        .customerName(r.getCustomer().getFirstName() + " " + r.getCustomer().getLastName().charAt(0) + ".")
                        .rating(r.getRating())
                        .comment(r.getComment())
                        .sellerReply(r.getSellerReply())
                        .sellerRepliedAt(r.getSellerRepliedAt())
                        .createdAt(r.getCreatedAt())
                        .build());
        return ResponseEntity.ok(reviews);
    }

    @PostMapping
    @Operation(summary = "Yorum yap")
    @Transactional
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateReviewRequest request) {

        Subscription sub = subscriptionRepository.findById(request.getSubscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", request.getSubscriptionId()));

        if (!sub.getCustomer().getId().equals(principal.getId())) {
            throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu abonelik size ait değil.");
        }

        if (sub.getStatus() != SubscriptionStatus.COMPLETED && sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BusinessException("INVALID_STATUS", "Yalnızca aktif veya tamamlanmış aboneliklere yorum yapılabilir.");
        }

        if (reviewRepository.existsByCustomerIdAndSubscriptionId(principal.getId(), sub.getId())) {
            throw new BusinessException("ALREADY_REVIEWED", "Bu abonelik için zaten yorum yapmışsınız.");
        }

        User customer = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", principal.getId()));
        Store store = sub.getStore();

        Review review = Review.builder()
                .customer(customer)
                .store(store)
                .subscription(sub)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
        review = reviewRepository.save(review);

        BigDecimal avgRating = reviewRepository.calculateAverageRating(store.getId());
        int count = reviewRepository.countByStoreId(store.getId());
        store.setRating(avgRating.setScale(1, java.math.RoundingMode.HALF_UP));
        store.setReviewCount(count);
        storeRepository.save(store);

        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.builder()
                .id(review.getId())
                .customerName(customer.getFirstName() + " " + customer.getLastName().charAt(0) + ".")
                .rating(review.getRating())
                .comment(review.getComment())
                .sellerReply(review.getSellerReply())
                .sellerRepliedAt(review.getSellerRepliedAt())
                .createdAt(review.getCreatedAt())
                .build());
    }
}
