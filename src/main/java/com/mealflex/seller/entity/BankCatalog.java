package com.mealflex.seller.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bank_catalog")
@Getter
@NoArgsConstructor
public class BankCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "bank_type", nullable = false)
    private String bankType;

    @Column(nullable = false)
    private boolean active = true;
}
