package net.momirealms.craftengine.bukkit.plugin.network.listener.game;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.momirealms.craftengine.bukkit.block.BukkitBlockManager;
import net.momirealms.craftengine.bukkit.util.KeyUtils;
import net.momirealms.craftengine.bukkit.util.PacketUtils;
import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.core.plugin.network.event.ByteBufPacketEvent;
import net.momirealms.craftengine.core.plugin.network.listener.ByteBufferPacketListener;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.proxy.minecraft.core.IdMapProxy;
import net.momirealms.craftengine.proxy.minecraft.core.registries.BuiltInRegistriesProxy;
import net.momirealms.craftengine.proxy.minecraft.network.codec.StreamEncoderProxy;
import net.momirealms.craftengine.proxy.minecraft.sounds.SoundEventProxy;
import org.bukkit.Location;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SoundListener implements ByteBufferPacketListener {
    public static final ByteBufferPacketListener INSTANCE = new SoundListener();
    public static final Object SoundEvent$DIRECT_STREAM_CODEC = VersionHelper.isOrAbove1_20_5 ? SoundEventProxy.INSTANCE.getDirectStreamCodec() : null;
    private static final Cache<SoundLocation, Object> IGNORED_SOUNDS = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(1))
            .build();
    private static final Key TRIDENT_THROW = Key.of("item.trident.throw");
    private static final Key SNOWBALL_THROW = Key.of("entity.snowball.throw");
    private static final Key EGG_THROW = Key.of("entity.egg.throw");
    private static final Key ENDER_PEARL_THROW = Key.of("entity.ender_pearl.throw");
    private static final Key EXPERIENCE_BOTTLE_THROW = Key.of("entity.experience_bottle.throw");
    private static final Key WIND_CHARGE_THROW = Key.of("entity.wind_charge.throw");
    private static final Key ARROW_SHOOT = Key.of("entity.arrow.shoot");
    private static final Key CROSSBOW_SHOOT = Key.of("item.crossbow.shoot");
    private static final Set<Key> PROJECTILE_SOUNDS = MiscUtils.init(new HashSet<>(), s -> {
       s.add(SNOWBALL_THROW);
       s.add(EGG_THROW);
       s.add(TRIDENT_THROW);
       s.add(ENDER_PEARL_THROW);
       s.add(EXPERIENCE_BOTTLE_THROW);
       s.add(WIND_CHARGE_THROW);
       s.add(ARROW_SHOOT);
       s.add(CROSSBOW_SHOOT);
    });

    private SoundListener() {}

    @SuppressWarnings("DuplicatedCode")
    @Override
    public void onPacketSend(NetWorkUser user, ByteBufPacketEvent event) {
        FriendlyByteBuf buf = event.getBuffer();
        int id = buf.readVarInt();
        if (id == 0) {
            Key soundId = buf.readKey();
            Float range = null;
            if (buf.readBoolean()) {
                range = buf.readFloat();
            }
            int source = buf.readVarInt();
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            float volume = buf.readFloat();
            float pitch = buf.readFloat();
            long seed = buf.readLong();
            Key mapped = BukkitBlockManager.instance().replaceSoundIfExist(soundId);
            if (mapped != null) {
                event.setChanged(true);
                buf.clear();
                buf.writeVarInt(event.packetID());
                buf.writeVarInt(0);
                buf.writeKey(mapped);
                if (range != null) {
                    buf.writeBoolean(true);
                    buf.writeFloat(range);
                } else {
                    buf.writeBoolean(false);
                }
                buf.writeVarInt(source);
                buf.writeInt(x);
                buf.writeInt(y);
                buf.writeInt(z);
                buf.writeFloat(volume);
                buf.writeFloat(pitch);
                buf.writeLong(seed);
            }
        } else {
            Object soundEvent = IdMapProxy.INSTANCE.byId(BuiltInRegistriesProxy.SOUND_EVENT, id - 1);
            if (soundEvent == null) return;
            Key soundId = KeyUtils.identifierToKey(SoundEventProxy.INSTANCE.getLocation(soundEvent));
            int source = buf.readVarInt();
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            float volume = buf.readFloat();
            float pitch = buf.readFloat();
            long seed = buf.readLong();

            // 只取消原版的
            if (PROJECTILE_SOUNDS.contains(soundId)) {
                SoundLocation soundLocation = new SoundLocation(user.clientSideWorld().uuid(), x, y, z);
                Object optionalIgnoredSound = IGNORED_SOUNDS.getIfPresent(soundLocation);
                if (optionalIgnoredSound != null) {
                    event.setCancelled(true);
                    return;
                }
            }

            Key mapped = BukkitBlockManager.instance().replaceSoundIfExist(soundId);
            if (mapped != null) {
                event.setChanged(true);
                buf.clear();
                buf.writeVarInt(event.packetID());
                buf.writeVarInt(0);
                Object newId = KeyUtils.toIdentifier(mapped);
                Object newSoundEvent = SoundEventProxy.INSTANCE.create(newId, SoundEventProxy.INSTANCE.fixedRange(soundEvent));
                if (VersionHelper.isOrAbove1_20_5) {
                    StreamEncoderProxy.INSTANCE.encode(SoundEvent$DIRECT_STREAM_CODEC, buf, newSoundEvent);
                } else {
                    SoundEventProxy.INSTANCE.writeToNetwork(newSoundEvent, PacketUtils.ensureNMSFriendlyByteBuf(buf));
                }
                buf.writeVarInt(source);
                buf.writeInt(x);
                buf.writeInt(y);
                buf.writeInt(z);
                buf.writeFloat(volume);
                buf.writeFloat(pitch);
                buf.writeLong(seed);
            }
        }
    }

    public static void addTempIgnoredSound(Location location) {
        IGNORED_SOUNDS.put(SoundLocation.fromLocation(location), new Object());
    }

    public record SoundLocation(UUID world, int x, int y, int z) {

        public static SoundLocation fromLocation(Location loc) {
            int x = (int) (loc.getX() * 8d);
            int y = (int) (loc.getY() * 8d);
            int z = (int) (loc.getZ() * 8d);
            UUID uuid = loc.getWorld().getUID();
            return new SoundLocation(uuid, x, y, z);
        }
    }
}
