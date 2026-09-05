package com.mealflex.user.service;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.customer.entity.CustomerProfile;
import com.mealflex.customer.repository.CustomerProfileRepository;
import com.mealflex.user.dto.UpdateProfileRequest;
import com.mealflex.user.dto.UserProfileResponse;
import com.mealflex.user.entity.Role;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));

        UserProfileResponse.UserProfileResponseBuilder builder = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified());

        if (user.getRole() == Role.CUSTOMER) {
            customerProfileRepository.findByUserId(userId).ifPresent(cp -> {
                builder.companyName(cp.getCompanyName());
                builder.taxNumber(cp.getTaxNumber());
                builder.taxOffice(cp.getTaxOffice());
                builder.invoiceAddress(cp.getInvoiceAddress());
            });
        }

        return builder.build();
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        if (!java.util.Objects.equals(user.getPhone(), request.getPhone())) {
            user.setPhoneVerified(false);
            user.setPhoneVerifiedAt(null);
        }
        user.setPhone(request.getPhone());
        userRepository.save(user);

        if (user.getRole() == Role.CUSTOMER) {
            CustomerProfile cp = customerProfileRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        CustomerProfile newCp = CustomerProfile.builder().user(user).build();
                        return customerProfileRepository.save(newCp);
                    });
            cp.setCompanyName(request.getCompanyName());
            cp.setTaxNumber(request.getTaxNumber());
            cp.setTaxOffice(request.getTaxOffice());
            cp.setInvoiceAddress(request.getInvoiceAddress());
            customerProfileRepository.save(cp);
        }

        return getProfile(userId);
    }
}
