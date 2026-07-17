package net.momirealms.craftengine.core.entity.data;

import java.util.List;

public interface EntityData<T> {

    Object serializer();

    int id();

    T defaultValue();

    Object entityDataAccessor();

    Object create(Object entityDataAccessor, T value);

    default Object createEntityDataIfNotDefaultValue(T value) {
        if (defaultValue().equals(value)) return null;
        return create(entityDataAccessor(), value);
    }

    default Object createEntityData(T value) {
        return create(entityDataAccessor(), value);
    }

    default void addEntityDataIfNotDefaultValue(T value, List<Object> list) {
        if (!defaultValue().equals(value)) {
            list.add(create(entityDataAccessor(), value));
        }
    }

    default void addEntityData(T value, List<Object> list) {
        list.add(create(entityDataAccessor(), value));
    }
}
