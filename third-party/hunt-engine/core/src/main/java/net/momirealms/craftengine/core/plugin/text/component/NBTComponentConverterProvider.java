package net.momirealms.craftengine.core.plugin.text.component;

import com.google.gson.JsonNull;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.DataComponentValueConverterRegistry;
import net.kyori.adventure.text.serializer.gson.GsonDataComponentValue;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.sparrow.nbt.adventure.NBTDataComponentValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NBTComponentConverterProvider implements DataComponentValueConverterRegistry.Provider {
    private final List<DataComponentValueConverterRegistry.Conversion<?, ?>> conversions;

    public NBTComponentConverterProvider() {
        this.conversions = List.of(
                DataComponentValueConverterRegistry.Conversion.convert(
                        NBTDataComponentValue.NBTDataComponentValueImpl.class,
                        GsonDataComponentValue.class,
                        (key, srcValue) -> GsonDataComponentValue.gsonDataComponentValue(CraftEngine.instance().platform().sparrowNBTToJson(srcValue.tag()))
                ),
                DataComponentValueConverterRegistry.Conversion.convert(
                        NBTDataComponentValue.RemovedNBTDataComponentValue.class,
                        GsonDataComponentValue.class,
                        (key, srcValue) -> GsonDataComponentValue.gsonDataComponentValue(JsonNull.INSTANCE)
                )
        );
    }

    @Override
    public @NotNull Key id() {
        return Key.key("craftengine", "serializer/nbt");
    }

    @Override
    public @NotNull Iterable<DataComponentValueConverterRegistry.Conversion<?, ?>> conversions() {
        return this.conversions;
    }
}