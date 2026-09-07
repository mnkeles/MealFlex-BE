package com.mealflex.menu.service;

import com.mealflex.menu.entity.Menu;
import com.mealflex.menu.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MenuMediaCleanupServiceTest {
    @TempDir Path temp;

    @Test void deletesOnlyOldUnreferencedManagedFiles() throws Exception {
        MenuRepository menus = mock(MenuRepository.class);
        MenuItemRepository items = mock(MenuItemRepository.class);
        MenuGalleryImageRepository gallery = mock(MenuGalleryImageRepository.class);
        Path folder = Files.createDirectories(temp.resolve("menus/7"));
        Path referenced = Files.createFile(folder.resolve("gallery-aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png"));
        Path orphan = Files.createFile(folder.resolve("item-bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg"));
        Path recent = Files.createFile(folder.resolve("menu-cccccccc-cccc-cccc-cccc-cccccccccccc.webp"));
        Path unmanaged = Files.createFile(folder.resolve("notes.txt"));
        Files.setLastModifiedTime(referenced, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
        Files.setLastModifiedTime(orphan, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
        Menu menu = Menu.builder().imageUrl("/api/v1/menus/media/7/" + referenced.getFileName()).build();
        when(menus.findAll()).thenReturn(List.of(menu));
        when(items.findAll()).thenReturn(List.of()); when(gallery.findAll()).thenReturn(List.of());
        MenuMediaCleanupService service = new MenuMediaCleanupService(menus, items, gallery);
        ReflectionTestUtils.setField(service, "uploadDir", temp.toString());
        ReflectionTestUtils.setField(service, "grace", Duration.ofHours(24));

        service.cleanup();

        assertThat(referenced).exists();
        assertThat(orphan).doesNotExist();
        assertThat(recent).exists();
        assertThat(unmanaged).exists();
    }
}
