package com.mealflex.menu.service;

import com.mealflex.menu.entity.*;
import com.mealflex.menu.repository.*;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.StoreRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuMediaServiceTest {
    @Mock MenuRepository menus;
    @Mock MenuItemRepository items;
    @Mock MenuGalleryImageRepository gallery;
    @Mock StoreRepository stores;
    @Mock MenuService menuService;
    @InjectMocks MenuMediaService service;
    @TempDir Path root;

    @BeforeEach void setup() { ReflectionTestUtils.setField(service, "uploadDir", root.toString()); }
    @AfterEach void cleanupSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    private Menu ownedMenu() {
        Store store = Store.builder().name("Owned").build(); store.setId(3L);
        Menu menu = Menu.builder().store(store).name("Menu").build(); menu.setId(7L);
        when(menus.findById(7L)).thenReturn(Optional.of(menu));
        when(stores.findByIdAndSellerUserIdAndDeletedAtIsNull(3L, 1L)).thenReturn(Optional.of(store));
        return menu;
    }
    @Test void rolledBackUploadRemovesOnlyTheNewFile() throws Exception {
        ownedMenu();
        TransactionSynchronizationManager.initSynchronization();
        service.uploadGallery(1L, 7L, List.of(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1,2,3})));
        Path folder = root.resolve("menus/7");
        try (var files = Files.list(folder)) { assertThat(files.count()).isEqualTo(1); }
        for (var callback : TransactionSynchronizationManager.getSynchronizations())
            callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        try (var files = Files.list(folder)) { assertThat(files.count()).isZero(); }
    }
    @Test void deletingImageWaitsForCommit() throws Exception {
        Menu menu = ownedMenu();
        Path folder = Files.createDirectories(root.resolve("menus/7"));
        Path file = Files.createFile(folder.resolve("gallery-abcd.png"));
        var image = MenuGalleryImage.builder().menu(menu).imageUrl("/api/v1/menus/media/7/gallery-abcd.png").build();
        when(gallery.findByIdAndMenuIdAndDeletedAtIsNull(8L,7L)).thenReturn(Optional.of(image));
        TransactionSynchronizationManager.initSynchronization();
        service.deleteGalleryImage(1L,7L,8L);
        assertThat(file).exists();
        for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCommit();
        assertThat(file).doesNotExist();
    }
    @Test void foreignSellerCannotUploadOrQueryGallery() {
        Store store = Store.builder().build(); store.setId(3L);
        Menu menu = Menu.builder().store(store).build(); menu.setId(7L);
        when(menus.findById(7L)).thenReturn(Optional.of(menu));
        assertThatThrownBy(() -> service.uploadGallery(1L,7L,List.of()))
                .isInstanceOf(com.mealflex.common.exception.ResourceNotFoundException.class);
        verifyNoInteractions(gallery, items, menuService);
    }
    @Test void missingContentTypeReturnsValidationError() {
        ownedMenu();
        assertThatThrownBy(() -> service.uploadGallery(1L,7L,List.of(new MockMultipartFile("file",new byte[]{1}))))
                .isInstanceOf(com.mealflex.common.exception.BusinessException.class);
        verify(gallery,never()).save(any());
    }
}
