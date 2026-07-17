package net.momirealms.craftengine.core.font;

import net.kyori.adventure.text.Component;

public record EmojiComponentProcessResult(boolean changed, Component newText) {

    public static EmojiComponentProcessResult success(Component newText) {
        return new EmojiComponentProcessResult(true, newText);
    }

    public static EmojiComponentProcessResult failed() {
        return new EmojiComponentProcessResult(false, null);
    }
}