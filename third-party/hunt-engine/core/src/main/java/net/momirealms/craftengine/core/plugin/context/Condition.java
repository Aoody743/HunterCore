package net.momirealms.craftengine.core.plugin.context;

import java.util.function.Predicate;

public interface Condition<CTX extends Context> extends Predicate<CTX> {

    @Override
    default boolean test(CTX ctx) {
        return false;
    }
}
