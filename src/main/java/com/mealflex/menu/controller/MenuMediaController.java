package com.mealflex.menu.controller;

import com.mealflex.menu.service.MenuMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/menus/media")
@RequiredArgsConstructor
public class MenuMediaController {
    private final MenuMediaService menuMediaService;

    @GetMapping("/{menuId}/{fileName:.+}")
    public ResponseEntity<Resource> get(@PathVariable Long menuId, @PathVariable String fileName) {
        Resource resource = menuMediaService.load(menuId, fileName);
        String lower = fileName.toLowerCase();
        MediaType type = lower.endsWith(".png") ? MediaType.IMAGE_PNG : lower.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic()).body(resource);
    }
}
