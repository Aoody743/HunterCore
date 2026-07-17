package net.momirealms.craftengine.core.pack.model.definition;

import com.google.gson.JsonObject;

@FunctionalInterface
public interface ItemModelReader<T extends ItemModel> {

    T read(JsonObject json);
}
