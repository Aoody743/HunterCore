package net.momirealms.craftengine.core.font;

import net.momirealms.craftengine.core.plugin.context.GlobalVariableManager;
import net.momirealms.craftengine.core.plugin.locale.ServerLangData;
import net.momirealms.craftengine.core.plugin.locale.TranslationManager;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;

import java.util.Map;

public final class NetworkTagDataSerializer {
    private NetworkTagDataSerializer() {}

    public static void writeOffsetFont(FriendlyByteBuf buf, OffsetFont offsetFont) {
        buf.writeKey(offsetFont.font);
        for (int i = 1; i <= 15; i++) {
            buf.writeUtf(offsetFont.negativeOffsets[i]);
        }
        buf.writeUtf(offsetFont.NEG_16);
        buf.writeUtf(offsetFont.NEG_32);
        buf.writeUtf(offsetFont.NEG_48);
        buf.writeUtf(offsetFont.NEG_64);
        buf.writeUtf(offsetFont.NEG_128);
        buf.writeUtf(offsetFont.NEG_256);
        for (int i = 1; i <= 15; i++) {
            buf.writeUtf(offsetFont.positiveOffsets[i]);
        }
        buf.writeUtf(offsetFont.POS_16);
        buf.writeUtf(offsetFont.POS_32);
        buf.writeUtf(offsetFont.POS_48);
        buf.writeUtf(offsetFont.POS_64);
        buf.writeUtf(offsetFont.POS_128);
        buf.writeUtf(offsetFont.POS_256);
    }

    public static void writeImages(FriendlyByteBuf buf, Map<Key, Image> images) {
        buf.writeMap(images,
                FriendlyByteBuf::writeKey,
                (byteBuf, image) -> {
                    if (image instanceof BitmapImage bitmapImage) {
                        buf.writeByte(0);
                        writeBitmapImage(byteBuf, bitmapImage);
                    } else if (image instanceof ReferenceImage referenceImage) {
                        buf.writeByte(1);
                        writeReferenceImage(byteBuf, referenceImage);
                    }
                }
        );
    }

    public static void writeBitmapImage(FriendlyByteBuf buf, BitmapImage image) {
        buf.writeKey(image.id());
        buf.writeKey(image.font());

        int[][] codepointGrid = image.codepointGrid();
        if (codepointGrid == null) {
            buf.writeVarInt(-1);
            return;
        }

        int rows = codepointGrid.length;
        int cols = rows == 0 ? 0 : codepointGrid[0].length;
        buf.writeVarInt(rows);
        buf.writeVarInt(cols);

        for (int[] col : codepointGrid) {
            for (int i = 0; i < cols; i++) {
                buf.writeVarInt(col[i]);
            }
        }
    }

    public static void writeReferenceImage(FriendlyByteBuf buf, ReferenceImage image) {
        buf.writeKey(image.reference());
    }

    public static void writeL10n(FriendlyByteBuf buf, TranslationManager translationManager) {
        buf.writeMap(translationManager.serverLangData(), FriendlyByteBuf::writeUtf, NetworkTagDataSerializer::writeServerLangData);
    }

    public static void writeServerLangData(FriendlyByteBuf buf, ServerLangData data) {
        buf.writeBoolean(data.fallback != null);
        if (data.fallback != null) {
            buf.writeUtf(data.fallback);
        }
        buf.writeMap(data.translations(), (b, locale) -> b.writeUtf(locale.toLanguageTag()), FriendlyByteBuf::writeUtf);
    }

    public static void writeGlobalVariables(FriendlyByteBuf buf, GlobalVariableManager globalVariableManager) {
        buf.writeMap(globalVariableManager.globalVariables(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
    }
}
