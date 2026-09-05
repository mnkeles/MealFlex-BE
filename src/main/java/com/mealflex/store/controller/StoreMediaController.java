package com.mealflex.store.controller;

import com.mealflex.store.service.StoreMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/v1/stores/media")
@RequiredArgsConstructor
public class StoreMediaController {

    private final StoreMediaService storeMediaService;

    @GetMapping("/{storeId}/{fileName}")
    public ResponseEntity<Resource> getImage(@PathVariable Long storeId, @PathVariable String fileName) {
        Resource resource = storeMediaService.load(storeId, fileName);
        MediaType mediaType = fileName.endsWith(".png") ? MediaType.IMAGE_PNG :
                fileName.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .contentType(mediaType)
                .body(resource);
    }
}
