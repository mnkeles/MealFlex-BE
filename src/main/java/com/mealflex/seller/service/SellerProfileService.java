package com.mealflex.seller.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.seller.dto.SellerProfileRequest;
import com.mealflex.seller.dto.SellerProfileResponse;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.seller.repository.BankCatalogRepository;
import com.mealflex.seller.repository.SellerProfileRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerProfileService {

    private final SellerProfileRepository sellerProfileRepository;
    private final BankCatalogRepository bankCatalogRepository;
    private final UserRepository userRepository;

    public SellerProfileResponse getProfile(Long userId) {
        SellerProfile profile = sellerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Satıcı Profili", "Profil bulunamadı"));
        return toResponse(profile);
    }

    public boolean profileExists(Long userId) {
        return sellerProfileRepository.existsByUserId(userId);
    }

    @Transactional
    public SellerProfileResponse createProfile(Long userId, SellerProfileRequest request) {
        if (sellerProfileRepository.existsByUserId(userId)) {
            throw new BusinessException("PROFILE_ALREADY_EXISTS",
                    "Zaten bir satıcı profiliniz bulunmaktadır.", HttpStatus.CONFLICT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));
        BankDetails bankDetails = validateBankDetails(request.getBankName(), request.getIban());

        SellerProfile profile = SellerProfile.builder()
                .user(user)
                .companyTitle(request.getCompanyTitle())
                .taxNumber(request.getTaxNumber())
                .taxOffice(request.getTaxOffice())
                .authorizedPerson(request.getAuthorizedPerson())
                .phone(request.getPhone())
                .bankName(bankDetails.bankName())
                .iban(bankDetails.iban())
                .build();

        profile = sellerProfileRepository.save(profile);
        log.info("Seller profile created for userId: {}", userId);
        return toResponse(profile);
    }

    @Transactional
    public SellerProfileResponse updateProfile(Long userId, SellerProfileRequest request) {
        SellerProfile profile = sellerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Satıcı Profili", "Profil bulunamadı"));
        BankDetails bankDetails = validateBankDetails(request.getBankName(), request.getIban());

        profile.setCompanyTitle(request.getCompanyTitle());
        profile.setAuthorizedPerson(request.getAuthorizedPerson());
        profile.setPhone(request.getPhone());
        profile.setBankName(bankDetails.bankName());
        profile.setIban(bankDetails.iban());

        profile = sellerProfileRepository.save(profile);
        return toResponse(profile);
    }

    private SellerProfileResponse toResponse(SellerProfile p) {
        return SellerProfileResponse.builder()
                .id(p.getId())
                .companyTitle(p.getCompanyTitle())
                .taxNumber(p.getTaxNumber())
                .taxOffice(p.getTaxOffice())
                .authorizedPerson(p.getAuthorizedPerson())
                .phone(p.getPhone())
                .bankName(p.getBankName())
                .iban(p.getIban())
                .build();
    }

    private BankDetails validateBankDetails(String bankName, String iban) {
        String normalizedBankName = bankName == null ? "" : bankName.trim();
        String normalizedIban = iban == null ? "" : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);

        if (normalizedBankName.isEmpty() && normalizedIban.isEmpty()) {
            return new BankDetails(null, null);
        }
        if (normalizedBankName.isEmpty() || normalizedIban.isEmpty()) {
            throw new BusinessException("BANK_DETAILS_REQUIRED",
                    "Banka adı ve IBAN birlikte girilmelidir.");
        }
        if (!bankCatalogRepository.existsByNameIgnoreCaseAndActiveTrue(normalizedBankName)) {
            throw new BusinessException("INVALID_BANK", "Lütfen listeden geçerli bir banka seçin.");
        }
        if (!isValidTurkishIban(normalizedIban)) {
            throw new BusinessException("INVALID_IBAN", "Geçerli bir Türkiye IBAN'ı girin.");
        }
        return new BankDetails(normalizedBankName, normalizedIban);
    }

    private boolean isValidTurkishIban(String iban) {
        if (!iban.matches("TR\\d{24}")) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        int remainder = 0;
        for (char character : rearranged.toCharArray()) {
            int value = Character.isDigit(character) ? character - '0' : character - 'A' + 10;
            if (value >= 10) {
                remainder = (remainder * 10 + value / 10) % 97;
            }
            remainder = (remainder * 10 + value % 10) % 97;
        }
        return remainder == 1;
    }

    private record BankDetails(String bankName, String iban) {
    }
}
