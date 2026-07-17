package net.momirealms.craftengine.bukkit.plugin.network.listener.game;

import net.momirealms.craftengine.bukkit.util.KeyUtils;
import net.momirealms.craftengine.bukkit.util.RegistryUtils;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.core.plugin.network.event.ByteBufPacketEvent;
import net.momirealms.craftengine.core.plugin.network.listener.ByteBufferPacketListener;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.proxy.minecraft.core.RegistryProxy;
import net.momirealms.craftengine.proxy.minecraft.core.registries.BuiltInRegistriesProxy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class UpdateAttributesListener implements ByteBufferPacketListener {
    public static final UpdateAttributesListener INSTANCE = new UpdateAttributesListener();
    public static final int BLOCK_BREAK_SPEED = RegistryProxy.INSTANCE.getId(BuiltInRegistriesProxy.ATTRIBUTE, RegistryUtils.getRegistryValue(BuiltInRegistriesProxy.ATTRIBUTE, KeyUtils.toIdentifier(Key.minecraft(VersionHelper.isOrAbove1_21 ? "block_break_speed" : "player.block_break_speed"))));
    public static final UUID CUSTOM_HARDNESS_UUID = UUID.nameUUIDFromBytes(Key.ce("custom_hardness").asString().getBytes(StandardCharsets.UTF_8));

    @Override
    public void onPacketSend(NetWorkUser user, ByteBufPacketEvent event) {
        if (!VersionHelper.isOrAbove1_20_5) return;
        boolean changed = false;
        Player player = (Player) user;
        if (player.clientSideCanBreak()) {
            return;
        }

        FriendlyByteBuf buf = event.getBuffer();
        int entityId = buf.readVarInt();
        if (entityId != player.entityId()) {
            return;
        }

        List<AttributeSnapshot> attributeSnapshots = new ArrayList<>();
        int snapshotCount = buf.readVarInt();
        for (int i = 0; i < snapshotCount; i++) {
            int attributeId = buf.readVarInt();
            double baseValue = buf.readDouble();

            int modifierCount = buf.readVarInt();
            List<AttributeModifier> modifiers;
            if (attributeId == BLOCK_BREAK_SPEED) {
                modifiers = List.of(VersionHelper.isOrAbove1_21 ?
                        new AttributeModifier1_21(Key.ce("custom_hardness"), -999d, (byte) 0) :
                        new AttributeModifier1_20_5(CUSTOM_HARDNESS_UUID, -999d, (byte) 0)
                );
                baseValue = 0;
                changed = true;
            } else {
                modifiers = new ArrayList<>(modifierCount);
                if (VersionHelper.isOrAbove1_21) {
                    for (int j = 0; j < modifierCount; j++) {
                        Key modifierName = buf.readKey();
                        double modifierValue = buf.readDouble();
                        byte operation = buf.readByte();
                        modifiers.add(new AttributeModifier1_21(modifierName, modifierValue, operation));
                    }
                } else {
                    for (int j = 0; j < modifierCount; j++) {
                        UUID uuid = buf.readUUID();
                        double modifierValue = buf.readDouble();
                        byte operation = buf.readByte();
                        modifiers.add(new AttributeModifier1_20_5(uuid, modifierValue, operation));
                    }
                }
            }
            attributeSnapshots.add(new AttributeSnapshot(attributeId, baseValue, modifiers));
        }

        if (changed) {
            event.setChanged(true);
            buf.clear();
            buf.writeVarInt(event.packetID());
            buf.writeVarInt(entityId);
            buf.writeVarInt(attributeSnapshots.size());
            for (AttributeSnapshot attributeSnapshot : attributeSnapshots) {
                buf.writeVarInt(attributeSnapshot.attributeType);
                buf.writeDouble(attributeSnapshot.base);
                buf.writeVarInt(attributeSnapshot.modifiers.size());
                for (AttributeModifier modifier : attributeSnapshot.modifiers) {
                    if (modifier instanceof AttributeModifier1_21(Key modifierName, double value, byte operation1)) {
                        buf.writeKey(modifierName);
                        buf.writeDouble(value);
                        buf.writeByte(operation1);
                    } else if (modifier instanceof AttributeModifier1_20_5(UUID uuid, double modifierValue, byte operation)) {
                        buf.writeUUID(uuid);
                        buf.writeDouble(modifierValue);
                        buf.writeByte(operation);
                    }
                }
            }
        }
    }

    public record AttributeSnapshot(int attributeType, double base, List<AttributeModifier> modifiers) {
    }

    interface AttributeModifier {
    }

    record AttributeModifier1_21(Key modifierName, double modifierValue, byte operation) implements AttributeModifier {
    }

    record AttributeModifier1_20_5(UUID uuid, double modifierValue, byte operation) implements AttributeModifier {
    }
}
