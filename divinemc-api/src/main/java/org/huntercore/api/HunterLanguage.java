package org.huntercore.api;

import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HunterLanguage {
    public static final String ZH_CN = "zh_cn";
    public static final String EN_US = "en_us";
    public static final String DEFAULT = ZH_CN;

    private HunterLanguage() {
    }

    public static @NotNull List<String> supportedLanguages() {
        return List.of(ZH_CN, EN_US);
    }

    public static @NotNull String normalize(@Nullable final String input) {
        final String normalized = normalizeOrNull(input);
        return normalized == null ? DEFAULT : normalized;
    }

    public static @Nullable String normalizeOrNull(@Nullable final String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        final String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "zh", "cn", "zh_cn", "zh_hans", "zh_hans_cn", "chinese", "simplified_chinese", "中文", "简体中文" -> ZH_CN;
            case "en", "en_us", "en_gb", "english" -> EN_US;
            default -> null;
        };
    }

    public static boolean isChinese(@Nullable final String language) {
        return normalize(language).equals(ZH_CN);
    }

    public static @NotNull String choose(@Nullable final String language, @NotNull final String zhCn, @NotNull final String enUs) {
        return isChinese(language) ? zhCn : enUs;
    }
}
