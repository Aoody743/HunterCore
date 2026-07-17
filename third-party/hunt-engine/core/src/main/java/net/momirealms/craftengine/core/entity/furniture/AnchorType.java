package net.momirealms.craftengine.core.entity.furniture;

public enum AnchorType {
    GROUND(0, "ground"),
    WALL(1, "wall"),
    CEILING(2, "ceiling");

    private final int id;
    private final String variantName;

    AnchorType(int id, String variantName) {
        this.id = id;
        this.variantName = variantName;
    }

    public int getId() {
        return id;
    }

    public String variantName() {
        return variantName;
    }

    public static AnchorType byId(int id) {
        return values()[id];
    }
}
