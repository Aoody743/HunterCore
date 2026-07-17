package net.momirealms.craftengine.bukkit.plugin.context.condition;

import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.Condition;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.PlayerContext;
import net.momirealms.craftengine.core.plugin.context.condition.ConditionFactory;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class TestFlagCondition<CTX extends Context> implements Condition<CTX> {
    private final Flag flag;

    public TestFlagCondition(Flag flag) {
        this.flag = flag;
    }

    @Override
    public boolean test(CTX ctx) {
        if (ctx instanceof PlayerContext playerContext) {
            Optional<WorldPosition> position = ctx.getOptionalParameter(DirectContextParameters.POSITION);
            if (position.isPresent()) {
                return BukkitCraftEngine.instance().antiGriefProvider().test(
                        (Player) playerContext.player().platformPlayer(),
                        this.flag.flag(),
                        LocationUtils.toLocation(position.get())
                );
            }
        }
        return false;
    }

    public static <CTX extends Context> ConditionFactory<CTX, TestFlagCondition<CTX>> factory() {
        return new Factory<>();
    }

    public enum Flag {
        BREAK(net.momirealms.antigrieflib.Flag.BREAK),
        PLACE(net.momirealms.antigrieflib.Flag.PLACE),
        INTERACT(net.momirealms.antigrieflib.Flag.INTERACT),
        OPEN_CONTAINER(net.momirealms.antigrieflib.Flag.OPEN_CONTAINER),
        ;

        private final net.momirealms.antigrieflib.Flag<?> flag;

        Flag(net.momirealms.antigrieflib.Flag<?> flag) {
            this.flag = flag;
        }

        @SuppressWarnings("unchecked")
        public <T> net.momirealms.antigrieflib.Flag<T> flag() {
            return (net.momirealms.antigrieflib.Flag<T>) this.flag;
        }
    }

    private static class Factory<CTX extends Context> implements ConditionFactory<CTX, TestFlagCondition<CTX>> {

        @Override
        public TestFlagCondition<CTX> create(ConfigSection section) {
            return new TestFlagCondition<>(
                    section.getEnum("flag", Flag.class, Flag.BREAK)
            );
        }
    }
}