package net.momirealms.craftengine.core.entity.seat;

import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.world.WorldPosition;

public interface Seat<O extends SeatOwner> {

    O owner();

    SeatConfig config();

    boolean isOccupied();

    void destroy();

    boolean spawnSeat(Player player, WorldPosition source);
}
