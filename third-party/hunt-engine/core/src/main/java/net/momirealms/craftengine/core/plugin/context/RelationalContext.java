package net.momirealms.craftengine.core.plugin.context;

import java.util.Optional;

public interface RelationalContext extends Context {

    ContextHolder viewerContexts();

    <T> Optional<T> getViewerOptionalParameter(ContextKey<T> parameter);

    <T> T getViewerParameterOrThrow(ContextKey<T> parameter);
}
