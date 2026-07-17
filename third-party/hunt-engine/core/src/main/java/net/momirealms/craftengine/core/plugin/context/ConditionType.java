package net.momirealms.craftengine.core.plugin.context;

import net.momirealms.craftengine.core.plugin.context.condition.ConditionFactory;
import net.momirealms.craftengine.core.util.Key;

public abstract class ConditionType<CTX extends Context, T extends Condition<CTX>> {
    protected final Key id;
    protected final ConditionFactory<CTX, T> factory;

    public ConditionType(Key id, ConditionFactory<CTX, T> factory) {
        this.id = id;
        this.factory = factory;
    }

    public Key id() {
        return id;
    }

    public ConditionFactory<CTX, T> factory() {
        return factory;
    }
}
