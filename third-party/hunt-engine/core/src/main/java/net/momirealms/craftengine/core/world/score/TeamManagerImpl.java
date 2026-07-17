package net.momirealms.craftengine.core.world.score;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.LegacyChatFormatter;
import net.momirealms.craftengine.core.util.VersionHelper;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TeamManagerImpl implements TeamManager {
    private static TeamManagerImpl instance;
    private final CraftEngine plugin;
    private final Map<LegacyChatFormatter, String> teamNameByColor = new EnumMap<>(LegacyChatFormatter.class);
    private List<ByteBuf> addTeamsPackets;

    public TeamManagerImpl(CraftEngine plugin) {
        instance = this;
        this.plugin = plugin;
    }

    public static TeamManagerImpl instance() {
        return instance;
    }

    @Override
    public String getTeamNameByColor(LegacyChatFormatter color) {
        return this.teamNameByColor.get(color);
    }

    @Override
    public List<ByteBuf> addTeamsPackets() {
        return this.addTeamsPackets;
    }

    @Override
    public void init() {
        List<ByteBuf> packets = new ObjectArrayList<>();
        int packetId = this.plugin.platform().packetIds().clientboundSetPlayerTeamPacket();
        LegacyChatFormatter[] values = LegacyChatFormatter.values();
        for (int i = 0; i < 16; i++) {
            LegacyChatFormatter color = values[i];
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeVarInt(packetId);
            String teamName = TeamManager.createTeamName(color);
            buf.writeUtf(teamName); // name
            buf.writeByte(0); // method 0 = add
            buf.writeComponent(Component.text(teamName)); // displayName
            if (VersionHelper.isOrAbove26_2) {
                buf.writeComponent(Component.empty()); // playerPrefix
                buf.writeComponent(Component.empty()); // playerSuffix
            } else {
                buf.writeByte(3); // options isAllowFriendlyFire && canSeeFriendlyInvisibles
            }
            if (VersionHelper.isOrAbove1_21_5) {
                buf.writeVarInt(0); // nametagVisibility
                buf.writeVarInt(0); // collisionRule
            } else {
                buf.writeUtf("always"); // nametagVisibility
                buf.writeUtf("always"); // collisionRule
            }
            if (VersionHelper.isOrAbove26_2) {
                buf.writeOptional(Optional.of(color), FriendlyByteBuf::writeEnumConstant); // color
            } else {
                buf.writeEnumConstant(color); // color
                buf.writeComponent(Component.empty()); // playerPrefix
                buf.writeComponent(Component.empty()); // playerSuffix
            }
            if (VersionHelper.isOrAbove26_2) {
                buf.writeByte(3); // options isAllowFriendlyFire && canSeeFriendlyInvisibles
            }
            buf.writeStringList(List.of()); // players
            this.teamNameByColor.put(color, teamName);
            packets.add(buf);
        }
        this.addTeamsPackets = packets;
    }
}
