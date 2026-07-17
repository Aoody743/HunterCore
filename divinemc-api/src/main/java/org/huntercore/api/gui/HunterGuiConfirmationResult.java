package org.huntercore.api.gui;

/** Result of consuming a one-time confirmation token. */
public enum HunterGuiConfirmationResult {
    /** The token was present, unexpired, and accepted by its revalidator. */
    CONFIRMED,
    /** No token with the requested id was pending. */
    MISSING,
    /** The token expired before it was consumed. */
    EXPIRED,
    /** The token was timely but its revalidator rejected the current state. */
    REJECTED;

    /** @return whether the confirmation may authorize its protected action */
    public boolean confirmed() {
        return this == CONFIRMED;
    }
}
