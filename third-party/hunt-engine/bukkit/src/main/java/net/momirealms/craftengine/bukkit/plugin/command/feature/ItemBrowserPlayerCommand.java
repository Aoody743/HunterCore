package net.momirealms.craftengine.bukkit.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.plugin.command.BukkitCommandFeature;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.command.CraftEngineCommandManager;
import net.momirealms.craftengine.core.plugin.gui.GuiElementMissingException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;

import static net.momirealms.craftengine.core.plugin.locale.MessageConstants.COMMAND_ITEM_BROWSER_MISSING_ELEMENT;

public final class ItemBrowserPlayerCommand extends BukkitCommandFeature<CommandSender> {

    public ItemBrowserPlayerCommand(CraftEngineCommandManager<CommandSender> commandManager, CraftEngine plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .senderType(Player.class)
                .handler(context -> {
                    Player player = context.sender();
                    BukkitServerPlayer serverPlayer = BukkitAdaptor.adapt(player);
                    if (serverPlayer == null) return;
                    try {
                        plugin().itemBrowserManager().open(serverPlayer);
                    } catch (GuiElementMissingException e) {
                        handleFeedback(context, COMMAND_ITEM_BROWSER_MISSING_ELEMENT, Component.text(e.getElement().asString()));
                    }
                });
    }

    @Override
    public String getFeatureID() {
        return "item_browser_player";
    }
}
