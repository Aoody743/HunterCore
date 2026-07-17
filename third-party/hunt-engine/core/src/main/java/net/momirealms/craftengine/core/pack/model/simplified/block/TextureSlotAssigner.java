package net.momirealms.craftengine.core.pack.model.simplified.block;

import net.momirealms.craftengine.core.util.Key;

import java.util.*;

public final class TextureSlotAssigner {

    private TextureSlotAssigner() {
    }

    public static Map<String, String> assign(List<Key> textures, List<String> orderedSlots) {
        List<String> features = new ArrayList<>(textures.size());
        for (Key texture : textures) {
            features.add(extractFeature(texture.value()));
        }

        boolean[] textureUsed = new boolean[textures.size()];
        Set<String> filledSlots = new HashSet<>();
        Map<String, String> result = new LinkedHashMap<>();

        for (int i = 0; i < textures.size(); i++) {
            if (textureUsed[i]) continue;
            String feature = features.get(i);
            for (String slot : orderedSlots) {
                if (filledSlots.contains(slot)) continue;
                if (isHighConfidenceMatch(feature, slot)) {
                    result.put(slot, textures.get(i).asMinimalString());
                    textureUsed[i] = true;
                    filledSlots.add(slot);
                    break;
                }
            }
        }

        for (int i = 0; i < textures.size(); i++) {
            if (textureUsed[i]) continue;
            String feature = features.get(i);
            for (String slot : orderedSlots) {
                if (filledSlots.contains(slot)) continue;
                if (isLowConfidenceMatch(feature, slot)) {
                    result.put(slot, textures.get(i).asMinimalString());
                    textureUsed[i] = true;
                    filledSlots.add(slot);
                    break;
                }
            }
        }

        int slotIndex = 0;
        for (int i = 0; i < textures.size(); i++) {
            if (textureUsed[i]) continue;
            while (slotIndex < orderedSlots.size() && filledSlots.contains(orderedSlots.get(slotIndex))) {
                slotIndex++;
            }
            if (slotIndex >= orderedSlots.size()) {
                break;
            }
            String slot = orderedSlots.get(slotIndex);
            result.put(slot, textures.get(i).asMinimalString());
            textureUsed[i] = true;
            filledSlots.add(slot);
            slotIndex++;
        }

        return result;
    }

    private static String extractFeature(String keyValue) {
        int lastSlash = keyValue.lastIndexOf('/');
        if (lastSlash >= 0) {
            return keyValue.substring(lastSlash + 1);
        }
        return keyValue;
    }

    private static boolean isHighConfidenceMatch(String feature, String slotName) {
        if (feature.equals(slotName)) {
            return true;
        }
        return feature.endsWith("_" + slotName);
    }

    private static boolean isLowConfidenceMatch(String feature, String slotName) {
        return feature.contains(slotName);
    }
}
