package com.mealflex.menu.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.menu.dto.MenuVersionResponse;
import com.mealflex.menu.entity.*;
import com.mealflex.menu.repository.*;
import com.mealflex.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MenuVersionService {
    private final MenuVersionRepository versionRepository;
    private final MenuScheduleVersionRepository scheduleVersionRepository;
    private final MenuItemRepository itemRepository;
    private final MenuGalleryImageRepository galleryImageRepository;
    private final MenuScheduleRepository scheduleRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public MenuVersion capture(Menu menu, Long userId) {
        int next = versionRepository.findFirstByMenuIdOrderByVersionNumberDesc(menu.getId())
                .map(value -> value.getVersionNumber() + 1).orElse(1);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", menu.getName()); snapshot.put("description", menu.getDescription());
        snapshot.put("imageUrl", menu.getImageUrl()); snapshot.put("allergenInfo", menu.getAllergenInfo());
        snapshot.put("galleryImages", galleryImageRepository.findByMenuIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(menu.getId()).stream()
                .map(image -> Map.of("imageUrl", image.getImageUrl(), "sortOrder", image.getSortOrder())).toList());
        snapshot.put("dietTags", menu.getDietTags()); snapshot.put("allergens", menu.getAllergens());
        snapshot.put("items", itemRepository.findByMenuIdOrderBySortOrder(menu.getId()).stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>(); value.put("name", item.getName());
            value.put("description", item.getDescription()); value.put("imageUrl", item.getImageUrl()); value.put("sortOrder", item.getSortOrder()); return value;
        }).toList());
        MenuVersion version = versionRepository.save(MenuVersion.builder().menu(menu).versionNumber(next)
                .effectiveFrom(menu.getPriceEffectiveFrom() == null ? LocalDate.now() : menu.getPriceEffectiveFrom())
                .pricePerPerson(menu.getPricePerPerson()).snapshotJson(json(snapshot)).createdBy(userId).build());
        var schedules = scheduleRepository.findByMenuIdOrderByDayOfWeekAscSortOrderAsc(menu.getId()).stream().map(item ->
                Map.of("dayOfWeek", (Object) item.getDayOfWeek().name(), "itemName", item.getItemName(), "sortOrder", item.getSortOrder())).toList();
        scheduleVersionRepository.save(MenuScheduleVersion.builder().menuVersion(version).snapshotJson(json(schedules)).build());
        return version;
    }

    @Transactional
    public MenuVersion latestOrCapture(Menu menu, Long userId) {
        return versionRepository.findFirstByMenuIdOrderByVersionNumberDesc(menu.getId()).orElseGet(() -> capture(menu, userId));
    }

    @Transactional
    public MenuVersion forSubscription(Menu menu, LocalDate startDate, Long userId) {
        MenuVersion latest = latestOrCapture(menu, userId);
        return versionRepository.findFirstByMenuIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionNumberDesc(menu.getId(), startDate)
                .orElseThrow(() -> new BusinessException("MENU_PRICE_NOT_EFFECTIVE", "Bu menünün seçilen başlangıç tarihi için geçerli bir fiyatı yok."));
    }

    @Transactional(readOnly = true)
    public String scheduleSnapshot(MenuVersion version) {
        return scheduleVersionRepository.findByMenuVersionId(version.getId()).map(MenuScheduleVersion::getSnapshotJson).orElse("[]");
    }

    @Transactional(readOnly = true)
    public List<MenuVersionResponse> list(Menu menu) {
        return versionRepository.findByMenuIdOrderByVersionNumberDesc(menu.getId()).stream()
                .map(value -> new MenuVersionResponse(value.getId(), value.getVersionNumber(), value.getEffectiveFrom(),
                        value.getPricePerPerson(), value.getSnapshotJson(), subscriptionRepository.countByMenuVersionId(value.getId()), value.getCreatedAt()))
                .toList();
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new BusinessException("MENU_SNAPSHOT_FAILED", "Menü sürümü oluşturulamadı."); }
    }
}
