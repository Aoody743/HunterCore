package net.momirealms.craftengine.core.plugin.context;

public class SimpleContext extends AbstractCommonContext {

    public SimpleContext(ContextHolder contexts) {
        super(contexts);
    }

    public static SimpleContext of(ContextHolder contexts) {
        return new SimpleContext(contexts);
    }
}
