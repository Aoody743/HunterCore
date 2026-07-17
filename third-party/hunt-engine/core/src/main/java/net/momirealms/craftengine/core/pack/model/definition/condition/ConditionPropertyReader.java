package net.momirealms.craftengine.core.pack.model.definition.condition;

import com.google.gson.JsonObject;

public interface ConditionPropertyReader<T extends ConditionProperty> {
    
    T read(JsonObject json);
}
