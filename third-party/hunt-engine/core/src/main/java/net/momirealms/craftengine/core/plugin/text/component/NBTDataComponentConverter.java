package net.momirealms.craftengine.core.plugin.text.component;

import com.google.gson.JsonNull;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.DataComponentValueConverterRegistry;
import net.kyori.adventure.text.serializer.gson.GsonDataComponentValue;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.sparrow.nbt.adventure.NBTDataComponentValue;
import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.constructor.SConstructor2;
import net.momirealms.sparrow.reflection.constructor.matcher.ConstructorMatcher;
import net.momirealms.sparrow.reflection.field.matcher.FieldMatcher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NBTDataComponentConverter {
    private NBTDataComponentConverter() {}

    @SuppressWarnings("unchecked")
    public static void register() {
        Map<Class<?>, Map<Class<?>, Object>> CACHE = (Map<Class<?>, Map<Class<?>, Object>>) SparrowClass.of(SparrowClass.find("net.kyori.adventure.text.event.DataComponentValueConverterRegistry$ConversionCache"))
                .getDeclaredSparrowField(FieldMatcher.named("CACHE"))
                .mh()
                .get(null);

        DataComponentValueConverterRegistry.Conversion<NBTDataComponentValue.NBTDataComponentValueImpl, GsonDataComponentValue> convertor1 = DataComponentValueConverterRegistry.Conversion.convert(
                NBTDataComponentValue.NBTDataComponentValueImpl.class,
                GsonDataComponentValue.class,
                (key, srcValue) -> GsonDataComponentValue.gsonDataComponentValue(CraftEngine.instance().platform().sparrowNBTToJson(srcValue.tag()))
        );
        DataComponentValueConverterRegistry.Conversion<NBTDataComponentValue.RemovedNBTDataComponentValue, GsonDataComponentValue> convertor2 = DataComponentValueConverterRegistry.Conversion.convert(
                NBTDataComponentValue.RemovedNBTDataComponentValue.class,
                GsonDataComponentValue.class,
                (key, srcValue) -> GsonDataComponentValue.gsonDataComponentValue(JsonNull.INSTANCE)
        );

        SConstructor2 constructor = SparrowClass.of(SparrowClass.find("net.kyori.adventure.text.event.DataComponentValueConverterRegistry$RegisteredConversion"))
                .getDeclaredSparrowConstructor(ConstructorMatcher.takeArguments(Key.class, DataComponentValueConverterRegistry.Conversion.class))
                .asm$2();

        CACHE.computeIfAbsent(NBTDataComponentValue.NBTDataComponentValueImpl.class, $ -> new ConcurrentHashMap<>())
                .computeIfAbsent(GsonDataComponentValue.class, $ -> constructor.newInstance(Key.key("craftengine", "serializer/nbt"), convertor1));
        CACHE.computeIfAbsent(NBTDataComponentValue.RemovedNBTDataComponentValue.class, $ -> new ConcurrentHashMap<>())
                .computeIfAbsent(GsonDataComponentValue.class, $ -> constructor.newInstance(Key.key("craftengine", "serializer/nbt"), convertor2));
    }
}
