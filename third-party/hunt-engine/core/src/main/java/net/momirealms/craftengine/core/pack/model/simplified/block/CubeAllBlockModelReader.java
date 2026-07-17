package net.momirealms.craftengine.core.pack.model.simplified.block;

import net.momirealms.craftengine.core.pack.model.generation.ModelGeneration;
import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class CubeAllBlockModelReader implements SimplifiedBlockModelReader {
    public static final CubeAllBlockModelReader INSTANCE = new CubeAllBlockModelReader();
    private static final Key PARENT = Key.of("minecraft:block/cube_all");
    private static final List<String> SLOTS = List.of("all");

    private CubeAllBlockModelReader() {
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
