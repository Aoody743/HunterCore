package org.huntercore.api.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable location within a registered GUI flow.
 *
 * <p>Screen ids deliberately use a stable, namespaced-friendly identifier format. State that can
 * change between renders belongs in {@link #arguments()}, not in an inventory title or item name.</p>
 *
 * @param screenId stable screen identifier
 * @param arguments immutable string arguments for that screen
 */
public record HunterGuiRoute(@NotNull String screenId, @NotNull Map<String, String> arguments) {
    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9][a-z0-9._:/-]*");

    public HunterGuiRoute {
        screenId = requireStableId("screenId", screenId);
        arguments = Map.copyOf(arguments);
        for (final Map.Entry<String, String> entry : arguments.entrySet()) {
            requireArgumentKey(entry.getKey());
            Objects.requireNonNull(entry.getValue(), "route argument value");
        }
    }

    /**
     * Creates a route with no arguments.
     *
     * @param screenId stable screen identifier
     * @return the route
     */
    public static @NotNull HunterGuiRoute of(@NotNull final String screenId) {
        return new HunterGuiRoute(screenId, Map.of());
    }

    /**
     * Creates a route with immutable string arguments.
     *
     * @param screenId stable screen identifier
     * @param arguments route arguments
     * @return the route
     */
    public static @NotNull HunterGuiRoute of(@NotNull final String screenId, @NotNull final Map<String, String> arguments) {
        return new HunterGuiRoute(screenId, arguments);
    }

    /**
     * Gets a route argument without exposing the backing map.
     *
     * @param key argument key
     * @return the argument when present
     */
    public @NotNull Optional<String> argument(@NotNull final String key) {
        return Optional.ofNullable(this.arguments.get(key));
    }

    /**
     * Returns a copy of this route with one argument added or replaced.
     *
     * @param key argument key
     * @param value argument value
     * @return the derived route
     */
    public @NotNull HunterGuiRoute withArgument(@NotNull final String key, @NotNull final String value) {
        final Map<String, String> updated = new LinkedHashMap<>(this.arguments);
        updated.put(key, value);
        return new HunterGuiRoute(this.screenId, updated);
    }

    static @NotNull String requireStableId(@NotNull final String field, @NotNull final String value) {
        Objects.requireNonNull(value, field);
        if (!STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase stable id: " + value);
        }
        return value;
    }

    private static void requireArgumentKey(@NotNull final String key) {
        requireStableId("route argument key", key);
    }
}
