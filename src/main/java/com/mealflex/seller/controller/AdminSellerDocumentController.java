package com.mealflex.seller.controller;

import com.mealflex.security.UserPrincipal;
import com.mealflex.seller.dto.AdminSellerDocumentResponse;
import com.mealflex.seller.dto.DocumentResponse;
import com.mealflex.seller.service.SellerDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/admin/seller-documents")
@RequiredArgsConstructor
public class AdminSellerDocumentController {
    private final SellerDocumentService service;

    @GetMapping
    public List<AdminSellerDocumentResponse> list(@RequestParam(required = false) String status) {
        return service.getDocumentsForAdmin(status);
    }

    @GetMapping("/{documentId}/file")
    public ResponseEntity<Resource> file(@PathVariable Long documentId) {
        Resource resource = service.loadFileForAdmin(documentId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"seller-document\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(resource);
    }

    @PatchMapping("/{documentId}/review")
    public ResponseEntity<DocumentResponse> review(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long documentId, @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(service.reviewDocument(principal.getId(), documentId, request.approve(), request.reason()));
    }

    @GetMapping("/{documentId}/history")
    public ResponseEntity<List<java.util.Map<String, Object>>> history(@PathVariable Long documentId) {
        return ResponseEntity.ok(service.getReviewHistory(documentId));
    }

    public record ReviewRequest(boolean approve, String reason) {}
}
