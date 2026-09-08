package com.mealflex.demand.entity;

import com.mealflex.address.entity.Address;
import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_demands", uniqueConstraints =
        @UniqueConstraint(name = "uk_service_demand_user_address", columnNames = {"user_id", "address_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceDemand extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "address_id", nullable = false) private Address address;
    @Column(nullable = false) private String city;
    @Column(nullable = false) private String district;
    private String neighborhood;
    @Column(nullable = false, length = 20) @Builder.Default private String status = "ACTIVE";
}
