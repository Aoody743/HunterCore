package net.momirealms.craftengine.core.plugin.context;

import net.momirealms.craftengine.core.plugin.context.function.Function;
import net.momirealms.craftengine.core.plugin.context.function.FunctionFactory;
import net.momirealms.craftengine.core.util.Key;

public class FunctionType<CTX extends Context, T extends Function<CTX>> {
    private final Key id;
    private final FunctionFactory<CTX, T> factory;

    public FunctionType(Key id, FunctionFactory<CTX, T> factory) {
        this.id = id;
        this.factory = factory;
    }

    public Key id() {
        return id;
    }

    public FunctionFactory<CTX, T> factory() {
        return factory;
    }
}
