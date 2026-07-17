package net.momirealms.craftengine.core.plugin.gui;

import net.momirealms.craftengine.core.util.Key;

public class GuiElementMissingException extends RuntimeException {
    private final Key element;

    public GuiElementMissingException(Key element) {
        super("Can't find gui element " + element.asString());
        this.element = element;
    }

    public Key getElement() {
        return this.element;
    }
}
