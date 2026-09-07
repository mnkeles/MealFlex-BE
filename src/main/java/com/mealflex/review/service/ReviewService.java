package com.mealflex.review.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.review.dto.CreateReviewRequest;
import com.mealflex.review.dto.ReviewResponse;
import com.mealflex.review.entity.Review;
import com.mealflex.review.repository.ReviewRepository;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviews;
    private final SubscriptionRepository subscriptions;
    private final StoreRepository stores;
    private final UserRepository users;
    private final NotificationEventService notificationEvents;

    @Transactional(readOnly = true)
    public Page<ReviewResponse> storeReviews(Long storeId, Pageable pageable) {
        return reviews.findByStoreIdAndDeletedAtIsNull(storeId, pageable).map(this::response);
    }

    @Transactional
    public ReviewResponse create(Long userId, CreateReviewRequest request) {
        var subscription = subscriptions.findById(request.getSubscriptionId())
                .filter(item -> item.getCustomer().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", request.getSubscriptionId()));
        if (subscription.getStatus() != SubscriptionStatus.COMPLETED && subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BusinessException("INVALID_STATUS", "Yalnızca aktif veya tamamlanmış aboneliklere yorum yapılabilir.");
        }
        if (reviews.existsByCustomerIdAndSubscriptionId(userId, subscription.getId())) {
            throw new BusinessException("ALREADY_REVIEWED", "Bu abonelik için zaten yorum yapmışsınız.");
        }
        var customer = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));
        var store = subscription.getStore();
        Review review = reviews.save(Review.builder().customer(customer).store(store).subscription(subscription)
                .rating(request.getRating()).comment(request.getComment()).build());
        var average = reviews.calculateAverageRating(store.getId());
        store.setRating(average.setScale(1, java.math.RoundingMode.HALF_UP));
        store.setReviewCount(reviews.countByStoreId(store.getId()));
        stores.save(store);
        return response(review);
    }

    @Transactional
    public ReviewResponse reply(Long sellerId, Long reviewId, String reply) {
        Review review = reviews.findByIdAndStoreSellerUserIdAndDeletedAtIsNull(reviewId, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Yorum", reviewId));
        review.setSellerReply(reply.trim());
        review.setSellerRepliedAt(Instant.now());
        reviews.save(review);
        notificationEvents.publish(Notification.builder().user(review.getCustomer())
                .title("Yorumunuza yanıt verildi").message(review.getStore().getName() + " yorumunuza yanıt verdi.")
                .referenceType("STORE").referenceId(review.getStore().getId()).build());
        return response(review);
    }

    private ReviewResponse response(Review review) {
        String lastName = review.getCustomer().getLastName();
        String customerName = review.getCustomer().getFirstName()
                + (lastName == null || lastName.isBlank() ? "" : " " + lastName.charAt(0) + ".");
        return ReviewResponse.builder().id(review.getId()).customerName(customerName)
                .rating(review.getRating()).comment(review.getComment()).sellerReply(review.getSellerReply())
                .sellerRepliedAt(review.getSellerRepliedAt()).createdAt(review.getCreatedAt()).build();
    }
}
