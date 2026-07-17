package net.momirealms.craftengine.core.plugin;

import com.google.gson.JsonElement;
import io.netty.buffer.ByteBuf;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.network.id.PacketIds;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.Container;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.particle.ParticleType;
import net.momirealms.sparrow.nbt.Tag;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public interface Platform {

    void dispatchCommand(String command);

    Tag jsonToSparrowNBT(JsonElement json);

    Tag javaToSparrowNBT(Object object);

    JsonElement sparrowNBTToJson(Tag object);

    World getWorld(String name);

    Player getPlayer(UUID uuid);

    ParticleType getParticleType(Key name);

    int biomeCount();

    Object createContainer(Container container);

    Item readItem(ByteBuf buf);

    void writeItem(ByteBuf buf, Item item);

    PacketIds packetIds();

    int getServerPort();

    boolean hasProxy();

    AtomicInteger getEntityCounter();
}
