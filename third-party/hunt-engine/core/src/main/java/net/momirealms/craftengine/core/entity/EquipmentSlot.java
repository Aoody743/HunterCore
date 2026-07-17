package net.momirealms.craftengine.core.entity;

import net.momirealms.craftengine.core.util.MiscUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum EquipmentSlot {
    MAINHAND,
    OFFHAND,
    FEET,
    LEGS,
    CHEST,
    HEAD,
    BODY,
    SADDLE;

    public static final Map<String, EquipmentSlot> BY_ID = MiscUtils.init(new HashMap<>(), m -> {
        m.put("mainhand", EquipmentSlot.MAINHAND);
        m.put("main_hand", EquipmentSlot.MAINHAND);
        m.put("hand", EquipmentSlot.MAINHAND);
        m.put("offhand", EquipmentSlot.OFFHAND);
        m.put("off_hand", EquipmentSlot.OFFHAND);
        m.put("feet", EquipmentSlot.FEET);
        m.put("boots", EquipmentSlot.FEET);
        m.put("boot", EquipmentSlot.FEET);
        m.put("shoes", EquipmentSlot.FEET);
        m.put("legs", EquipmentSlot.LEGS);
        m.put("leg", EquipmentSlot.LEGS);
        m.put("leggings", EquipmentSlot.LEGS);
        m.put("chest", EquipmentSlot.CHEST);
        m.put("chestplate", EquipmentSlot.CHEST);
        m.put("head", EquipmentSlot.HEAD);
        m.put("helmet", EquipmentSlot.HEAD);
        m.put("hat", EquipmentSlot.HEAD);
        m.put("body", EquipmentSlot.BODY);
        m.put("saddle", EquipmentSlot.SADDLE);
    });

    public static EquipmentSlot byId(String name) {
        return BY_ID.get(name.toLowerCase(Locale.ROOT));
    }

    public static EquipmentSlot byId(String name, EquipmentSlot defaultValue) {
        return BY_ID.getOrDefault(name.toLowerCase(Locale.ROOT), defaultValue);
    }
}
