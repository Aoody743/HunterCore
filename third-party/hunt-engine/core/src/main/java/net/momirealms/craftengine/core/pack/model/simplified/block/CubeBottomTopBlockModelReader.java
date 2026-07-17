package net.momirealms.craftengine.core.pack.model.simplified.block;

import net.momirealms.craftengine.core.pack.model.generation.ModelGeneration;
import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class CubeBottomTopBlockModelReader implements SimplifiedBlockModelReader {
    public static final CubeBottomTopBlockModelReader INSTANCE = new CubeBottomTopBlockModelReader();
    private static final Key PARENT = Key.of("minecraft:block/cube_bottom_top");
    private static final List<String> SLOTS = List.of("bottom", "side", "top");

    private CubeBottomTopBlockModelReader() {
    }

    @Override
    public ModelGeneration read(@NotNull List<Key> textures, @Nullable Key particle) {
        Map<String, String> assignment = TextureSlotAssigner.assign(textures, SLOTS);
        if (particle != null) {
            assignment.put("particle", particle.asMinimalString());
        }
        return ModelGeneration.builder()
                .parentModelPath(PARENT)
                .texturesOverride(assignment)
                .build();
    }
}
