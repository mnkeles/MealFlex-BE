package com.mealflex.delivery.service;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.delivery.entity.DeliveryProof;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.DeliveryProofRepository;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryProofServiceTest {
    @Mock private DeliveryProofRepository proofs;
    @Mock private SubscriptionDeliveryRepository deliveries;
    @Mock private SellerStoreAccessService storeAccess;
    @InjectMocks private DeliveryProofService service;

    @Test
    void proofAccessIsRestrictedToTheDeliveryCustomer() {
        SubscriptionDelivery delivery = mock(SubscriptionDelivery.class, RETURNS_DEEP_STUBS);
        when(deliveries.findById(20L)).thenReturn(Optional.of(delivery));
        when(delivery.getSubscription().getCustomer().getId()).thenReturn(7L);

        assertThrows(ResourceNotFoundException.class, () -> service.accessForCustomer(8L, 20L));

        verify(proofs, never()).findByDeliveryId(anyLong());
    }

    @Test
    void proofAccessIssuesAnOpaqueFiveMinuteLink() {
        SubscriptionDelivery delivery = mock(SubscriptionDelivery.class, RETURNS_DEEP_STUBS);
        DeliveryProof proof = mock(DeliveryProof.class);
        Instant before = Instant.now();
        when(deliveries.findById(20L)).thenReturn(Optional.of(delivery));
        when(delivery.getSubscription().getCustomer().getId()).thenReturn(7L);
        when(proofs.findByDeliveryId(20L)).thenReturn(Optional.of(proof));
        when(proof.getId()).thenReturn(90L);

        var access = service.accessForCustomer(7L, 20L);

        assertThat(access.url()).contains("/delivery-proofs/90/file?token=");
        assertThat(access.expiresAt()).isAfter(before.plus(Duration.ofMinutes(4)))
                .isBefore(before.plus(Duration.ofMinutes(6)));
    }

    @Test
    void sellerCannotIssueProofLinkForAnotherStoresDelivery() {
        Store ownedStore = mock(Store.class);
        SubscriptionDelivery delivery = mock(SubscriptionDelivery.class, RETURNS_DEEP_STUBS);
        when(storeAccess.requireOwnedStore(8L, 5L)).thenReturn(ownedStore);
        when(deliveries.findById(20L)).thenReturn(Optional.of(delivery));
        when(delivery.getSubscription().getStore().getId()).thenReturn(6L);

        assertThrows(ResourceNotFoundException.class, () -> service.accessForSeller(8L, 5L, 20L));

        verify(proofs, never()).findByDeliveryId(anyLong());
    }

    @Test
    void uploadRejectsUnsupportedFileTypeBeforeItIsStored() {
        SubscriptionDelivery delivery = mock(SubscriptionDelivery.class, RETURNS_DEEP_STUBS);
        when(deliveries.findById(20L)).thenReturn(Optional.of(delivery));
        when(delivery.getSubscription().getStore().getId()).thenReturn(5L);
        MockMultipartFile file = new MockMultipartFile("file", "proof.gif", "image/gif", new byte[] {1, 2});

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.uploadForSeller(8L, 5L, 20L, file));

        assertThat(error.getCode()).isEqualTo("INVALID_DELIVERY_PROOF");
        verify(proofs, never()).save(any());
    }

    @Test
    void uploadRejectsFilesLargerThanTenMegabytesBeforeItIsStored() {
        SubscriptionDelivery delivery = mock(SubscriptionDelivery.class, RETURNS_DEEP_STUBS);
        when(deliveries.findById(20L)).thenReturn(Optional.of(delivery));
        when(delivery.getSubscription().getStore().getId()).thenReturn(5L);
        MockMultipartFile file = new MockMultipartFile("file", "proof.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.uploadForSeller(8L, 5L, 20L, file));

        assertThat(error.getCode()).isEqualTo("DELIVERY_PROOF_TOO_LARGE");
        verify(proofs, never()).save(any());
    }
}
