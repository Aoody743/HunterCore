package net.momirealms.craftengine.bukkit.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.plugin.command.BukkitCommandFeature;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.bukkit.util.KeyUtils;
import net.momirealms.craftengine.core.advancement.AdvancementType;
import net.momirealms.craftengine.core.item.BuildableItem;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.command.CraftEngineCommandManager;
import net.momirealms.craftengine.core.plugin.command.FlagKeys;
import net.momirealms.craftengine.core.plugin.context.PlayerOptionalContext;
import net.momirealms.craftengine.core.plugin.locale.MessageConstants;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.NamespacedKeyParser;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ToastCommand extends BukkitCommandFeature<CommandSender> {

    public ToastCommand(CraftEngineCommandManager<CommandSender> commandManager, CraftEngine plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .flag(FlagKeys.SILENT_FLAG)
                .required("players", MultiplePlayerSelectorParser.multiplePlayerSelectorParser(false))
                .required("type", EnumParser.enumComponent(AdvancementType.class))
                .required("item", NamespacedKeyParser.namespacedKeyComponent().suggestionProvider(new SuggestionProvider<>() {
                    @Override
                    public @NonNull CompletableFuture<? extends @NonNull Iterable<? extends @NonNull Suggestion>> suggestionsFuture(@NonNull CommandContext<Object> context, @NonNull CommandInput input) {
                        Collection<Suggestion> suggestions = new ArrayList<>(plugin().itemManager().cachedCustomItemSuggestions());
                        for (Key vanilla : BukkitItemManager.instance().vanillaItems()) {
                            suggestions.add(Suggestion.suggestion(vanilla.asString()));
                        }
                        return CompletableFuture.completedFuture(suggestions);
                    }
                }))
                .required("message", StringParser.stringComponent(StringParser.StringMode.GREEDY_FLAG_YIELDING))
                .handler(context -> {
                    MultiplePlayerSelector selector = context.get("players");
                    Collection<Player> players = selector.values();
                    AdvancementType type = context.get("type");
                    NamespacedKey namespacedKey = context.get("item");
                    Key key = KeyUtils.namespacedKeyToKey(namespacedKey);
                    String message = context.get("message");
                    Optional<? extends BuildableItem> buildableItem = plugin().itemManager().getBuildableItem(key);
                    if (buildableItem.isEmpty()) {
                        handleFeedback(context, MessageConstants.COMMAND_TOAST_FAILURE_ITEM_NOT_EXIST, Component.text(key.toString()));
                        return;
                    }
                    for (Player player : players) {
                        BukkitServerPlayer serverPlayer = BukkitAdaptor.adapt(player);
                        if (serverPlayer == null) continue;
                        Item icon = buildableItem.get().buildItem(serverPlayer);
                        Component text = AdventureHelper.miniMessage().deserialize(AdventureHelper.legacyToMiniMessage(message), PlayerOptionalContext.of(serverPlayer).tagResolvers());
                        serverPlayer.sendToast(text, icon, type);
                    }
                    if (players.size() == 1) {
                        handleFeedback(context, MessageConstants.COMMAND_TOAST_SUCCESS_SINGLE, Component.text(key.toString()), Component.text(players.iterator().next().getName()));
                    } else if (players.size() > 1) {
                        handleFeedback(context, MessageConstants.COMMAND_TOAST_SUCCESS_MULTIPLE, Component.text(key.toString()), Component.text(players.size()));
                    }
                });
    }

    @Override
    public String getFeatureID() {
        return "toast";
    }
}
