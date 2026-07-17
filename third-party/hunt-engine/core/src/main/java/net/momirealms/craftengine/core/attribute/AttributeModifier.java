package net.momirealms.craftengine.core.attribute;

import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.MiscUtils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class AttributeModifier {
    private final String type;
    private final Slot slot;
    private final Key id;
    private final double amount;
    private final Operation operation;
    @Nullable
    private final Display display;

    public AttributeModifier(String type, Slot slot, Key id, double amount, Operation operation, @Nullable Display display) {
        this.amount = amount;
        this.display = display;
        this.id = id;
        this.operation = operation;
        this.slot = slot;
        this.type = type;
    }

    public double amount() {
        return this.amount;
    }

    public @Nullable Display display() {
        return this.display;
    }

    public Key id() {
        return this.id;
    }

    public Operation operation() {
        return this.operation;
    }

    public Slot slot() {
        return this.slot;
    }

    public String type() {
        return this.type;
    }

    public enum Slot {
        ANY,
        MAINHAND,
        OFFHAND,
        HAND,
        FEET,
        LEGS,
        CHEST,
        HEAD,
        ARMOR,
        BODY,
        SADDLE;

        public static final Map<String, Slot> BY_ID = MiscUtils.init(new HashMap<>(), m -> {
            m.put("any", Slot.ANY);
            m.put("hand", Slot.HAND);
            m.put("mainhand", Slot.MAINHAND);
            m.put("main_hand", Slot.MAINHAND);
            m.put("offhand", Slot.OFFHAND);
            m.put("off_hand", Slot.OFFHAND);
            m.put("feet", Slot.FEET);
            m.put("boots", Slot.FEET);
            m.put("boot", Slot.FEET);
            m.put("shoes", Slot.FEET);
            m.put("legs", Slot.LEGS);
            m.put("leg", Slot.LEGS);
            m.put("leggings", Slot.LEGS);
            m.put("chest", Slot.CHEST);
            m.put("chestplate", Slot.CHEST);
            m.put("head", Slot.HEAD);
            m.put("helmet", Slot.HEAD);
            m.put("hat", Slot.HEAD);
            m.put("armor", Slot.ARMOR);
            m.put("body", Slot.BODY);
            m.put("saddle", Slot.SADDLE);
        });

        public static Slot byId(String name) {
            return BY_ID.get(name.toLowerCase(Locale.ROOT));
        }

        public static Slot byId(String name, Slot defaultValue) {
            return BY_ID.getOrDefault(name.toLowerCase(Locale.ROOT), defaultValue);
        }
    }

    public enum Operation {
        ADD_VALUE("add_value"), ADD_MULTIPLIED_BASE("add_multiplied_base"), ADD_MULTIPLIED_TOTAL("add_multiplied_total");

        private final String id;

        Operation(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Display(AttributeModifier.Display.Type type, Component value) {

        public enum Type {
                DEFAULT, HIDDEN, OVERRIDE
        }
    }
}
