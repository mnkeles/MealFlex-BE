package com.mealflex.menu.service;

import com.mealflex.menu.repository.MenuGalleryImageRepository;
import com.mealflex.menu.repository.MenuItemRepository;
import com.mealflex.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuMediaCleanupService {
    private static final Pattern MANAGED_FILE = Pattern.compile("(menu|item|gallery)-[a-f0-9-]+\\.(jpg|png|webp)");
    private final MenuRepository menus;
    private final MenuItemRepository items;
    private final MenuGalleryImageRepository gallery;

    @Value("${app.upload-dir:uploads}") private String uploadDir;
    @Value("${app.media-cleanup-grace:PT24H}") private Duration grace;

    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Istanbul")
    public void cleanup() {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize().resolve("menus");
        if (!Files.isDirectory(root)) return;
        Set<Path> referenced = referencedFiles(root);
        Instant cutoff = Instant.now().minus(grace);
        try (Stream<Path> paths = Files.walk(root, 2)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> MANAGED_FILE.matcher(path.getFileName().toString()).matches())
                    .filter(path -> !referenced.contains(path.toAbsolutePath().normalize()))
                    .filter(path -> olderThan(path, cutoff))
                    .forEach(this::deleteSafely);
        } catch (IOException exception) {
            log.error("Menu media cleanup could not scan {}", root, exception);
        }
    }

    private Set<Path> referencedFiles(Path root) {
        Set<Path> result = new HashSet<>();
        menus.findAll().forEach(menu -> addReference(result, root, menu.getImageUrl()));
        items.findAll().forEach(item -> addReference(result, root, item.getImageUrl()));
        gallery.findAll().forEach(image -> addReference(result, root, image.getImageUrl()));
        return result;
    }

    private void addReference(Set<Path> result, Path root, String url) {
        if (url == null) return;
        String marker = "/api/v1/menus/media/";
        int index = url.indexOf(marker);
        if (index < 0) return;
        String relative = url.substring(index + marker.length());
        Path path = root.resolve(relative).toAbsolutePath().normalize();
        if (path.startsWith(root) && MANAGED_FILE.matcher(path.getFileName().toString()).matches()) result.add(path);
    }

    private boolean olderThan(Path path, Instant cutoff) {
        try { return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff); }
        catch (IOException exception) { log.warn("Could not inspect menu media {}", path, exception); return false; }
    }

    private void deleteSafely(Path path) {
        try { Files.deleteIfExists(path); log.info("Deleted orphan menu media {}", path); }
        catch (IOException exception) { log.warn("Could not delete orphan menu media {}", path, exception); }
    }
}
