package com.mealflex.seller.repository;

import com.mealflex.seller.entity.SellerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SellerDocumentRepository extends JpaRepository<SellerDocument, Long> {

    List<SellerDocument> findByStoreId(Long storeId);
    List<SellerDocument> findByStoreIdAndVerificationStatus(Long storeId, String verificationStatus);
}
