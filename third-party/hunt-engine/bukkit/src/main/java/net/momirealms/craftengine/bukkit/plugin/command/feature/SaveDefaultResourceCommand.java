package net.momirealms.craftengine.bukkit.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.plugin.command.BukkitCommandFeature;
import net.momirealms.craftengine.core.pack.AbstractPackManager;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.command.CraftEngineCommandManager;
import net.momirealms.craftengine.core.plugin.locale.MessageConstants;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.io.IOException;
import java.util.Optional;

public final class SaveDefaultResourceCommand extends BukkitCommandFeature<CommandSender> {

    public SaveDefaultResourceCommand(CraftEngineCommandManager<CommandSender> commandManager, CraftEngine plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .optional("path", StringParser.stringComponent(StringParser.StringMode.GREEDY).suggestionProvider(SuggestionProvider.suggesting(
                        Suggestion.suggestion("/default_assets"),
                        Suggestion.suggestion("/default_feature_populator"),
                        Suggestion.suggestion("/default_templates"),
                        Suggestion.suggestion("/internal"),
                        Suggestion.suggestion("/legacy_armor"),
                        Suggestion.suggestion("/remove_shulker_head")
                )))
                .handler(context -> {
                    AbstractPackManager packManager = (AbstractPackManager) CraftEngine.instance().packManager();
                    Optional<String> path = context.optional("path");
                    String finalPath = path.orElse("");
                    try {
                        packManager.saveDefaultConfigs(finalPath);
                    } catch (IOException e) {
                        handleFeedback(context, MessageConstants.COMMAND_RESOURCE_SAVE_DEFAULT_FAILURE);
                        this.plugin().logger().warn("Failed to save default configs", e);
                        return;
                    }
                    handleFeedback(context, MessageConstants.COMMAND_RESOURCE_SAVE_DEFAULT_SUCCESS, Component.text("resources" + finalPath));
                });
    }

    @Override
    public String getFeatureID() {
        return "save_default_resource";
    }
}
