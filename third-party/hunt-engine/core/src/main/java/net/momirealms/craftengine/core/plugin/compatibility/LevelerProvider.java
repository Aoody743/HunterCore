package net.momirealms.craftengine.core.plugin.compatibility;

import net.momirealms.craftengine.core.entity.player.Player;

public interface LevelerProvider {

    String plugin();

    void addExp(Player player, String target, double amount);

    int getLevel(Player player, String target);
}