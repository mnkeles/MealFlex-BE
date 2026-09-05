package com.mealflex.seller.controller;

import com.mealflex.seller.dto.BankCatalogResponse;
import com.mealflex.seller.repository.BankCatalogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/seller/banks")
@RequiredArgsConstructor
@Tag(name = "Seller Banks", description = "Satıcı banka kataloğu")
public class SellerBankCatalogController {

    private final BankCatalogRepository bankCatalogRepository;

    @GetMapping
    @Operation(summary = "Aktif bankaları getir")
    public ResponseEntity<List<BankCatalogResponse>> getActiveBanks() {
        List<BankCatalogResponse> banks = bankCatalogRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(bank -> new BankCatalogResponse(
                        bank.getId(), bank.getName(), bank.getLegalName(), bank.getBankType()))
                .toList();
        return ResponseEntity.ok(banks);
    }
}
