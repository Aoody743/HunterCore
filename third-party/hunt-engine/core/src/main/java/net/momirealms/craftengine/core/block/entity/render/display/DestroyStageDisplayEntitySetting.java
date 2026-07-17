package net.momirealms.craftengine.core.block.entity.render.display;

import net.momirealms.craftengine.core.entity.display.Billboard;
import net.momirealms.craftengine.core.entity.display.ItemDisplayContext;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class DestroyStageDisplayEntitySetting {
    private static final String[] DISPLAY_CONTEXT = new String[]{"display_context", "display_transform", "display-context", "display-transform"};
    private static final String[] VIEW_RANGE = new String[]{"view_range", "view-range"};
    private static final String[] BLOCK_LIGHT = new String[]{"block_light", "block-light"};
    private static final String[] SKY_LIGHT = new String[]{"sky_light", "sky-light"};

    public final List<Key> items;
    public final Vector3f position;
    public final Vector3f translation;
    public final Vector3f scale;
    public final float pitch;
    public final float yaw;
    public final Quaternionf rotation;
    public final ItemDisplayContext displayContext;
    public final Billboard billboard;
    public final float viewRange;
    public final int blockLight;
    public final int skyLight;

    private DestroyStageDisplayEntitySetting(List<Key> items,
                                             Vector3f position,
                                             Vector3f translation,
                                             Vector3f scale,
                                             float pitch,
                                             float yaw,
                                             Quaternionf rotation,
                                             ItemDisplayContext displayContext,
                                             Billboard billboard,
                                             float viewRange,
                                             int blockLight,
                                             int skyLight) {
        this.items = items;
        this.position = position;
        this.translation = translation;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.rotation = rotation;
        this.displayContext = displayContext;
        this.billboard = billboard;
        this.viewRange = viewRange;
        this.blockLight = blockLight;
        this.skyLight = skyLight;
    }

    public static DestroyStageDisplayEntitySetting fromConfig(ConfigSection section) {
        List<Key> items = section.getValue("items", v -> v.getAsList(ConfigValue::getAsIdentifier));
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("destroy_stage_display requires a non-empty 'items' list");
        }
        ConfigSection brightness = section.getSection("brightness");
        return new DestroyStageDisplayEntitySetting(
                items,
                section.getVector3f("position", ConfigConstants.CENTER_VECTOR3),
                section.getVector3f("translation", ConfigConstants.ZERO_VECTOR3),
                section.getVector3f("scale", ConfigConstants.NORMAL_SCALE),
                section.getFloat("pitch", 0f),
                section.getFloat("yaw", 0f),
                section.getQuaternion("rotation", ConfigConstants.ZERO_QUATERNION),
                section.getEnum(DISPLAY_CONTEXT, ItemDisplayContext.class, ItemDisplayContext.NONE),
                section.getEnum("billboard", Billboard.class, Billboard.FIXED),
                section.getFloat(VIEW_RANGE, 1f),
                brightness != null ? brightness.getInt(BLOCK_LIGHT, -1) : -1,
                brightness != null ? brightness.getInt(SKY_LIGHT, -1) : -1
        );
    }

    public int itemIndexForProgress(float progress) {
        if (progress < 0 || this.items.isEmpty()) return -1;
        int index = (int) (progress * this.items.size());
        if (index >= this.items.size()) index = this.items.size() - 1;
        return index;
    }

    @Nullable
    public Key itemForProgress(float progress) {
        int index = itemIndexForProgress(progress);
        return index < 0 ? null : this.items.get(index);
    }

    @Nullable
    public Key itemForIndex(int index) {
        if (index < 0 || index >= this.items.size()) return null;
        return this.items.get(index);
    }
}
