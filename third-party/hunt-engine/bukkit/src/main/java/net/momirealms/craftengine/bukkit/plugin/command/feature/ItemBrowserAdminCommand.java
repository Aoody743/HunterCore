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
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;

import java.util.Collection;

import static net.momirealms.craftengine.core.plugin.locale.MessageConstants.COMMAND_ITEM_BROWSER_MISSING_ELEMENT;

public final class ItemBrowserAdminCommand extends BukkitCommandFeature<CommandSender> {

    public ItemBrowserAdminCommand(CraftEngineCommandManager<CommandSender> commandManager, CraftEngine plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .required("players", MultiplePlayerSelectorParser.multiplePlayerSelectorParser(false))
                .handler(context -> {
                    MultiplePlayerSelector selector = context.get("players");
                    Collection<Player> players = selector.values();
                    for (Player player : players) {
                        BukkitServerPlayer serverPlayer = BukkitAdaptor.adapt(player);
                        if (serverPlayer == null) continue;
                        try {
                            plugin().itemBrowserManager().open(serverPlayer);
                        } catch (GuiElementMissingException e) {
                            handleFeedback(context, COMMAND_ITEM_BROWSER_MISSING_ELEMENT, Component.text(e.getElement().asString()));
                        }
                    }
                });
    }

    @Override
    public String getFeatureID() {
        return "item_browser_admin";
    }
}
