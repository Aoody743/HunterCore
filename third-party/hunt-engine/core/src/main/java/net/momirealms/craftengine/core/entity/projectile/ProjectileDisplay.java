package net.momirealms.craftengine.core.entity.projectile;

import net.momirealms.craftengine.core.entity.display.Billboard;
import net.momirealms.craftengine.core.entity.display.ItemDisplayContext;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record ProjectileDisplay(
        Key item,
        ItemDisplayContext displayType,
        Billboard billboard,
        Vector3f scale,
        Vector3f translation,
        Quaternionf rotation) {
    private static final String[] DISPLAY_TRANSFORM = new String[] {"display_transform", "display-transform"};

    public static ProjectileDisplay fromConfig(ConfigSection section) {
        return new ProjectileDisplay(
                section.getNonNullIdentifier("item"),
                section.getEnum(DISPLAY_TRANSFORM, ItemDisplayContext.class, ItemDisplayContext.NONE),
                section.getEnum("billboard", Billboard.class, Billboard.FIXED),
                section.getVector3f("scale", ConfigConstants.NORMAL_SCALE),
                section.getVector3f("translation", ConfigConstants.ZERO_VECTOR3),
                section.getQuaternion("rotation", ConfigConstants.ZERO_QUATERNION)
        );
    }
}
