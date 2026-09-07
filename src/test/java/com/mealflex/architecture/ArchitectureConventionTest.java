package com.mealflex.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureConventionTest {
    private static final Path MAIN = Path.of("src", "main", "java");

    @Test
    void businessCodeUsesTheIstanbulDatePolicy() throws IOException {
        try (Stream<Path> sources = Files.walk(MAIN)) {
            var violations = sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith(Path.of("subscription", "service", "SubscriptionDatePolicy.java")))
                    .filter(path -> read(path).contains("LocalDate.now("))
                    .map(Path::toString).toList();
            assertThat(violations).as("Çıplak LocalDate.now() kullanımları").isEmpty();
        }
    }

    @Test
    void controllerEnumParsingHandlesInvalidInput() throws IOException {
        Set<String> enumTypes = new HashSet<>();
        Pattern enumDeclaration = Pattern.compile("\\benum\\s+(\\w+)");
        try (Stream<Path> sources = Files.walk(MAIN)) {
            sources.filter(path -> path.toString().endsWith(".java")).forEach(path ->
                    enumDeclaration.matcher(read(path)).results().map(result -> result.group(1)).forEach(enumTypes::add));
        }
        try (Stream<Path> sources = Files.walk(MAIN)) {
            var violations = sources.filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .filter(path -> {
                        String source = read(path);
                        return enumTypes.stream().anyMatch(type -> source.contains(type + ".valueOf("))
                                && !source.contains("catch (IllegalArgumentException");
                    }).map(Path::toString).toList();
            assertThat(violations).as("Korumasız controller Enum.valueOf kullanımları").isEmpty();
        }
    }

    @Test
    void controllersDoNotExposeJpaEntitiesAsReturnTypes() throws IOException {
        Set<String> entities = new HashSet<>();
        try (Stream<Path> sources = Files.walk(MAIN)) {
            sources.filter(path -> path.toString().replace('\\', '/').contains("/entity/") && path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString().replace(".java", ""))
                    .forEach(entities::add);
        }
        Pattern method = Pattern.compile("public\\s+([^({;]+?)\\s+\\w+\\s*\\(", Pattern.DOTALL);
        try (Stream<Path> sources = Files.walk(MAIN)) {
            var violations = sources.filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .flatMap(path -> method.matcher(read(path)).results()
                            .filter(result -> entities.stream().anyMatch(entity ->
                                    Pattern.compile("\\b" + Pattern.quote(entity) + "\\b").matcher(result.group(1)).find()))
                            .map(result -> path + " -> " + result.group(1).replaceAll("\\s+", " ")))
                    .toList();
            assertThat(violations).as("Controller entity dönüş tipleri").isEmpty();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
