package com.mealflex.support.controller;

import com.mealflex.security.UserPrincipal;
import com.mealflex.support.dto.CreateSupportRequest;
import com.mealflex.support.dto.SupportRequestResponse;
import com.mealflex.support.service.SupportRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/support/requests")
@RequiredArgsConstructor
public class SupportRequestController {
    private final SupportRequestService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupportRequestResponse create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSupportRequest request) {
        return service.create(principal.getId(), request);
    }
}
