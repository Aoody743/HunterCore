package net.momirealms.craftengine.bukkit.plugin.network.listener.game;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.momirealms.craftengine.bukkit.item.BukkitItem;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.util.PacketUtils;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.core.plugin.network.event.NMSPacketEvent;
import net.momirealms.craftengine.core.plugin.network.listener.NMSPacketListener;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundSetEquipmentPacketProxy;

import java.util.List;
import java.util.Optional;

public final class NMSSetEquipmentListener implements NMSPacketListener {
    public static final NMSSetEquipmentListener INSTANCE = new NMSSetEquipmentListener();

    @Override
    public void onPacketSend(NetWorkUser user, NMSPacketEvent event, Object packet) {
        if (Config.disableItemOperations()) return;
        BukkitServerPlayer serverPlayer = (BukkitServerPlayer) user;
        List<Pair<Object, Object>> slots = ClientboundSetEquipmentPacketProxy.INSTANCE.getSlots(packet);
        int size = slots.size();
        boolean changed = false;
        List<Pair<Object, Object>> newSlots = Lists.newArrayList();
        for (int i = 0; i < size; i++) {
            Pair<Object, Object> pair = slots.get(i);
            Object item = pair.getSecond();
            Optional<Item> optional = BukkitItemManager.instance().s2c(ItemStackUtils.wrap(item).copy(), serverPlayer);
            if (optional.isPresent()) {
                changed = true;
                item = optional.get().minecraftItem();
            }
            newSlots.add(Pair.of(pair.getFirst(), item));
        }
        if (changed) {
            PacketUtils.replacePacket(event, packet, ClientboundSetEquipmentPacketProxy.INSTANCE.newInstance(
                    ClientboundSetEquipmentPacketProxy.INSTANCE.getEntityId(packet),
                    newSlots
            ));
        }
    }
}
