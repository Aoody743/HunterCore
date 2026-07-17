package net.momirealms.craftengine.core.font;

public record EmojiTextProcessResult(boolean replaced, String text) {

    public static EmojiTextProcessResult replaced(String text) {
        return new EmojiTextProcessResult(true, text);
    }

    public static EmojiTextProcessResult notReplaced(String text) {
        return new EmojiTextProcessResult(false, text);
    }
}
