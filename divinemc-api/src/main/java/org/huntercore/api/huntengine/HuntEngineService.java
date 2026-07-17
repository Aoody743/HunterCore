package org.huntercore.api.huntengine;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * HunterCore's stable, implementation-neutral contract for the bundled HuntEngine plugin.
 *
 * <p>Implementations are registered through Bukkit's {@code ServicesManager}. Consumers must use
 * {@link HuntEngineServices#get()} rather than importing CraftEngine/HuntEngine implementation
 * classes or reading the engine's files directly.</p>
 */
public interface HuntEngineService {

    boolean available();

    @NotNull HuntEngineStatus status();

    @NotNull HuntEngineCatalogue catalogue();

    default @NotNull Optional<HuntEngineContent> content(@NotNull final String id) {
        return this.catalogue().contents().stream().filter(content -> content.id().equals(id)).findFirst();
    }

    @NotNull Collection<HuntEngineContentPackage> stagedContentPackages();

    @NotNull HuntEngineActionResult stageContentPackage(@NotNull HuntEngineContentPackageUpload upload);

    @NotNull HuntEngineActionResult removeStagedContentPackage(@NotNull String packageId);

    @NotNull HuntEngineActionResult give(@NotNull Player player, @NotNull String contentId, int amount);

    @NotNull HuntEngineResourcePack resourcePack();

    /**
     * Requests the currently active engine pack once. The implementation owns duplicate-request
     * suppression and PlayerResourcePackStatusEvent tracking.
     */
    @NotNull HuntEngineActionResult requestResourcePack(@NotNull Player player);

    boolean resourcePackReady(@NotNull UUID playerId);

    @NotNull HuntEngineOperationTicket validate();

    @NotNull HuntEngineOperationTicket build();

    @NotNull HuntEngineOperationTicket publish();

    @NotNull HuntEngineOperationTicket reload();

    default @NotNull Optional<HuntEngineOperation> operation(@NotNull final UUID operationId) {
        return Optional.empty();
    }

    @NotNull HuntEngineMigrationReport migrationReport();
}
