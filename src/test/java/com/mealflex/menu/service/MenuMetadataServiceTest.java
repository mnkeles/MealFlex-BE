package com.mealflex.menu.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.menu.dto.CreateMenuRequest;
import com.mealflex.menu.entity.Menu;
import com.mealflex.menu.entity.MenuItem;
import com.mealflex.menu.repository.*;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuMetadataServiceTest {
    @Mock MenuRepository menuRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock MenuGalleryImageRepository menuGalleryImageRepository;
    @Mock StoreRepository storeRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock MenuVersionService menuVersionService;
    @InjectMocks MenuService menuService;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"toggle", "delete", "bulk"})
    void pendingSubscriptionPreventsMenuRemoval(String action) {
        Store store = Store.builder().name("Owned").build(); store.setId(5L);
        Menu menu = Menu.builder().store(store).name("Menu").active(true).build(); menu.setId(7L);
        when(storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(9L)).thenReturn(List.of(store));
        when(menuRepository.findById(7L)).thenReturn(Optional.of(menu));
        when(subscriptionRepository.existsByMenuIdAndStatusIn(eq(7L), any()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(1))
                        .contains(com.mealflex.subscription.entity.SubscriptionStatus.PENDING_APPROVAL));
        assertThrows(BusinessException.class, () -> {
            if (action.equals("toggle")) menuService.toggleActive(9L, 7L);
            else if (action.equals("delete")) menuService.deleteMenu(9L, 7L);
            else menuService.setBulkActive(9L, List.of(7L), false);
        });
        assertThat(menu.isActive()).isTrue();
        assertThat(menu.getDeletedAt()).isNull();
        verify(menuRepository, never()).save(any());
        verify(menuRepository, never()).saveAll(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"update", "delete", "copy", "toggle"})
    void foreignMenuCannotBeModified(String action) {
        Store owned = Store.builder().name("Owned").build(); owned.setId(5L);
        Store foreign = Store.builder().name("Foreign").build(); foreign.setId(6L);
        Menu menu = Menu.builder().store(foreign).name("Private").active(true).build(); menu.setId(7L);
        when(storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(9L)).thenReturn(List.of(owned));
        when(menuRepository.findById(7L)).thenReturn(Optional.of(menu));
        assertThrows(BusinessException.class, () -> {
            switch (action) {
                case "update" -> menuService.updateMenu(9L, 7L, new CreateMenuRequest());
                case "delete" -> menuService.deleteMenu(9L, 7L);
                case "copy" -> menuService.copyMenu(9L, 7L);
                default -> menuService.toggleActive(9L, 7L);
            }
        });
        verify(menuRepository, never()).save(any());
        verifyNoInteractions(menuItemRepository, menuGalleryImageRepository, menuVersionService, subscriptionRepository);
    }

    @Test
    void unknownDietTagIsRejected() {
        CreateMenuRequest request = new CreateMenuRequest();
        request.setName("Öğle Menüsü");
        request.setPricePerPerson(BigDecimal.TEN);
        request.setDietTags(Set.of("BILINMEYEN_ETIKET"));

        assertThrows(BusinessException.class, () -> menuService.createMenu(9L, request));
        verify(menuRepository, never()).save(any());
    }

    @Test
    void foreignStoreMenuListIsRejectedBeforeQueryingMenus() {
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> menuService.getAllMenusByStore(9L, 5L));

        verify(menuRepository, never()).findByStoreId(anyLong());
    }

    @Test
    void creatingMenuCapturesExactlyOneVersion() {
        Store store = Store.builder().name("Test Mağazası").build();
        store.setId(5L);
        CreateMenuRequest request = new CreateMenuRequest();
        request.setName("Öğle Menüsü");
        request.setPricePerPerson(BigDecimal.TEN);
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 9L)).thenReturn(Optional.of(store));
        when(menuRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuItemRepository.findByMenuIdOrderBySortOrder(any())).thenReturn(java.util.List.of());

        menuService.createMenu(9L, 5L, request);

        verify(menuVersionService, times(1)).capture(any(), eq(9L));
    }

    @Test
    void menuAvailabilityEndCannotPrecedeStart() {
        Store store = Store.builder().name("Test Mağazası").build();
        store.setId(5L);
        CreateMenuRequest request = new CreateMenuRequest();
        request.setName("Dönem Menüsü");
        request.setPricePerPerson(BigDecimal.TEN);
        request.setAvailableFrom(LocalDate.of(2026, 9, 14));
        request.setAvailableUntil(LocalDate.of(2026, 9, 13));
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 9L))
                .thenReturn(Optional.of(store));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> menuService.createMenu(9L, 5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Menü bitiş tarihi başlangıç tarihinden önce olamaz.");
        verify(menuRepository, never()).save(any());
    }

    @Test
    void updatingMenuPreservesExistingMealIdentityAndImageWhileAddingNewVarieties() {
        Store store = Store.builder().name("Test Mağazası").build();
        store.setId(5L);
        Menu menu = Menu.builder().store(store).name("Eski Menü")
                .pricePerPerson(BigDecimal.valueOf(150)).build();
        menu.setId(7L);
        MenuItem existingItem = MenuItem.builder().menu(menu).name("Eski yemek")
                .imageUrl("/api/v1/menus/media/7/item-kept.jpg").sortOrder(0).build();
        existingItem.setId(11L);

        CreateMenuRequest.MenuItemRequest updatedSoup = new CreateMenuRequest.MenuItemRequest();
        updatedSoup.setId(11L);
        updatedSoup.setName("Mercimek Çorbası");
        updatedSoup.setDescription("Çorba");
        CreateMenuRequest.MenuItemRequest mainCourse = new CreateMenuRequest.MenuItemRequest();
        mainCourse.setName("Kuru Fasulye");
        mainCourse.setDescription("Ana yemek");

        CreateMenuRequest request = new CreateMenuRequest();
        request.setName("Haftalık Kurumsal Menü");
        request.setPricePerPerson(BigDecimal.valueOf(175));
        request.setItems(List.of(updatedSoup, mainCourse));

        when(storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(9L)).thenReturn(List.of(store));
        when(menuRepository.findById(7L)).thenReturn(Optional.of(menu));
        when(menuRepository.save(menu)).thenReturn(menu);
        when(menuItemRepository.findByMenuIdOrderBySortOrder(7L))
                .thenReturn(List.of(existingItem));

        menuService.updateMenu(9L, 7L, request);

        ArgumentCaptor<MenuItem> itemCaptor = ArgumentCaptor.forClass(MenuItem.class);
        verify(menuItemRepository, times(2)).save(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues()).extracting(MenuItem::getName)
                .containsExactly("Mercimek Çorbası", "Kuru Fasulye");
        assertThat(itemCaptor.getAllValues()).extracting(MenuItem::getDescription)
                .containsExactly("Çorba", "Ana yemek");
        assertThat(itemCaptor.getAllValues().getFirst().getId()).isEqualTo(11L);
        assertThat(itemCaptor.getAllValues().getFirst().getImageUrl())
                .isEqualTo("/api/v1/menus/media/7/item-kept.jpg");
        verify(menuItemRepository, never()).flush();
        verify(menuVersionService).capture(menu, 9L);
    }

    @Test
    void sellerCanUpdateMenuOfAnyOwnedStore() {
        Store firstStore = Store.builder().name("Birinci Mağaza").build();
        firstStore.setId(5L);
        Store secondStore = Store.builder().name("İkinci Mağaza").build();
        secondStore.setId(6L);
        Menu secondStoreMenu = Menu.builder().store(secondStore).name("İkinci Menü")
                .pricePerPerson(BigDecimal.valueOf(150)).build();
        secondStoreMenu.setId(8L);
        CreateMenuRequest request = new CreateMenuRequest();
        request.setName("Güncel İkinci Menü");
        request.setPricePerPerson(BigDecimal.valueOf(175));

        when(storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(9L))
                .thenReturn(List.of(firstStore, secondStore));
        when(menuRepository.findById(8L)).thenReturn(Optional.of(secondStoreMenu));
        when(menuRepository.save(secondStoreMenu)).thenReturn(secondStoreMenu);
        when(menuItemRepository.findByMenuIdOrderBySortOrder(8L)).thenReturn(List.of());

        menuService.updateMenu(9L, 8L, request);

        assertThat(secondStoreMenu.getName()).isEqualTo("Güncel İkinci Menü");
        assertThat(secondStoreMenu.getPricePerPerson()).isEqualByComparingTo("175");
        verify(menuVersionService).capture(secondStoreMenu, 9L);
    }

    @Test
    void menuCopyPreservesFoodVarietiesAndStartsInactiveWithItsOwnVersion() {
        Store store = Store.builder().name("Test Mağazası").build(); store.setId(5L);
        Menu source = Menu.builder().store(store).name("Ev Menüsü").description("Günlük menü")
                .pricePerPerson(BigDecimal.valueOf(150)).imageUrl("/menu.jpg").active(true).build(); source.setId(7L);
        MenuItem item = MenuItem.builder().menu(source).name("Çorba").sortOrder(0).imageUrl("/soup.jpg").build();
        when(storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(9L)).thenReturn(List.of(store));
        when(menuRepository.findById(7L)).thenReturn(Optional.of(source));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> { Menu copy = invocation.getArgument(0); copy.setId(8L); return copy; });
        when(menuItemRepository.findByMenuIdOrderBySortOrder(7L)).thenReturn(List.of(item));
        when(menuItemRepository.findByMenuIdOrderBySortOrder(8L)).thenReturn(List.of());

        menuService.copyMenu(9L, 7L);

        var copyCaptor = ArgumentCaptor.forClass(Menu.class);
        verify(menuRepository).save(copyCaptor.capture());
        assertThat(copyCaptor.getValue().getName()).isEqualTo("Ev Menüsü (Kopya)");
        assertThat(copyCaptor.getValue().isActive()).isFalse();
        verify(menuItemRepository).save(argThat(value -> value.getMenu().getId().equals(8L) && value.getName().equals("Çorba")));
        verify(menuVersionService).capture(copyCaptor.getValue(), 9L);
    }

    @Test
    void bulkDeactivationUpdatesOnlyOwnedMenusWhenNoActiveSubscriptionExists() {
        Store store = Store.builder().name("Test Mağazası").build(); store.setId(5L);
        Menu first = Menu.builder().store(store).name("Birinci").pricePerPerson(BigDecimal.TEN).active(true).build(); first.setId(7L);
        Menu second = Menu.builder().store(store).name("İkinci").pricePerPerson(BigDecimal.TEN).active(true).build(); second.setId(8L);
        when(storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(9L)).thenReturn(List.of(store));
        when(menuRepository.findById(7L)).thenReturn(Optional.of(first));
        when(menuRepository.findById(8L)).thenReturn(Optional.of(second));
        when(subscriptionRepository.existsByMenuIdAndStatusIn(anyLong(), any())).thenReturn(false);
        when(menuItemRepository.findByMenuIdOrderBySortOrder(anyLong())).thenReturn(List.of());

        menuService.setBulkActive(9L, List.of(7L, 8L), false);

        assertThat(first.isActive()).isFalse();
        assertThat(second.isActive()).isFalse();
        verify(menuRepository).saveAll(List.of(first, second));
    }
}
