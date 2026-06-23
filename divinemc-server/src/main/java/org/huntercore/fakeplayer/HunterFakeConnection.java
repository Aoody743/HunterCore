package org.huntercore.fakeplayer;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

final class HunterFakeConnection extends Connection {

    HunterFakeConnection() {
        super(PacketFlow.SERVERBOUND);
        final EmbeddedChannel fakeChannel = new EmbeddedChannel();
        final ChannelPipeline pipeline = fakeChannel.pipeline();
        Connection.configureInMemoryPipeline(pipeline, PacketFlow.SERVERBOUND);
        if (pipeline.get(HandlerNames.ENCODER) == null) {
            pipeline.addLast(HandlerNames.ENCODER, new ChannelOutboundHandlerAdapter());
        }
        this.configureViaVersionPipeline(pipeline, fakeChannel);
        this.configurePacketHandler(pipeline);
        this.channel = fakeChannel;
        final InetSocketAddress loopback = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        this.address = loopback;
        this.virtualHost = loopback;
        this.hostname = "huntercore-fake-player";
        this.preparing = false;
        this.isPending = false;
    }

    @Override
    public void send(final Packet<?> packet, final @Nullable ChannelFutureListener listener, final boolean flush) {
        final ServerPlayer player = this.getPlayer();
        if (player != null) {
            packet.onPacketDispatch(player);
        }
        if (player != null && packet.hasFinishListener()) {
            packet.onPacketDispatchFinish(player, null);
        }
        if (listener != null && this.channel != null) {
            try {
                final ChannelFuture future = this.channel.newSucceededFuture();
                listener.operationComplete(future);
            } catch (final Exception ex) {
                throw new RuntimeException("Fake player packet listener failed", ex);
            }
        }
    }

    @Override
    public void flushChannel() {
    }

    private void configureViaVersionPipeline(final ChannelPipeline pipeline, final EmbeddedChannel channel) {
        try {
            final Plugin plugin = Bukkit.getPluginManager().getPlugin("ViaVersion");
            if (plugin == null || !plugin.isEnabled()) {
                return;
            }
            final ClassLoader loader = plugin.getClass().getClassLoader();
            final Class<?> userConnectionClass = Class.forName("com.viaversion.viaversion.connection.UserConnectionImpl", true, loader);
            final Class<?> userConnectionInterface = Class.forName("com.viaversion.viaversion.api.connection.UserConnection", true, loader);
            final Class<?> encodeHandlerClass = Class.forName("com.viaversion.viaversion.bukkit.handlers.BukkitEncodeHandler", true, loader);
            final Class<?> decodeHandlerClass = Class.forName("com.viaversion.viaversion.bukkit.handlers.BukkitDecodeHandler", true, loader);
            final Object userConnection = userConnectionClass
                .getConstructor(io.netty.channel.Channel.class, boolean.class)
                .newInstance(channel, false);
            final ChannelHandler encoder = (ChannelHandler) encodeHandlerClass
                .getConstructor(userConnectionInterface)
                .newInstance(userConnection);
            final ChannelHandler decoder = (ChannelHandler) decodeHandlerClass
                .getConstructor(userConnectionInterface)
                .newInstance(userConnection);
            if (pipeline.get(decodeHandlerClass.asSubclass(ChannelHandler.class)) == null) {
                pipeline.addFirst("huntercore-via-decoder", decoder);
            }
            if (pipeline.get(encodeHandlerClass.asSubclass(ChannelHandler.class)) == null) {
                if (pipeline.get(HandlerNames.ENCODER) != null) {
                    pipeline.addBefore(HandlerNames.ENCODER, "huntercore-via-encoder", encoder);
                } else {
                    pipeline.addLast("huntercore-via-encoder", encoder);
                }
            }
        } catch (final Throwable ignored) {
            // ViaVersion is optional. Fake players still work without its in-memory handlers.
        }
    }
}
