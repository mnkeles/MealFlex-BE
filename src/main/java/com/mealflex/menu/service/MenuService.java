package com.mealflex.menu.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.menu.dto.CreateMenuRequest;
import com.mealflex.menu.dto.MenuResponse;
import com.mealflex.menu.dto.MenuVersionResponse;
import com.mealflex.menu.entity.Menu;
import com.mealflex.menu.entity.MenuGalleryImage;
import com.mealflex.menu.entity.MenuItem;
import com.mealflex.menu.repository.MenuItemRepository;
import com.mealflex.menu.repository.MenuGalleryImageRepository;
import com.mealflex.menu.repository.MenuRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuGalleryImageRepository menuGalleryImageRepository;
    private final StoreRepository storeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MenuVersionService menuVersionService;

    @Transactional(readOnly = true)
    public List<MenuResponse> getActiveMenusByStore(Long storeId) {
        return menuRepository.findByStoreIdAndActiveTrueAndDeletedAtIsNull(storeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MenuResponse> getAllMenusByStore(Long userId) {
        return sellerStores(userId).stream()
                .flatMap(store -> menuRepository.findByStoreIdAndDeletedAtIsNull(store.getId()).stream())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MenuResponse> getAllMenusByStore(Long userId, Long storeId) {
        storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(storeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));
        return menuRepository.findByStoreIdAndDeletedAtIsNull(storeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MenuResponse getMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResourceNotFoundException("Menü", menuId));
        return toResponse(menu);
    }

    @Transactional
    public MenuResponse createMenu(Long userId, CreateMenuRequest request) {
        List<Store> stores = sellerStores(userId);
        if (stores.size() != 1) {
            throw new BusinessException("STORE_SELECTION_REQUIRED",
                    "Menü oluşturmak için mağaza seçmelisiniz.");
        }
        Store store = stores.getFirst();

        Menu menu = Menu.builder()
                .store(store)
                .name(request.getName())
                .description(request.getDescription())
                .pricePerPerson(request.getPricePerPerson())
                .priceEffectiveFrom(com.mealflex.subscription.service.CommerceDateRules.menuEffectiveDate(request.getPriceEffectiveFrom(), com.mealflex.subscription.service.SubscriptionDatePolicy.today()))
                .allergenInfo(request.getAllergenInfo())
                .imageUrl(request.getImageUrl())
                .dietTags(validated(request.getDietTags(), com.mealflex.store.service.StoreService.DIET_TAGS, "Diyet etiketi"))
                .allergens(validated(request.getAllergens(), com.mealflex.store.service.StoreService.ALLERGENS, "Alerjen"))
                .build();

        menu = menuRepository.save(menu);

        if (request.getItems() != null) {
            for (int i = 0; i < request.getItems().size(); i++) {
                CreateMenuRequest.MenuItemRequest itemReq = request.getItems().get(i);
                MenuItem item = MenuItem.builder()
                        .menu(menu)
                        .name(itemReq.getName())
                        .description(itemReq.getDescription())
                        .imageUrl(itemReq.getImageUrl())
                        .sortOrder(itemReq.getSortOrder() != null ? itemReq.getSortOrder() : i)
                        .build();
                menuItemRepository.save(item);
            }
        }

        log.info("Menu created: {} for store: {}", menu.getName(), store.getName());
        return toResponse(menu);
    }

    @Transactional(readOnly = true)
    public MenuResponse getActiveStoreMenu(Long storeId, Long menuId) {
        Menu menu = menuRepository.findByIdAndStoreIdAndActiveTrueAndDeletedAtIsNull(menuId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Menü", menuId));
        return toResponse(menu);
    }

    @Transactional
    public MenuResponse createMenu(Long userId, Long storeId, CreateMenuRequest request) {
        Store store = storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(storeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));

        Menu menu = Menu.builder()
                .store(store)
                .name(request.getName())
                .description(request.getDescription())
                .pricePerPerson(request.getPricePerPerson())
                .priceEffectiveFrom(com.mealflex.subscription.service.CommerceDateRules.menuEffectiveDate(request.getPriceEffectiveFrom(), com.mealflex.subscription.service.SubscriptionDatePolicy.today()))
                .allergenInfo(request.getAllergenInfo())
                .imageUrl(request.getImageUrl())
                .dietTags(validated(request.getDietTags(), com.mealflex.store.service.StoreService.DIET_TAGS, "Diyet etiketi"))
                .allergens(validated(request.getAllergens(), com.mealflex.store.service.StoreService.ALLERGENS, "Alerjen"))
                .build();
        menu = menuRepository.save(menu);

        if (request.getItems() != null) {
            for (int i = 0; i < request.getItems().size(); i++) {
                CreateMenuRequest.MenuItemRequest itemReq = request.getItems().get(i);
                menuItemRepository.save(MenuItem.builder()
                        .menu(menu)
                        .name(itemReq.getName())
                        .description(itemReq.getDescription())
                        .imageUrl(itemReq.getImageUrl())
                        .sortOrder(itemReq.getSortOrder() != null ? itemReq.getSortOrder() : i)
                        .build());
            }
        }
        menuVersionService.capture(menu, userId);
        log.info("Menu created: {} for store: {}", menu.getName(), store.getName());
        return toResponse(menu);
    }

    @Transactional
    public MenuResponse updateMenu(Long userId, Long menuId, CreateMenuRequest request) {
        Menu menu = getMenuForSeller(userId, menuId);

        menu.setName(request.getName());
        menu.setDescription(request.getDescription());
        menu.setPricePerPerson(request.getPricePerPerson());
        menu.setPriceEffectiveFrom(com.mealflex.subscription.service.CommerceDateRules.menuEffectiveDate(request.getPriceEffectiveFrom(), com.mealflex.subscription.service.SubscriptionDatePolicy.today()));
        menu.setAllergenInfo(request.getAllergenInfo());
        if (request.getImageUrl() != null) menu.setImageUrl(request.getImageUrl());
        if (request.getDietTags() != null) menu.setDietTags(validated(request.getDietTags(), com.mealflex.store.service.StoreService.DIET_TAGS, "Diyet etiketi"));
        if (request.getAllergens() != null) menu.setAllergens(validated(request.getAllergens(), com.mealflex.store.service.StoreService.ALLERGENS, "Alerjen"));
        menu = menuRepository.save(menu);
        replaceMenuItems(menu, request.getItems());
        menuVersionService.capture(menu, userId);

        return toResponse(menu);
    }

    private void replaceMenuItems(Menu menu, List<CreateMenuRequest.MenuItemRequest> requestedItems) {
        if (requestedItems == null) return;

        List<MenuItem> currentItems = menuItemRepository.findByMenuIdOrderBySortOrder(menu.getId());
        Map<Long, MenuItem> itemsById = new HashMap<>();
        for (MenuItem item : currentItems) {
            itemsById.put(item.getId(), item);
        }
        Set<Long> requestedIds = new HashSet<>();

        for (int i = 0; i < requestedItems.size(); i++) {
            CreateMenuRequest.MenuItemRequest itemRequest = requestedItems.get(i);
            int sortOrder = itemRequest.getSortOrder() != null ? itemRequest.getSortOrder() : i;
            if (itemRequest.getId() == null) {
                menuItemRepository.save(MenuItem.builder()
                        .menu(menu)
                        .name(itemRequest.getName().trim())
                        .description(itemRequest.getDescription())
                        .imageUrl(itemRequest.getImageUrl())
                        .sortOrder(sortOrder)
                        .build());
                continue;
            }

            if (!requestedIds.add(itemRequest.getId())) {
                throw new BusinessException("DUPLICATE_MENU_ITEM", "Aynı yemek birden fazla kez gönderilemez.");
            }
            MenuItem item = itemsById.remove(itemRequest.getId());
            if (item == null) {
                throw new BusinessException("INVALID_MENU_ITEM", "Yemek bu menüye ait değil.", HttpStatus.BAD_REQUEST);
            }
            item.setName(itemRequest.getName().trim());
            item.setDescription(itemRequest.getDescription());
            if (itemRequest.getImageUrl() != null) item.setImageUrl(itemRequest.getImageUrl());
            item.setSortOrder(sortOrder);
            menuItemRepository.save(item);
        }

        menuItemRepository.deleteAll(itemsById.values());
    }

    @Transactional
    public MenuResponse toggleActive(Long userId, Long menuId) {
        Menu menu = getMenuForSeller(userId, menuId);

        if (menu.isActive()) {
            boolean hasActive = subscriptionRepository.existsByMenuIdAndStatusIn(menuId,
                    List.of(com.mealflex.subscription.entity.SubscriptionStatus.ACTIVE,
                            com.mealflex.subscription.entity.SubscriptionStatus.APPROVED,
                            com.mealflex.subscription.entity.SubscriptionStatus.PENDING_APPROVAL,
                            com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_SUSPENDED));
            if (hasActive) {
                throw new BusinessException("MENU_HAS_ACTIVE_SUBSCRIPTIONS",
                        "Aktif aboneliği olan menü pasife alınamaz.", HttpStatus.CONFLICT);
            }
        }

        menu.setActive(!menu.isActive());
        menu = menuRepository.save(menu);
        log.info("Menu #{} toggled to active={}", menuId, menu.isActive());
        return toResponse(menu);
    }

    @Transactional
    public void deleteMenu(Long userId, Long menuId) {
        Menu menu = getMenuForSeller(userId, menuId);

        boolean hasActive = subscriptionRepository.existsByMenuIdAndStatusIn(menuId,
                List.of(com.mealflex.subscription.entity.SubscriptionStatus.ACTIVE,
                        com.mealflex.subscription.entity.SubscriptionStatus.APPROVED,
                        com.mealflex.subscription.entity.SubscriptionStatus.PENDING_APPROVAL,
                        com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_SUSPENDED));
        if (hasActive) {
            throw new BusinessException("MENU_HAS_ACTIVE_SUBSCRIPTIONS",
                    "Aktif aboneliği olan menü silinemez.", HttpStatus.CONFLICT);
        }

        menu.setDeletedAt(java.time.Instant.now());
        menuRepository.save(menu);
        log.info("Menu #{} soft-deleted", menuId);
    }

    @Transactional
    public MenuResponse copyMenu(Long userId, Long menuId) {
        Menu source = getMenuForSeller(userId, menuId);
        Menu copy = menuRepository.save(Menu.builder().store(source.getStore()).name(source.getName() + " (Kopya)")
                .description(source.getDescription()).pricePerPerson(source.getPricePerPerson())
                .priceEffectiveFrom(source.getPriceEffectiveFrom()).imageUrl(source.getImageUrl())
                .allergenInfo(source.getAllergenInfo()).dietTags(new LinkedHashSet<>(source.getDietTags()))
                .allergens(new LinkedHashSet<>(source.getAllergens())).active(false).build());
        menuItemRepository.findByMenuIdOrderBySortOrder(source.getId()).forEach(item -> menuItemRepository.save(MenuItem.builder()
                .menu(copy).name(item.getName()).description(item.getDescription()).imageUrl(item.getImageUrl()).sortOrder(item.getSortOrder()).build()));
        menuVersionService.capture(copy, userId);
        return toResponse(copy);
    }

    @Transactional
    public List<MenuResponse> setBulkActive(Long userId, List<Long> menuIds, boolean active) {
        if (menuIds == null || menuIds.isEmpty()) throw new BusinessException("EMPTY_MENU_SELECTION", "En az bir menü seçin.");
        List<Menu> menus = menuIds.stream().distinct().map(id -> getMenuForSeller(userId, id)).toList();
        if (!active && menus.stream().anyMatch(menu -> subscriptionRepository.existsByMenuIdAndStatusIn(menu.getId(),
                List.of(com.mealflex.subscription.entity.SubscriptionStatus.ACTIVE, com.mealflex.subscription.entity.SubscriptionStatus.APPROVED, com.mealflex.subscription.entity.SubscriptionStatus.PENDING_APPROVAL, com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_SUSPENDED))))
            throw new BusinessException("MENU_HAS_ACTIVE_SUBSCRIPTIONS", "Aktif aboneliği olan menüler pasife alınamaz.", HttpStatus.CONFLICT);
        menus.forEach(menu -> menu.setActive(active)); menuRepository.saveAll(menus); return menus.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MenuVersionResponse> getVersions(Long userId, Long menuId) { return menuVersionService.list(getMenuForSeller(userId, menuId)); }

    private Menu getMenuForSeller(Long userId, Long menuId) {
        List<Store> stores = sellerStores(userId);
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResourceNotFoundException("Menü", menuId));
        boolean owned = stores.stream().anyMatch(s -> s.getId().equals(menu.getStore().getId()));
        if (!owned) {
            throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu menü size ait değil.", HttpStatus.FORBIDDEN);
        }
        return menu;
    }

    private List<Store> sellerStores(Long userId) {
        List<Store> stores = storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(userId);
        if (stores.isEmpty()) {
            throw new ResourceNotFoundException("Mağaza", "Satıcıya ait mağaza bulunamadı");
        }
        return stores;
    }

    private MenuResponse toResponse(Menu menu) {
        List<MenuResponse.MenuItemResponse> items = menuItemRepository
                .findByMenuIdOrderBySortOrder(menu.getId()).stream()
                .map(item -> MenuResponse.MenuItemResponse.builder()
                        .id(item.getId())
                        .name(item.getName())
                        .description(item.getDescription())
                        .imageUrl(item.getImageUrl())
                        .sortOrder(item.getSortOrder())
                        .build())
                .toList();
        List<MenuResponse.MenuGalleryImageResponse> galleryImages = menuGalleryImageRepository
                .findByMenuIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(menu.getId()).stream()
                .map(image -> MenuResponse.MenuGalleryImageResponse.builder()
                        .id(image.getId())
                        .imageUrl(image.getImageUrl())
                        .sortOrder(image.getSortOrder())
                        .build())
                .toList();

        return MenuResponse.builder()
                .id(menu.getId())
                .storeId(menu.getStore().getId())
                .name(menu.getName())
                .description(menu.getDescription())
                .pricePerPerson(menu.getPricePerPerson())
                .priceEffectiveFrom(menu.getPriceEffectiveFrom())
                .imageUrl(menu.getImageUrl())
                .galleryImages(galleryImages)
                .allergenInfo(menu.getAllergenInfo())
                .dietTags(menu.getDietTags())
                .allergens(menu.getAllergens())
                .active(menu.isActive())
                .items(items)
                .build();
    }

    private Set<String> validated(Set<String> values, Set<String> allowed, String label) {
        if (values == null) return new LinkedHashSet<>();
        if (!allowed.containsAll(values)) throw new BusinessException("INVALID_DISCOVERY_LABEL", label + " seçeneklerinden biri geçersiz.");
        return new LinkedHashSet<>(values);
    }
}
