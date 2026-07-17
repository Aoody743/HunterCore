package org.huntercore.api.huntengine;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

/** Accesses the currently registered HuntEngine service without binding consumers to its plugin. */
public final class HuntEngineServices {
    private static final HuntEngineService UNAVAILABLE = new UnavailableHuntEngineService();
    private static volatile Supplier<HuntEngineService> resolver = () -> UNAVAILABLE;

    private HuntEngineServices() {
    }

    public static @NotNull HuntEngineService get() {
        try {
            final RegisteredServiceProvider<HuntEngineService> registration = Bukkit.getServicesManager().getRegistration(HuntEngineService.class);
            if (registration != null && registration.getProvider() != null) {
                return registration.getProvider();
            }
        } catch (final IllegalStateException ignored) {
            // The resolver is deliberately still consulted: HunterCore can finish starting before
            // Bukkit exposes a service manager, and will then safely return unavailable.
        }
        final HuntEngineService resolved = resolver.get();
        return resolved == null ? UNAVAILABLE : resolved;
    }

    /**
     * Installs HunterCore's server-side resolver for the independently bundled HuntEngine plugin.
     *
     * <p>This is an internal bootstrap hook. The resolver must register a real provider through
     * Bukkit's {@code ServicesManager} as soon as the engine becomes available; callers continue
     * to use {@link #get()}.</p>
     */
    public static void installResolver(@NotNull final Supplier<HuntEngineService> resolver) {
        HuntEngineServices.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public static @NotNull HuntEngineService unavailable() {
        return UNAVAILABLE;
    }

    private static final class UnavailableHuntEngineService implements HuntEngineService {
        private static final String MESSAGE = "HuntEngine is not available.";

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public @NotNull HuntEngineStatus status() {
            return HuntEngineStatus.unavailable();
        }

        @Override
        public @NotNull HuntEngineCatalogue catalogue() {
            return HuntEngineCatalogue.unavailable();
        }

        @Override
        public @NotNull Collection<HuntEngineContentPackage> stagedContentPackages() {
            return List.of();
        }

        @Override
        public @NotNull HuntEngineActionResult stageContentPackage(@NotNull final HuntEngineContentPackageUpload upload) {
            return HuntEngineActionResult.fail(MESSAGE);
        }

        @Override
        public @NotNull HuntEngineActionResult removeStagedContentPackage(@NotNull final String packageId) {
            return HuntEngineActionResult.fail(MESSAGE);
        }

        @Override
        public @NotNull HuntEngineActionResult give(@NotNull final Player player, @NotNull final String contentId, final int amount) {
            return HuntEngineActionResult.fail(MESSAGE);
        }

        @Override
        public @NotNull HuntEngineResourcePack resourcePack() {
            return HuntEngineResourcePack.unavailable();
        }

        @Override
        public @NotNull HuntEngineActionResult requestResourcePack(@NotNull final Player player) {
            return HuntEngineActionResult.fail(MESSAGE);
        }

        @Override
        public boolean resourcePackReady(@NotNull final UUID playerId) {
            return false;
        }

        @Override
        public @NotNull HuntEngineOperationTicket validate() {
            return failedTicket(HuntEngineOperationType.VALIDATE);
        }

        @Override
        public @NotNull HuntEngineOperationTicket build() {
            return failedTicket(HuntEngineOperationType.BUILD);
        }

        @Override
        public @NotNull HuntEngineOperationTicket publish() {
            return failedTicket(HuntEngineOperationType.PUBLISH);
        }

        @Override
        public @NotNull HuntEngineOperationTicket reload() {
            return failedTicket(HuntEngineOperationType.RELOAD);
        }

        @Override
        public @NotNull HuntEngineMigrationReport migrationReport() {
            return HuntEngineMigrationReport.unavailable();
        }

        private static @NotNull HuntEngineOperationTicket failedTicket(final HuntEngineOperationType type) {
            final Instant now = Instant.now();
            final HuntEngineOperation operation = new HuntEngineOperation(
                UUID.randomUUID(), type, HuntEngineOperationState.FAILED, -1L, MESSAGE, now, now
            );
            return new HuntEngineOperationTicket(operation, CompletableFuture.completedFuture(operation));
        }
    }
}
