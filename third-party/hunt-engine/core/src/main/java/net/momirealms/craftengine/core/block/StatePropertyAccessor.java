package net.momirealms.craftengine.core.block;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface StatePropertyAccessor {

    String getPropertyValueAsString(String property);

    Collection<String> getPropertyNames();

    boolean hasProperty(String property);

    <T> T getPropertyValue(String property);

    @NotNull
    Object withProperty(String propertyName, String value);

    @NotNull
    default Object cycleProperty(String propertyName) {
        return cycleProperty(propertyName, false);
    }

    @NotNull
    Object cycleProperty(String propertyName, boolean backwards);
}
