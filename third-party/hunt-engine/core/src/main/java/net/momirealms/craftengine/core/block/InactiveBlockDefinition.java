package net.momirealms.craftengine.core.block;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.registry.Holder;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.sparrow.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

public final class InactiveBlockDefinition extends AbstractBlockDefinition {
    private final Map<CompoundTag, ImmutableBlockState> cachedData = new HashMap<>();
    private final Key vanillaBlockId;

    public InactiveBlockDefinition(Holder.Reference<BlockDefinition> holder) {
        super(holder, new BlockStateVariantProvider(holder, ImmutableBlockState::new, Map.of()), Map.of(), null);
        this.vanillaBlockId = null;
    }

    public InactiveBlockDefinition(Holder.Reference<BlockDefinition> holder, Key vanillaBlockId) {
        super(holder, new BlockStateVariantProvider(holder, ImmutableBlockState::new, Map.of()), Map.of(), null);
        this.vanillaBlockId = vanillaBlockId;
    }

    @Override
    public ImmutableBlockState getBlockState(CompoundTag nbt) {
        return this.cachedData.computeIfAbsent(nbt, k -> {
            ImmutableBlockState state = new ImmutableBlockState(super.holder, super.variantProvider, new Reference2ObjectArrayMap<>());
            state.setNbtToSave(state.toNbtToSave(nbt));
            state.setBehavior(CraftEngine.instance().blockManager().getEmptyBlockBehavior());
            if (this.vanillaBlockId != null) {
                BlockStateWrapper vanillaBlockState = CraftEngine.instance().blockManager().createVanillaBlockState(this.vanillaBlockId.asString());
                if (vanillaBlockState != null) {
                    vanillaBlockState = vanillaBlockState.withProperties(nbt);
                    state.setCustomBlockState(vanillaBlockState);
                }
            }
            return state;
        });
    }
}