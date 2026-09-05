package com.mealflex.delivery.entity;
import com.mealflex.common.entity.BaseEntity; import com.mealflex.store.entity.Store; import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="couriers") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Courier extends BaseEntity { @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="store_id", nullable=false) private Store store; @Column(nullable=false) private String fullName; private String phone; @Column(length=255) private String email; @Builder.Default private boolean active=true; }
