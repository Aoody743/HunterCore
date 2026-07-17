package net.momirealms.craftengine.core.pack.model.simplified.block;

import net.momirealms.craftengine.core.pack.model.generation.ModelGeneration;
import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class CubeFiveTexturesBlockModelReader implements SimplifiedBlockModelReader {
    public static final CubeFiveTexturesBlockModelReader INSTANCE = new CubeFiveTexturesBlockModelReader();
    private static final Key PARENT = Key.of("minecraft:block/cube");
    private static final List<String> SLOTS = List.of("down", "up", "north", "south", "west", "east");

    private CubeFiveTexturesBlockModelReader() {
    }

    @Override
    public ModelGeneration read(@NotNull List<Key> textures, @Nullable Key particle) {
        Map<String, String> assignment = TextureSlotAssigner.assign(textures, SLOTS);
        // west and east always share the same texture in the minecraft cube model
        if (!assignment.containsKey("east") && assignment.containsKey("west")) {
            assignment.put("east", assignment.get("west"));
        }
        if (!assignment.containsKey("west") && assignment.containsKey("east")) {
            assignment.put("west", assignment.get("east"));
        }
        if (particle != null) {
            assignment.put("particle", particle.asMinimalString());
        }
        return ModelGeneration.builder()
                .parentModelPath(PARENT)
                .texturesOverride(assignment)
                .build();
    }
}
