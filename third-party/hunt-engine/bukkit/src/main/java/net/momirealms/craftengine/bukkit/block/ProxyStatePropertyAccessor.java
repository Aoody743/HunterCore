package net.momirealms.craftengine.bukkit.block;

import net.momirealms.craftengine.core.block.StatePropertyAccessor;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.BlockProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockBehaviourProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockStateProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.StateDefinitionProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.StateHolderProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.properties.PropertyProxy;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ProxyStatePropertyAccessor implements StatePropertyAccessor {
    private final Object blockState;

    public ProxyStatePropertyAccessor(Object blockState) {
        this.blockState = blockState;
    }

    @Override
    public Collection<String> getPropertyNames() {
        Collection<Object> properties = StateHolderProxy.INSTANCE.getProperties(this.blockState);
        return properties.stream()
                .map(PropertyProxy.INSTANCE::getName)
                .collect(Collectors.toList());
    }

    @Override
    public String getPropertyValueAsString(String propertyName) {
        Object property = getPropertyByName(propertyName);
        if (property == null) {
            return null;
        }
        return StateHolderProxy.INSTANCE.getValue(this.blockState, property)
                .toString()
                .toLowerCase(Locale.ROOT);
    }

    @Override
    public <T> T getPropertyValue(String propertyName) {
        Object property = getPropertyByName(propertyName);
        if (property == null) {
            return null;
        }
        return StateHolderProxy.INSTANCE.getValue(this.blockState, property);
    }

    @Override
    public boolean hasProperty(String propertyName) {
        return getPropertyByName(propertyName) != null;
    }

    @Override
    public @NotNull Object withProperty(String propertyName, String value) {
        Object property = getPropertyByName(propertyName);
        if (property == null) {
            return this.blockState;
        }
        Optional<Object> optionalValue = PropertyProxy.INSTANCE.getValue(property, value);
        if (optionalValue.isPresent()) {
            return StateHolderProxy.INSTANCE.setValue(this.blockState, property, (Comparable<?>) optionalValue.get());
        }
        return this.blockState;
    }

    @Override
    public @NotNull Object cycleProperty(String propertyName, boolean backwards) {
        Object property = getPropertyByName(propertyName);
        if (property == null) {
            return this.blockState;
        }
        return cycleState(this.blockState, property, backwards);
    }

    private Object getPropertyByName(String propertyName) {
        Object block = BlockBehaviourProxy.BlockStateBaseProxy.INSTANCE.getBlock(this.blockState);
        Object stateDefinition = BlockProxy.INSTANCE.getStateDefinition(block);
        return StateDefinitionProxy.INSTANCE.getProperty(stateDefinition, propertyName);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object cycleState(Object blockState, Object property, boolean backwards) {
        if (backwards) {
            Collection<Object> possibleValues = PropertyProxy.INSTANCE.getPossibleValues(property);
            Comparable currentValue = StateHolderProxy.INSTANCE.getValue(blockState, property);
            Comparable previousValue = (Comparable) MiscUtils.findPreviousInIterable(possibleValues, currentValue);
            return StateHolderProxy.INSTANCE.setValue(blockState, property, previousValue);
        } else {
            return BlockStateProxy.INSTANCE.cycle(blockState, property);
        }
    }
}
