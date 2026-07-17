package org.huntercore.api.gui;

/** Outcome of opening or re-rendering a managed GUI route. */
public enum HunterGuiOpenResult {
    /** The requested view is active. */
    OPENED,
    /** HunterCore is absent or has not installed the GUI runtime. */
    UNAVAILABLE,
    /** The operation must be invoked on Bukkit's primary server thread. */
    WRONG_THREAD,
    /** The plugin registration was closed or disabled. */
    REGISTRATION_CLOSED,
    /** No screen in this registration owns the requested route id. */
    UNKNOWN_SCREEN,
    /** The player has no active session owned by this registration. */
    NO_ACTIVE_SESSION,
    /** Rendering or presentation resolution failed; the prior view remains active when possible. */
    RENDER_FAILED;

    /** @return whether a view was opened or re-rendered */
    public boolean opened() {
        return this == OPENED;
    }
}
