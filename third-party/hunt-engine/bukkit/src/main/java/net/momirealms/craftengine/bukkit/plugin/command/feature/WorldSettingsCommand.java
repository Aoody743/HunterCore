package net.momirealms.craftengine.bukkit.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.plugin.command.BukkitCommandFeature;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.command.CraftEngineCommandManager;
import net.momirealms.craftengine.core.util.Tristate;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.bukkit.parser.WorldParser;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static net.momirealms.craftengine.core.plugin.locale.MessageConstants.*;
import static net.momirealms.craftengine.core.world.WorldSettings.SETTINGS;

public final class WorldSettingsCommand extends BukkitCommandFeature<CommandSender> {
    private static final List<Suggestion> SUGGESTIONS = SETTINGS.stream().map(Suggestion::suggestion).toList();

    public WorldSettingsCommand(CraftEngineCommandManager<CommandSender> commandManager, CraftEngine plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .required("world", WorldParser.worldParser())
                .required("setting", StringParser.stringComponent().suggestionProvider((context, input) -> CompletableFuture.completedFuture(SUGGESTIONS)))
                .optional("value", EnumParser.enumComponent(Tristate.class))
                .handler(context -> {
                    World world = context.get("world");
                    String setting = context.get("setting");
                    if (!SETTINGS.contains(setting)) {
                        handleFeedback(context, COMMAND_WORLD_SETTINGS_FAILURE, Component.text(setting));
                        return;
                    }
                    CEWorld ceWorld = BukkitAdaptor.adapt(world).storageWorld();
                    Optional<Tristate> value = context.optional("value");
                    if (value.isPresent()) {
                        ceWorld.settings.set(setting, value.get());
                        ceWorld.saveSettings();
                        handleFeedback(context, COMMAND_WORLD_SETTINGS_SET_SUCCESS, Component.text(setting), Component.text(value.get().name().toLowerCase(Locale.ROOT)));
                    } else {
                        Tristate state = ceWorld.settings.get(setting);
                        if (state != null) {
                            handleFeedback(context, COMMAND_WORLD_SETTINGS_GET_SUCCESS, Component.text(setting), Component.text(state.name().toLowerCase(Locale.ROOT)));
                        }
                    }
                });
    }

    @Override
    public String getFeatureID() {
        return "world_settings";
    }
}
