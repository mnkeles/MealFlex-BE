package com.mealflex.common.validation;

import com.mealflex.common.exception.BusinessException;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Müşteriye gösterilen ret gerekçeleri için merkezi içerik politikası. */
public final class RejectionReasonPolicy {
    private static final Set<String> OFFENSIVE_WORDS = Set.of(
            "amk", "aq", "aptal", "gerizekali", "it", "mal", "orospu", "pic",
            "salak", "serefsiz", "siktir", "sikik", "yavsak");
    private static final Set<String> OFFENSIVE_PHRASES = Set.of(
            "geri zekali", "siktir git");

    private RejectionReasonPolicy() {
    }

    public static String validateAndNormalize(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BusinessException("INVALID_REJECTION_REASON",
                    "Ret gerekçesi zorunludur ve en fazla 500 karakter olabilir.");
        }
        String cleaned = reason.trim().replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ");
        if (containsInappropriateContent(cleaned)) {
            throw new BusinessException("INAPPROPRIATE_REJECTION_REASON",
                    "Ret gerekçesi hakaret veya uygunsuz ifade içeremez. Lütfen profesyonel ve açıklayıcı bir gerekçe yazın.");
        }
        return cleaned;
    }

    public static boolean containsInappropriateContent(String value) {
        if (value == null || value.isBlank()) return false;
        String canonical = canonicalize(value);
        Set<String> tokens = Arrays.stream(canonical.split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank())
                .map(RejectionReasonPolicy::collapseRepeatedLetters)
                .collect(Collectors.toSet());
        String collapsedText = Arrays.stream(canonical.split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank())
                .map(RejectionReasonPolicy::collapseRepeatedLetters)
                .collect(Collectors.joining(" "));
        return tokens.stream().anyMatch(OFFENSIVE_WORDS::contains)
                || OFFENSIVE_PHRASES.stream().anyMatch(phrase -> containsPhrase(collapsedText, phrase));
    }

    public static String maskInappropriateRejectionReason(String message) {
        if (message == null) return null;
        int marker = message.lastIndexOf("Neden:");
        if (marker < 0) return message;
        String reason = message.substring(marker + "Neden:".length()).trim();
        if (!containsInappropriateContent(reason)) return message;
        return message.substring(0, marker) + "Neden: Uygunsuz ifade gizlendi.";
    }

    private static String canonicalize(String value) {
        String turkishFolded = value.toLowerCase(Locale.forLanguageTag("tr"))
                .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
        return Normalizer.normalize(turkishFolded, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    private static String collapseRepeatedLetters(String token) {
        return token.replaceAll("(.)\\1{2,}", "$1");
    }

    private static boolean containsPhrase(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }
}
