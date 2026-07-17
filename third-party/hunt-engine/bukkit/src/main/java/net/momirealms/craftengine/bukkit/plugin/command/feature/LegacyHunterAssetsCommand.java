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

/**
 * 2.9.x-only forwarding surface for the retired HunterAssets command roots.
 *
 * <p>The command intentionally accepts no destructive subcommands. It gives existing players a
 * visible migration notice and opens the same content browser as {@code /huntengine} when their
 * old or new use permission permits it. This avoids keeping a second resource-pack lifecycle or
 * silently translating legacy administrative arguments.</p>
 */
public final class LegacyHunterAssetsCommand extends BukkitCommandFeature<CommandSender> {

    public LegacyHunterAssetsCommand(
        CraftEngineCommandManager<CommandSender> commandManager,
        CraftEngine plugin
    ) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(
        org.incendo.cloud.CommandManager<CommandSender> manager,
        Command.Builder<CommandSender> builder
    ) {
        return builder.handler(context -> {
            final CommandSender sender = context.sender();
            sender.sendMessage(Component.text(
                "HunterAssets was replaced by HuntEngine in HunterCore 2.9.x. Use /huntengine or /he."
            ));
            if (!(sender instanceof Player player)) {
                return;
            }
            if (!canOpenCatalogue(player)) {
                sender.sendMessage(Component.text("You do not have permission to open the HuntEngine catalogue."));
                return;
            }
            final BukkitServerPlayer serverPlayer = BukkitAdaptor.adapt(player);
            if (serverPlayer == null) {
                return;
            }
            try {
                plugin().itemBrowserManager().open(serverPlayer);
            } catch (GuiElementMissingException ex) {
                sender.sendMessage(Component.text("The HuntEngine item browser is not available yet."));
            }
        });
    }

    @Override
    public String getFeatureID() {
        return "legacy_hunterassets";
    }

    private static boolean canOpenCatalogue(CommandSender sender) {
        return sender.hasPermission("huntengine.use")
            || sender.hasPermission("hunterassets.use")
            || sender.hasPermission("huntengine.admin")
            || sender.hasPermission("hunterassets.admin");
    }
}
