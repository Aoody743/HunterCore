package net.momirealms.craftengine.proxy.minecraft.world.item;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.world.item.Items")
public interface ItemsProxy {
    ItemsProxy INSTANCE = ASMProxyFactory.create(ItemsProxy.class);
    Object AIR = INSTANCE.getAir();
    Object WATER_BUCKET = INSTANCE.getWaterBucket();
    Object BARRIER = INSTANCE.getBarrier();
    Object DEBUG_STICK = INSTANCE.getDebugStick();
    Object WRITABLE_BOOK = INSTANCE.getWritableBook();
    Object FIRE_CHARGE = INSTANCE.getFireCharge();
    Object WIND_CHARGE = INSTANCE.getWindCharge();

    @FieldGetter(name = "AIR", isStatic = true)
    Object getAir();

    @FieldGetter(name = "WATER_BUCKET", isStatic = true)
    Object getWaterBucket();

    @FieldGetter(name = "BARRIER", isStatic = true)
    Object getBarrier();

    @FieldGetter(name = "DEBUG_STICK", isStatic = true)
    Object getDebugStick();

    @FieldGetter(name = "WRITABLE_BOOK", isStatic = true)
    Object getWritableBook();

    @FieldGetter(name = "FIRE_CHARGE", isStatic = true)
    Object getFireCharge();

    @FieldGetter(name = "WIND_CHARGE", isStatic = true, activeIf = "min_version=1.21")
    default Object getWindCharge() {
        return null;
    }
}
