package net.momirealms.craftengine.core.entity.player;

public enum InteractionResult {
    FAIL(false),
    SUCCESS(true),
    PASS(false),
    TRY_EMPTY_HAND(false),
    SUCCESS_AND_CANCEL(true);

    private final boolean success;

    InteractionResult(boolean success) {
        this.success = success;
    }

    public boolean success() {
        return success;
    }
}
