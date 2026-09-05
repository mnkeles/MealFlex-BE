package com.mealflex.review.controller;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.review.dto.ReplyReviewRequest;
import com.mealflex.review.repository.ReviewRepository;
import com.mealflex.security.UserPrincipal;
import com.mealflex.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerReviewControllerTest {

    @Mock private ReviewRepository reviewRepository;
    @InjectMocks private SellerReviewController controller;

    @Test
    void anotherSellersReviewCannotBeRepliedTo() {
        ReplyReviewRequest request = new ReplyReviewRequest();
        request.setReply("Yanıt");
        when(reviewRepository.findByIdAndStoreSellerUserIdAndDeletedAtIsNull(8L, 77L))
                .thenReturn(Optional.empty());
        UserPrincipal principal = new UserPrincipal(77L, "seller@example.com", "x",
                Role.SELLER, true, List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> controller.reply(principal, 8L, request));
    }
}
