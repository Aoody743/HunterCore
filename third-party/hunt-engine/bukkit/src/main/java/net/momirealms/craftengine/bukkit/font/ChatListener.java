package net.momirealms.craftengine.bukkit.font;

import io.papermc.paper.event.player.AsyncChatCommandDecorateEvent;
import io.papermc.paper.event.player.AsyncChatDecorateEvent;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.plugin.network.BukkitNetworkManager;
import net.momirealms.craftengine.bukkit.util.ComponentUtils;
import net.momirealms.craftengine.core.font.EmojiTextProcessResult;
import net.momirealms.craftengine.core.font.EmojiUseCase;
import net.momirealms.craftengine.core.font.FontManager;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.plugin.network.IllegalCharacterProcessResult;
import net.momirealms.craftengine.proxy.paper.event.player.AsyncChatDecorateEventProxy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {
    private final BukkitFontManager manager;

    public ChatListener(BukkitFontManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("UnstableApiUsage")
    public void onChat(AsyncChatDecorateEvent event) {
        if (!Config.filterChat()) return;
        this.processChatEvent(event, EmojiUseCase.CHAT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("UnstableApiUsage")
    public void onChatCommand(AsyncChatCommandDecorateEvent event) {
        if (!Config.filterChat()) return;
        this.processChatEvent(event, EmojiUseCase.COMMAND);
    }

    // fixme 这些做法其实是错误的，我们只应该修改字体为minecraft:default的部分
    @SuppressWarnings("UnstableApiUsage")
    void processChatEvent(AsyncChatDecorateEvent event, EmojiUseCase useCase) {
        Player player = event.player();
        if (player == null) return;
        Object originalMessage = AsyncChatDecorateEventProxy.INSTANCE.getResult(event);
        String rawJsonMessage = ComponentUtils.paperAdventureToJson(originalMessage);
        boolean changed = false;
        if (!player.hasPermission(FontManager.BYPASS_CHAT)) {
            IllegalCharacterProcessResult result = BukkitNetworkManager.instance().processIllegalCharacters(rawJsonMessage);
            if (result.has()) {
                rawJsonMessage = result.text();
                changed = true;
            }
        }
        if (Config.allowEmojiChat()/* && !Config.disableChatReport()*/) {
            EmojiTextProcessResult result = this.manager.replaceJsonEmoji(rawJsonMessage, BukkitAdaptor.adapt(player), useCase);
            if (result.replaced()) {
                rawJsonMessage = result.text();
                changed = true;
            }
        }
        if (changed) {
            AsyncChatDecorateEventProxy.INSTANCE.setResult(event, ComponentUtils.jsonToPaperAdventure(rawJsonMessage));
        }
    }
}
