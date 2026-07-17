package net.momirealms.craftengine.core.pack.model.definition.special;

import com.google.gson.JsonObject;

public interface SpecialModelReader<T extends SpecialModel> {

    T read(JsonObject json);
}
