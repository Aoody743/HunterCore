package org.huntercore.api.fakeplayer;

import java.util.Collection;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface HunterFakePlayerService {

    boolean available();

    @NotNull Collection<FakePlayerSnapshot> list();

    @NotNull Optional<FakePlayerSnapshot> snapshot(@NotNull String name);

    @NotNull FakePlayerActionResult spawn(@NotNull String name, @NotNull Location location);

    default @NotNull FakePlayerActionResult respawn(@NotNull final String name) {
        return FakePlayerActionResult.fail("Fake player respawn is not supported by this service.");
    }

    default @NotNull FakePlayerActionResult openInventoryEditor(@NotNull final String name, @NotNull final Player viewer) {
        return FakePlayerActionResult.fail("Fake player inventory editing is not supported by this service.");
    }

    default @NotNull FakePlayerActionResult setSkinProfile(@NotNull final String name, @Nullable final PlayerProfile skinProfile) {
        return FakePlayerActionResult.fail("Fake player skin changes are not supported by this service.");
    }

    default @NotNull FakePlayerActionResult setSkinTexture(@NotNull final String name, @NotNull final String textureValue, @Nullable final String textureSignature) {
        return FakePlayerActionResult.fail("Fake player skin changes are not supported by this service.");
    }

    default @NotNull FakePlayerActionResult equipArmor(@NotNull final String name, @NotNull final ItemStack item) {
        return FakePlayerActionResult.fail("Fake player armor equip is not supported by this service.");
    }

    @NotNull FakePlayerActionResult remove(@NotNull String name);

    @NotNull FakePlayerActionResult removeAll();

    @NotNull FakePlayerActionResult teleport(@NotNull String name, @NotNull Location location);

    @NotNull FakePlayerActionResult look(@NotNull String name, float yaw, float pitch);

    @NotNull FakePlayerActionResult setSneaking(@NotNull String name, boolean sneaking);

    @NotNull FakePlayerActionResult setSprinting(@NotNull String name, boolean sprinting);

    @NotNull FakePlayerActionResult move(@NotNull String name, double forward, double sideways, boolean jump, boolean sprinting, boolean sneaking);

    @NotNull FakePlayerActionResult jump(@NotNull String name);

    @NotNull FakePlayerActionResult use(@NotNull String name);

    default @NotNull FakePlayerActionResult placeBlock(@NotNull final String name, @NotNull final Location clickedBlock, @NotNull final BlockFace face) {
        return FakePlayerActionResult.fail("Fake player block placement is not supported by this service.");
    }

    @NotNull FakePlayerActionResult attack(@NotNull String name);

    default @NotNull FakePlayerActionResult attackEntity(@NotNull final String name, @NotNull final Entity target) {
        return FakePlayerActionResult.fail("Fake player direct entity attack is not supported by this service.");
    }

    @NotNull FakePlayerActionResult stopActions(@NotNull String name);

    @NotNull FakePlayerActionResult dropSelected(@NotNull String name, boolean stack);

    @NotNull FakePlayerActionResult swapHands(@NotNull String name);

    @NotNull FakePlayerActionResult setGameMode(@NotNull String name, @NotNull GameMode gameMode);

    @NotNull FakePlayerActionResult setSelectedSlot(@NotNull String name, int slot);
}
