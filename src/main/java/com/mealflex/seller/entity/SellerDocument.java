package com.mealflex.seller.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "seller_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private String documentType;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileUrl;

    @Column
    private LocalDate expiryDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(nullable = false)
    @Builder.Default
    private String verificationStatus = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    private java.time.Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private com.mealflex.user.entity.User reviewedBy;

    private Long fileSize;
    private String contentType;
    private String storageName;
}
