package com.mealflex.auth.dto;

import com.mealflex.user.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Ad alanı zorunludur.")
    private String firstName;

    @NotBlank(message = "Soyad alanı zorunludur.")
    private String lastName;

    @NotBlank(message = "E-posta alanı zorunludur.")
    @Email(message = "Geçerli bir e-posta adresi giriniz.")
    private String email;

    @NotBlank(message = "Şifre alanı zorunludur.")
    @Size(min = 8, message = "Şifre en az 8 karakter olmalıdır.")
    private String password;

    private String phone;

    @NotNull(message = "Kullanıcı rolü zorunludur.")
    private Role role;
}
