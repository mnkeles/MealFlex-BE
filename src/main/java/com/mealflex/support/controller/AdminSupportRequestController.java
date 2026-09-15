package com.mealflex.support.controller;

import com.mealflex.security.UserPrincipal;
import com.mealflex.support.dto.SupportRequestResponse;
import com.mealflex.support.dto.UpdateSupportRequest;
import com.mealflex.support.service.SupportRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/support-requests")
@RequiredArgsConstructor
public class AdminSupportRequestController {
    private final SupportRequestService service;

    @GetMapping
    public Page<SupportRequestResponse> list(@RequestParam(required = false) String status, Pageable pageable) {
        return service.listForAdmin(status, pageable);
    }

    @PutMapping("/{id}")
    public SupportRequestResponse update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody UpdateSupportRequest request) {
        return service.updateByAdmin(principal.getId(), id, request);
    }
}
