package net.momirealms.craftengine.core.pack.model.simplified.block;

import net.momirealms.craftengine.core.pack.model.generation.ModelGeneration;
import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class CubeColumnBlockModelReader implements SimplifiedBlockModelReader {
    public static final CubeColumnBlockModelReader INSTANCE = new CubeColumnBlockModelReader();
    private static final Key PARENT = Key.of("minecraft:block/cube_column");
    private static final List<String> SLOTS = List.of("end", "side");

    private CubeColumnBlockModelReader() {
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
