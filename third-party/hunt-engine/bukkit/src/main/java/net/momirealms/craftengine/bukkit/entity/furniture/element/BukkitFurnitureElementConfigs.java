package net.momirealms.craftengine.bukkit.entity.furniture.element;

import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElementConfigType;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElementConfigs;
import net.momirealms.craftengine.core.util.Key;

public final class BukkitFurnitureElementConfigs extends FurnitureElementConfigs {
    public static final FurnitureElementConfigType<ItemDisplayFurnitureElement> ITEM_DISPLAY = register(Key.ce("item_display"), ItemDisplayFurnitureElementConfig.FACTORY);
    public static final FurnitureElementConfigType<TextDisplayFurnitureElement> TEXT_DISPLAY = register(Key.ce("text_display"), TextDisplayFurnitureElementConfig.FACTORY);
    public static final FurnitureElementConfigType<ItemFurnitureElement> ITEM = register(Key.ce("item"), ItemFurnitureElementConfig.FACTORY);
    public static final FurnitureElementConfigType<ArmorStandFurnitureElement> ARMOR_STAND = register(Key.ce("armor_stand"), ArmorStandFurnitureElementConfig.FACTORY);

    private BukkitFurnitureElementConfigs() {}

    public static void init() {
    }
}
