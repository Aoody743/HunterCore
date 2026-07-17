package net.momirealms.craftengine.core.pack.model.definition.rangedisptach;

import com.google.gson.JsonObject;

public interface RangeDispatchPropertyReader<T extends RangeDispatchProperty> {
    
    T read(JsonObject json);
}
