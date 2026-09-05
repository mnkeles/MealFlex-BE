package com.mealflex.menu.controller;

import com.mealflex.menu.dto.MenuResponse;
import com.mealflex.menu.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/stores/{storeId}/menus")
@RequiredArgsConstructor
@Tag(name = "Menus", description = "Mağaza menüleri (public)")
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    @Operation(summary = "Mağaza aktif menülerini listele")
    public ResponseEntity<List<MenuResponse>> getMenus(@PathVariable Long storeId) {
        return ResponseEntity.ok(menuService.getActiveMenusByStore(storeId));
    }

    @GetMapping("/{menuId}")
    @Operation(summary = "Menü detayı")
    public ResponseEntity<MenuResponse> getMenu(@PathVariable Long storeId,
                                                 @PathVariable Long menuId) {
        return ResponseEntity.ok(menuService.getActiveStoreMenu(storeId, menuId));
    }

}
