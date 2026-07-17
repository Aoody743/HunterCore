package net.momirealms.craftengine.core.loot.function.formula;

import net.momirealms.craftengine.core.util.Key;

public record FormulaType<T extends Formula>(Key id, FormulaFactory<T> factory) {
}
