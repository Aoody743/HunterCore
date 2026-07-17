package net.momirealms.craftengine.core.entity.furniture;

public abstract class AbstractExternalModel implements ExternalModel {
    protected final String id;

    public AbstractExternalModel(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
