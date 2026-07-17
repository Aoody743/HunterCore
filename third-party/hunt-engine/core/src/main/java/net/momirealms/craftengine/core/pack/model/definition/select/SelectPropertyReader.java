package net.momirealms.craftengine.core.pack.model.definition.select;

import com.google.gson.JsonObject;

public interface SelectPropertyReader<T extends SelectProperty> {
    
    T read(JsonObject json);
}
