package com.mealflex.seller.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.seller.entity.SellerDocument;
import com.mealflex.seller.repository.SellerDocumentRepository;
import com.mealflex.seller.repository.StoreOnboardingRepository;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SellerDocumentServiceTest {

    @Mock private SellerDocumentRepository documentRepository;
    @Mock private StoreOnboardingRepository onboardingRepository;
    @Mock private SellerStoreAccessService storeAccessService;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private NotificationRepository notificationRepository;
    @InjectMocks private SellerDocumentService documentService;

    @Test
    void documentFromAnotherStoreCannotBeDeleted() {
        Store selectedStore = Store.builder().name("Seçili mağaza").build();
        selectedStore.setId(5L);
        Store otherStore = Store.builder().name("Diğer mağaza").build();
        otherStore.setId(6L);
        SellerDocument document = SellerDocument.builder().store(otherStore)
                .documentType("RUHSAT").fileName("ruhsat.pdf").fileUrl("/files/ruhsat.pdf").build();

        when(storeAccessService.requireOwnedStore(99L, 5L)).thenReturn(selectedStore);
        when(documentRepository.findById(8L)).thenReturn(Optional.of(document));

        assertThrows(BusinessException.class,
                () -> documentService.deleteDocument(99L, 5L, 8L));
        verify(documentRepository, never()).delete(document);
    }

    @Test
    void foreignStoreDocumentsAreRejectedBeforeListing() {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Mağaza", 5L))
                .when(storeAccessService).requireOwnedStore(99L, 5L);

        assertThrows(ResourceNotFoundException.class, () -> documentService.getDocuments(99L, 5L));

        verify(documentRepository, never()).findByStoreId(5L);
    }

    @Test
    void adminReviewStoresNoteInAuditAndNotifiesSeller() {
        User reviewer = User.builder().email("admin@example.com").password("x").build(); reviewer.setId(1L);
        User sellerUser = User.builder().email("seller@example.com").password("x").build(); sellerUser.setId(2L);
        Store store = Store.builder().name("Mağaza").seller(SellerProfile.builder().user(sellerUser).build()).build(); store.setId(5L);
        SellerDocument document = SellerDocument.builder().store(store).documentType("FOOD_LICENSE")
                .fileName("ruhsat.pdf").fileUrl("stored.pdf").verificationStatus("PENDING").build(); document.setId(8L);
        when(documentRepository.findById(8L)).thenReturn(Optional.of(document));
        when(userRepository.findById(1L)).thenReturn(Optional.of(reviewer));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        documentService.reviewDocument(1L, 8L, true, "Belge ve tarih bilgileri doğrulandı.");

        assertThat(document.getVerificationStatus()).isEqualTo("VERIFIED");
        verify(auditLogRepository).save(argThat(a -> a.getActorId().equals(1L)
                && a.getNewValue().contains("Belge ve tarih bilgileri doğrulandı.")));
        verify(notificationRepository).save(argThat(n -> n.getUser().equals(sellerUser)));
    }

    @Test
    void expiredVerifiedDocumentIsReturnedAsExpiredAndNotVerified() {
        Store store = Store.builder().name("Mağaza").build();
        store.setId(5L);
        SellerDocument document = SellerDocument.builder().store(store).documentType("FOOD_LICENSE")
                .fileName("ruhsat.pdf").fileUrl("stored.pdf").verificationStatus("VERIFIED")
                .verified(true).expiryDate(com.mealflex.subscription.service.SubscriptionDatePolicy.today().minusDays(1)).build();
        when(storeAccessService.requireOwnedStore(99L, 5L)).thenReturn(store);
        when(documentRepository.findByStoreId(5L)).thenReturn(List.of(document));

        var response = documentService.getDocuments(99L, 5L).getFirst();

        assertThat(response.getVerificationStatus()).isEqualTo("EXPIRED");
        assertThat(response.isVerified()).isFalse();
    }
}
