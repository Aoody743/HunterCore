package org.huntercore.huntengine;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.huntercore.api.huntengine.HuntEngineActionResult;
import org.huntercore.api.huntengine.HuntEngineCatalogue;
import org.huntercore.api.huntengine.HuntEngineContent;
import org.huntercore.api.huntengine.HuntEngineContentKind;
import org.huntercore.api.huntengine.HuntEngineContentPackage;
import org.huntercore.api.huntengine.HuntEngineContentPackageState;
import org.huntercore.api.huntengine.HuntEngineContentPackageUpload;
import org.huntercore.api.huntengine.HuntEngineLifecycleState;
import org.huntercore.api.huntengine.HuntEngineMigrationEntry;
import org.huntercore.api.huntengine.HuntEngineMigrationEntryState;
import org.huntercore.api.huntengine.HuntEngineMigrationReport;
import org.huntercore.api.huntengine.HuntEngineMigrationState;
import org.huntercore.api.huntengine.HuntEngineOperation;
import org.huntercore.api.huntengine.HuntEngineOperationState;
import org.huntercore.api.huntengine.HuntEngineOperationTicket;
import org.huntercore.api.huntengine.HuntEngineOperationType;
import org.huntercore.api.huntengine.HuntEngineResourcePack;
import org.huntercore.api.huntengine.HuntEngineService;
import org.huntercore.api.huntengine.HuntEngineServices;
import org.huntercore.api.huntengine.HuntEngineStatus;
import org.slf4j.Logger;

/**
 * Registers a HunterCore-facing service around the independently built HuntEngine jar.
 *
 * <p>The adapter deliberately uses reflection. HuntEngine remains a self-contained GPL project
 * with no HunterCore API compile dependency, while server modules receive a stable contract.
 * Reflection is limited to the preserved upstream package names documented by the pinned source.
 * A failed lookup makes the service unavailable instead of breaking other bundled plugins.</p>
 */
public final class HunterHuntEngineServiceManager {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final String PLUGIN_NAME = "HuntEngine";
    private static final String ENGINE_CLASS = "net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine";
    private static final String KEY_CLASS = "net.momirealms.craftengine.core.util.Key";
    private static final String ADAPTOR_CLASS = "net.momirealms.craftengine.bukkit.api.BukkitAdaptor";
    private static final String CONFIG_CLASS = "net.momirealms.craftengine.core.plugin.config.Config";
    private static final Object LOCK = new Object();
    private static volatile ReflectiveHuntEngineService registered;

    private HunterHuntEngineServiceManager() {
    }

    /**
     * Resolves and registers an adapter once the independently loaded HuntEngine plugin is enabled.
     * This method is safe to call before Bukkit or HuntEngine is fully initialized.
     */
    public static HuntEngineService resolve() {
        try {
            final RegisteredServiceProvider<HuntEngineService> existing = Bukkit.getServicesManager().getRegistration(HuntEngineService.class);
            if (existing != null && existing.getProvider() != null) {
                return existing.getProvider();
            }
            final Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (plugin == null || !plugin.isEnabled()) {
                return HuntEngineServices.unavailable();
            }
            synchronized (LOCK) {
                final RegisteredServiceProvider<HuntEngineService> rechecked = Bukkit.getServicesManager().getRegistration(HuntEngineService.class);
                if (rechecked != null && rechecked.getProvider() != null) {
                    return rechecked.getProvider();
                }
                if (registered == null || registered.plugin() != plugin) {
                    registered = new ReflectiveHuntEngineService(plugin);
                }
                registered.registerResourcePackListener();
                Bukkit.getServicesManager().register(HuntEngineService.class, registered, plugin, ServicePriority.Normal);
                LOGGER.info("HunterCore registered the HuntEngine service adapter.");
                return registered;
            }
        } catch (final IllegalStateException | LinkageError ex) {
            return HuntEngineServices.unavailable();
        }
    }

    private static final class ReflectiveHuntEngineService implements HuntEngineService {
        private static final long MAX_UPLOAD_BYTES = 12L * 1024L * 1024L;
        private static final long MAX_UNCOMPRESSED_BYTES = 128L * 1024L * 1024L;
        private static final int MAX_ARCHIVE_ENTRIES = 4_096;
        private static final long OPERATION_TIMEOUT_SECONDS = 180L;
        private final Plugin plugin;
        private final ConcurrentMap<String, HuntEngineContentPackage> stagedPackages = new ConcurrentHashMap<>();
        private final ConcurrentMap<UUID, HuntEngineOperation> operations = new ConcurrentHashMap<>();
        private final ConcurrentMap<UUID, Boolean> resourcePackReady = new ConcurrentHashMap<>();
        private final AtomicLong revision = new AtomicLong();
        private final AtomicReference<String> catalogueFingerprint = new AtomicReference<>("");
        private final AtomicReference<String> publishedPackRevision = new AtomicReference<>("");
        private final AtomicReference<PendingBuild> pendingBuild = new AtomicReference<>();
        private final AtomicBoolean operationInProgress = new AtomicBoolean();
        /**
         * Serializes filesystem workspace changes with the asynchronous build/publish lifecycle.
         * A semaphore is intentional here: the initiating Bukkit thread reserves the workspace and
         * the asynchronous worker releases it after its transaction has finished.
         */
        private final Semaphore workspaceAccess = new Semaphore(1);
        private final AtomicBoolean resourcePackListenerRegistered = new AtomicBoolean();

        private ReflectiveHuntEngineService(final Plugin plugin) {
            this.plugin = plugin;
            this.loadStagedPackages();
            this.loadPublishedPackRevision();
            this.loadPendingBuild();
        }

        private Plugin plugin() {
            return this.plugin;
        }

        @Override
        public boolean available() {
            if (!this.plugin.isEnabled()) {
                return false;
            }
            try {
                return this.engine() != null;
            } catch (final ReflectiveOperationException | RuntimeException ex) {
                return false;
            }
        }

        @Override
        public HuntEngineStatus status() {
            if (!this.plugin.isEnabled()) {
                return HuntEngineStatus.unavailable();
            }
            try {
                final Object engine = this.engine();
                final boolean stopping = booleanMethod(engine, "isStopping");
                final boolean disabled = booleanMethod(engine, "isDisabled");
                final HuntEngineLifecycleState lifecycle = disabled
                    ? HuntEngineLifecycleState.FAILED
                    : stopping ? HuntEngineLifecycleState.STOPPING : HuntEngineLifecycleState.READY;
                return new HuntEngineStatus(
                    lifecycle == HuntEngineLifecycleState.READY,
                    this.plugin.getDescription().getVersion(),
                    this.revision.get(),
                    lifecycle,
                    lifecycle == HuntEngineLifecycleState.READY ? "HuntEngine is ready." : "HuntEngine is stopping or disabled."
                );
            } catch (final ReflectiveOperationException | RuntimeException ex) {
                return new HuntEngineStatus(
                    false,
                    this.plugin.getDescription().getVersion(),
                    this.revision.get(),
                    HuntEngineLifecycleState.FAILED,
                    "HuntEngine adapter could not access the engine: " + conciseMessage(ex)
                );
            }
        }

        @Override
        public HuntEngineCatalogue catalogue() {
            try {
                final Object engine = this.engine();
                final Object itemManager = invoke(this.engine(), "itemManager");
                final Object loaded = invoke(itemManager, "loadedItems");
                if (!(loaded instanceof Map<?, ?> items)) {
                    return HuntEngineCatalogue.unavailable();
                }
                final Map<String, HuntEngineContent> byId = new LinkedHashMap<>();
                for (final Map.Entry<?, ?> entry : items.entrySet()) {
                    final String id = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
                    if (!stableIdentifier(id)) {
                        continue;
                    }
                    final HuntEngineContentKind kind = contentKind(entry.getValue());
                    addCatalogueContent(byId, id, kind, kind == HuntEngineContentKind.ITEM ? "items" : kind.name().toLowerCase(Locale.ROOT));
                }
                // These are all public manager snapshots in the pinned Community Edition. They
                // make non-item native content visible without exposing upstream objects to API
                // consumers. A content detail remains deliberately non-grantable unless an item
                // definition exists and the service's give() revalidation succeeds.
                appendManagerMapContents(byId, engine, "blockManager", "loadedBlocks", HuntEngineContentKind.BLOCK, "blocks");
                appendManagerMapContents(byId, engine, "furnitureManager", "loadedFurniture", HuntEngineContentKind.FURNITURE, "furniture");
                appendManagerMapContents(byId, engine, "fontManager", "loadedImages", HuntEngineContentKind.IMAGE, "images");
                appendManagerMapContents(byId, engine, "fontManager", "loadedBitmapImages", HuntEngineContentKind.IMAGE, "images");
                appendManagerCollectionContents(byId, engine, "fontManager", "fonts", HuntEngineContentKind.FONT, "fonts");
                appendManagerMapContents(byId, engine, "soundManager", "sounds", HuntEngineContentKind.SOUND, "sounds");
                appendRecipeContents(byId, engine, this.plugin.getClass().getClassLoader());
                final List<HuntEngineContent> contents = new ArrayList<>(byId.values());
                contents.sort(Comparator.comparing(HuntEngineContent::id));
                final String fingerprint = contents.stream()
                    .map(content -> content.kind().name() + ':' + content.id())
                    .reduce("", (left, right) -> left + '\n' + right);
                if (!this.catalogueFingerprint.getAndSet(fingerprint).equals(fingerprint)) {
                    this.revision.incrementAndGet();
                }
                return new HuntEngineCatalogue(this.revision.get(), contents);
            } catch (final ReflectiveOperationException | RuntimeException ex) {
                return HuntEngineCatalogue.unavailable();
            }
        }

        @Override
        public Collection<HuntEngineContentPackage> stagedContentPackages() {
            return this.stagedPackages.values().stream()
                .sorted(Comparator.comparing(HuntEngineContentPackage::fileName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        }

        @Override
        public HuntEngineActionResult stageContentPackage(final HuntEngineContentPackageUpload upload) {
            if (!this.workspaceAccess.tryAcquire()) {
                return HuntEngineActionResult.fail("HuntEngine workspace is busy with another operation.");
            }
            try {
                if (this.operationInProgress.get()) {
                    return HuntEngineActionResult.fail("HuntEngine workspace is busy with another operation.");
                }
                final String validationError = validateArchive(upload.fileName(), upload.contents());
                if (validationError != null) {
                    return HuntEngineActionResult.fail(validationError);
                }
                final String id = "huntercraft-" + UUID.randomUUID().toString().replace("-", "");
                final Path destination = this.stagingDirectory().resolve(id + ".zip");
                try {
                    Files.createDirectories(destination.getParent());
                    final Path temporary = Files.createTempFile(destination.getParent(), id, ".tmp");
                    try {
                        Files.write(temporary, upload.contents());
                        atomicMove(temporary, destination);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                    final HuntEngineContentPackage contentPackage = new HuntEngineContentPackage(
                        id, safeFileName(upload.fileName()), Files.size(destination), HuntEngineContentPackageState.VALID,
                        "Validated and staged for the next HuntEngine build."
                    );
                    this.stagedPackages.put(id, contentPackage);
                    this.writeStagedPackageMetadata(contentPackage);
                    return HuntEngineActionResult.ok("Content package staged as " + id + '.');
                } catch (final IOException ex) {
                    return HuntEngineActionResult.fail("Could not stage content package: " + conciseMessage(ex));
                }
            } finally {
                this.workspaceAccess.release();
            }
        }

        @Override
        public HuntEngineActionResult removeStagedContentPackage(final String packageId) {
            if (!this.workspaceAccess.tryAcquire()) {
                return HuntEngineActionResult.fail("HuntEngine workspace is busy with another operation.");
            }
            try {
                if (this.operationInProgress.get()) {
                    return HuntEngineActionResult.fail("HuntEngine workspace is busy with another operation.");
                }
                if (!packageId.matches("huntercraft-[a-f0-9]{32}")) {
                    return HuntEngineActionResult.fail("Invalid staged content package identifier.");
                }
                final PendingBuild pending = this.pendingBuild.get();
                if (pending != null && pending.packageIds().contains(packageId)) {
                    return HuntEngineActionResult.fail("This package belongs to a validated build waiting to be published or replaced.");
                }
                try {
                    Files.deleteIfExists(this.stagingDirectory().resolve(packageId + ".zip"));
                    Files.deleteIfExists(this.stagingDirectory().resolve(packageId + ".yml"));
                    this.stagedPackages.remove(packageId);
                    return HuntEngineActionResult.ok("Staged content package removed.");
                } catch (final IOException ex) {
                    return HuntEngineActionResult.fail("Could not remove staged content package: " + conciseMessage(ex));
                }
            } finally {
                this.workspaceAccess.release();
            }
        }

        @Override
        public HuntEngineActionResult give(final Player player, final String contentId, final int amount) {
            if (amount < 1 || amount > 2_304 || !stableIdentifier(contentId)) {
                return HuntEngineActionResult.fail("Invalid HuntEngine item request.");
            }
            if (!canGiveContent(player, contentId)) {
                return HuntEngineActionResult.fail("You do not have permission to receive this HuntEngine item.");
            }
            try {
                final Object itemManager = invoke(this.engine(), "itemManager");
                final Class<?> keyClass = Class.forName(KEY_CLASS, true, this.plugin.getClass().getClassLoader());
                final Object key = staticInvoke(keyClass, "of", contentId);
                final Object optional = invoke(itemManager, "getItemDefinition", key);
                if (!(optional instanceof Optional<?> definitionOptional) || definitionOptional.isEmpty()) {
                    return HuntEngineActionResult.fail("HuntEngine item does not exist: " + contentId);
                }
                final Object built = invoke(definitionOptional.get(), "buildBukkitItem");
                if (!(built instanceof ItemStack template) || template.getType() == Material.AIR) {
                    return HuntEngineActionResult.fail("HuntEngine did not build a Bukkit item for " + contentId + '.');
                }
                final List<ItemStack> stacks = stacks(template, amount);
                if (!hasRoom(player.getInventory(), stacks)) {
                    return HuntEngineActionResult.fail("Your inventory does not have enough room for this item.");
                }
                final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stacks.toArray(ItemStack[]::new));
                if (!leftovers.isEmpty()) {
                    return HuntEngineActionResult.fail("Inventory changed before HuntEngine could grant the item; no further items were granted.");
                }
                return HuntEngineActionResult.ok("Granted " + amount + " of " + contentId + '.');
            } catch (final ReflectiveOperationException | RuntimeException ex) {
                return HuntEngineActionResult.fail("Could not grant HuntEngine item: " + conciseMessage(ex));
            }
        }

        @Override
        public HuntEngineResourcePack resourcePack() {
            try {
                final Path config = this.plugin.getDataFolder().toPath().resolve("config.yml");
                if (!Files.isRegularFile(config)) {
                    return HuntEngineResourcePack.unavailable();
                }
                final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config.toFile());
                final Path resourcePackPath = this.generatedPackPath();
                final boolean published = Files.isRegularFile(resourcePackPath);
                final String revision = published ? digest(resourcePackPath, "SHA-256") : "";
                final String sha1 = published ? digest(resourcePackPath, "SHA-1") : "";
                final boolean uploaded = published && revision.equals(this.publishedPackRevision.get());
                final PendingBuild pending = this.pendingBuild.get();
                return new HuntEngineResourcePack(
                    true,
                    uploaded,
                    yaml.getBoolean("resource-pack.delivery.kick-if-declined", false),
                    "",
                    sha1,
                    revision,
                    uploaded
                        ? "Pack is published through the HuntEngine resource-pack host."
                        : pending != null
                            ? "A validated immutable HuntEngine pack is waiting for an explicit publish."
                        : published
                            ? "A generated pack is waiting for an explicit HuntEngine publish."
                            : "No generated HuntEngine resource pack is available yet."
                );
            } catch (final IOException | ReflectiveOperationException | RuntimeException ex) {
                return new HuntEngineResourcePack(true, false, false, "", "", "", "Could not read the generated resource pack.");
            }
        }

        @Override
        public HuntEngineActionResult requestResourcePack(final Player player) {
            try {
                final Object platformPlayer = staticInvoke(
                    Class.forName(ADAPTOR_CLASS, true, this.plugin.getClass().getClassLoader()), "adapt", player
                );
                final Object packManager = invoke(this.engine(), "packManager");
                invoke(packManager, "sendResourcePack", platformPlayer);
                this.resourcePackReady.put(player.getUniqueId(), false);
                return HuntEngineActionResult.ok("HuntEngine resource pack requested.");
            } catch (final ReflectiveOperationException | RuntimeException ex) {
                return HuntEngineActionResult.fail("Could not request the HuntEngine resource pack: " + conciseMessage(ex));
            }
        }

        @Override
        public boolean resourcePackReady(final UUID playerId) {
            return this.resourcePackReady.getOrDefault(playerId, false);
        }

        @Override
        public HuntEngineOperationTicket validate() {
            return this.start(HuntEngineOperationType.VALIDATE, this::validateStagedPackages);
        }

        @Override
        public HuntEngineOperationTicket build() {
            return this.start(HuntEngineOperationType.BUILD, this::buildCandidate);
        }

        @Override
        public HuntEngineOperationTicket publish() {
            return this.start(HuntEngineOperationType.PUBLISH, this::publishCandidate);
        }

        @Override
        public HuntEngineOperationTicket reload() {
            return this.start(HuntEngineOperationType.RELOAD, () -> {
                this.reloadEngine();
                this.revision.incrementAndGet();
                return HuntEngineActionResult.ok("HuntEngine reloaded successfully.");
            });
        }

        @Override
        public Optional<HuntEngineOperation> operation(final UUID operationId) {
            return Optional.ofNullable(this.operations.get(operationId));
        }

        @Override
        public HuntEngineMigrationReport migrationReport() {
            final Path journal = this.plugin.getDataFolder().toPath().getParent()
                .resolve("HunterCore/migrations/hunterassets-to-huntengine/journal.yml");
            if (!Files.isRegularFile(journal)) {
                return new HuntEngineMigrationReport(
                    HuntEngineMigrationState.NOT_REQUIRED, journal.toString(), null, null, List.of(), "No HunterAssets migration journal exists."
                );
            }
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(journal.toFile());
            final List<HuntEngineMigrationEntry> entries = new ArrayList<>();
            for (final Map<?, ?> entry : yaml.getMapList("entries")) {
                final String source = string(entry.get("source"), "unknown");
                entries.add(new HuntEngineMigrationEntry(
                    source,
                    nullableString(entry.get("target")),
                    nullableString(entry.get("sha256")),
                    migrationEntryState(string(entry.get("state"), "failed")),
                    string(entry.get("message"), "No migration detail available.")
                ));
            }
            final HuntEngineMigrationState state = migrationState(yaml.getString("state", "failed"));
            return new HuntEngineMigrationReport(
                state,
                journal.toString(),
                instant(yaml.getString("started-at", yaml.getString("updated-at", ""))),
                state == HuntEngineMigrationState.RUNNING ? null : instant(yaml.getString("updated-at", "")),
                entries,
                yaml.getString("message", "HunterAssets migration journal loaded.")
            );
        }

        private void registerResourcePackListener() {
            if (this.resourcePackListenerRegistered.compareAndSet(false, true)) {
                Bukkit.getPluginManager().registerEvents(new ResourcePackListener(), this.plugin);
            }
        }

        private HuntEngineOperationTicket start(final HuntEngineOperationType type, final Callable<HuntEngineActionResult> action) {
            final Instant startedAt = Instant.now();
            final UUID id = UUID.randomUUID();
            final HuntEngineOperation queued = new HuntEngineOperation(
                id, type, HuntEngineOperationState.QUEUED, this.revision.get(), "Operation queued.", startedAt, null
            );
            this.operations.put(id, queued);
            if (!this.operationInProgress.compareAndSet(false, true)) {
                final HuntEngineOperation failed = new HuntEngineOperation(
                    id, type, HuntEngineOperationState.FAILED, this.revision.get(), "Another HuntEngine operation is already running.", startedAt, Instant.now()
                );
                this.operations.put(id, failed);
                return new HuntEngineOperationTicket(failed, CompletableFuture.completedFuture(failed));
            }
            if (!this.workspaceAccess.tryAcquire()) {
                this.operationInProgress.set(false);
                final HuntEngineOperation failed = new HuntEngineOperation(
                    id, type, HuntEngineOperationState.FAILED, this.revision.get(),
                    "HuntEngine workspace is changing; retry the operation once that change has completed.", startedAt, Instant.now()
                );
                this.operations.put(id, failed);
                return new HuntEngineOperationTicket(failed, CompletableFuture.completedFuture(failed));
            }
            final HuntEngineOperation running = new HuntEngineOperation(
                id, type, HuntEngineOperationState.RUNNING, this.revision.get(), "Operation is running.", startedAt, null
            );
            this.operations.put(id, running);
            try {
                final CompletableFuture<HuntEngineOperation> completion = CompletableFuture.supplyAsync(() -> {
                    try {
                        final HuntEngineActionResult result = action.call();
                        final HuntEngineOperation completed = new HuntEngineOperation(
                            id,
                            type,
                            result.success() ? HuntEngineOperationState.SUCCEEDED : HuntEngineOperationState.FAILED,
                            this.revision.get(),
                            result.message(),
                            startedAt,
                            Instant.now()
                        );
                        this.operations.put(id, completed);
                        return completed;
                    } catch (final Exception ex) {
                        final HuntEngineOperation failed = new HuntEngineOperation(
                            id, type, HuntEngineOperationState.FAILED, this.revision.get(), conciseMessage(ex), startedAt, Instant.now()
                        );
                        this.operations.put(id, failed);
                        return failed;
                    } finally {
                        this.operationInProgress.set(false);
                        this.workspaceAccess.release();
                    }
                }, ForkJoinPool.commonPool());
                return new HuntEngineOperationTicket(queued, completion);
            } catch (final RuntimeException ex) {
                this.operationInProgress.set(false);
                this.workspaceAccess.release();
                final HuntEngineOperation failed = new HuntEngineOperation(
                    id, type, HuntEngineOperationState.FAILED, this.revision.get(),
                    "Could not schedule HuntEngine operation: " + conciseMessage(ex), startedAt, Instant.now()
                );
                this.operations.put(id, failed);
                return new HuntEngineOperationTicket(failed, CompletableFuture.completedFuture(failed));
            }
        }

        /**
         * Builds a candidate pack without leaving un-published content active in the engine.
         *
         * <p>HuntEngine generates from its live resources directory, so a candidate build has to
         * temporarily import staged packages and reload the plugin. The old resources and upload
         * ZIP are snapshotted first, then restored and reloaded before the candidate becomes
         * visible to management surfaces. The only persistent candidate artifacts are immutable
         * SHA-256-addressed files under {@code huntercore-publications}.</p>
         */
        private HuntEngineActionResult buildCandidate() {
            if (this.pendingBuild.get() != null) {
                return HuntEngineActionResult.fail("A validated HuntEngine build is already waiting to be published. Publish it or remove it before building again.");
            }
            final HuntEngineActionResult validation = this.validateStagedPackages();
            if (!validation.success()) {
                return validation;
            }
            final List<String> packageIds = this.stagedPackages.values().stream()
                .filter(contentPackage -> contentPackage.state() == HuntEngineContentPackageState.VALID)
                .map(HuntEngineContentPackage::id)
                .sorted()
                .toList();
            final BuildSnapshot snapshot;
            try {
                snapshot = this.snapshotActiveBuildState();
            } catch (final IOException | ReflectiveOperationException ex) {
                return HuntEngineActionResult.fail("Could not snapshot the active HuntEngine pack before building: " + conciseMessage(ex));
            }

            String candidateRevision = null;
            String candidateResourcesRevision = null;
            HuntEngineActionResult result;
            try {
                this.importStagedPackages(packageIds);
                this.reloadEngine();
                this.callOnPrimaryThread(() -> {
                    final Object packManager = invoke(this.engine(), "packManager");
                    invoke(packManager, "generateResourcePack");
                    return null;
                });
                final Path generated = this.generatedPackPath();
                verifyGeneratedPack(generated);
                candidateRevision = digest(generated, "SHA-256");
                final Path immutablePack = this.publicationPack(candidateRevision);
                copyImmutableFile(generated, immutablePack);
                candidateResourcesRevision = copyImmutableDirectory(this.resourcesDirectory(), this.publicationResources(candidateRevision));
                result = HuntEngineActionResult.ok("Validated HuntEngine resource-pack candidate " + candidateRevision + " was built.");
            } catch (final Exception ex) {
                result = HuntEngineActionResult.fail("Could not build the HuntEngine candidate: " + conciseMessage(ex));
            }

            try {
                this.restoreActiveBuildStateAfterReload(snapshot);
            } catch (final IOException | ReflectiveOperationException | InterruptedException | ExecutionException | TimeoutException ex) {
                result = HuntEngineActionResult.fail("HuntEngine candidate build rolled back incompletely; the active resources were restored but the engine reload needs operator attention: " + conciseMessage(ex));
            } finally {
                try {
                    deleteDirectoryIfExists(snapshot.transactionDirectory());
                } catch (final IOException ex) {
                    LOGGER.warn("Could not remove completed HuntEngine build transaction {}", snapshot.transactionDirectory(), ex);
                }
            }

            if (!result.success() || candidateRevision == null || candidateResourcesRevision == null) {
                return result;
            }
            final PendingBuild pending = new PendingBuild(candidateRevision, candidateResourcesRevision, packageIds, Instant.now());
            try {
                this.writePendingBuild(pending);
                this.pendingBuild.set(pending);
                this.updatePackageStates(packageIds, HuntEngineContentPackageState.BUILDING, "Included in immutable build " + candidateRevision + ".");
                this.revision.incrementAndGet();
                return HuntEngineActionResult.ok("HuntEngine candidate " + candidateRevision + " is ready. Publish explicitly to make it active.");
            } catch (final IOException ex) {
                this.pendingBuild.compareAndSet(pending, null);
                try {
                    Files.deleteIfExists(this.pendingBuildStateFile());
                } catch (final IOException cleanup) {
                    ex.addSuppressed(cleanup);
                }
                return HuntEngineActionResult.fail("Candidate was built but could not be recorded safely, so it was not made publishable: " + conciseMessage(ex));
            }
        }

        /** Publishes a previously verified immutable candidate and restores the old live state on failure. */
        private HuntEngineActionResult publishCandidate() {
            final PendingBuild pending = this.pendingBuild.get();
            if (pending == null) {
                return HuntEngineActionResult.fail("No validated HuntEngine build is waiting to be published.");
            }
            final Path candidatePack = this.publicationPack(pending.revision());
            final Path candidateResources = this.publicationResources(pending.revision());
            try {
                verifyGeneratedPack(candidatePack);
                if (!pending.revision().equals(digest(candidatePack, "SHA-256"))
                    || !pending.resourcesRevision().equals(digestDirectory(candidateResources))) {
                    return HuntEngineActionResult.fail("The pending HuntEngine candidate is incomplete or its immutable hash no longer matches.");
                }
                this.ensurePublishStateWritable();
            } catch (final IOException ex) {
                return HuntEngineActionResult.fail("Could not verify the pending HuntEngine candidate: " + conciseMessage(ex));
            }

            final BuildSnapshot snapshot;
            try {
                snapshot = this.snapshotActiveBuildState();
            } catch (final IOException | ReflectiveOperationException ex) {
                return HuntEngineActionResult.fail("Could not snapshot the current HuntEngine pack before publishing: " + conciseMessage(ex));
            }
            final String previousRevision = this.publishedPackRevision.get();
            boolean uploadStarted = false;
            String hostRollbackFailure = null;
            HuntEngineActionResult result;
            try {
                this.installCandidate(candidateResources, candidatePack, pending.revision(), pending.resourcesRevision());
                this.reloadEngine();
                final Path activePack = this.generatedPackPath();
                verifyGeneratedPack(activePack);
                if (!pending.revision().equals(digest(activePack, "SHA-256"))) {
                    throw new IOException("active pack hash does not match the immutable candidate");
                }
                final CompletableFuture<?> uploadFuture = this.startUpload(candidatePack);
                uploadStarted = true;
                uploadFuture.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                this.clearPendingBuild(pending);
                try {
                    this.recordPublishedPackRevision(pending.revision());
                } catch (final IOException recordFailure) {
                    // The remote upload succeeded, but without an atomically persisted local
                    // state it must still be treated as a failed publish and rolled back.
                    this.pendingBuild.set(pending);
                    this.writePendingBuild(pending);
                    throw recordFailure;
                }
                try {
                    this.updatePackageStates(pending.packageIds(), HuntEngineContentPackageState.PUBLISHED, "Published in immutable build " + pending.revision() + ".");
                } catch (final IOException metadataFailure) {
                    // Publication has already succeeded. Keep the engine and host active, and
                    // retain the package metadata for an administrator to inspect/reconcile.
                    LOGGER.warn("HuntEngine pack {} was published but staged-package metadata could not be updated.", pending.revision(), metadataFailure);
                }
                this.revision.incrementAndGet();
                result = HuntEngineActionResult.ok("HuntEngine resource pack " + pending.revision() + " was uploaded and activated successfully.");
            } catch (final Exception ex) {
                result = HuntEngineActionResult.fail("HuntEngine publish failed; restoring the previously active pack: " + conciseMessage(ex));
                if (uploadStarted) {
                    hostRollbackFailure = this.restorePreviouslyPublishedHost(previousRevision);
                }
                try {
                    this.restoreActiveBuildStateAfterReload(snapshot);
                } catch (final IOException | ReflectiveOperationException | InterruptedException | ExecutionException | TimeoutException restoreFailure) {
                    result = HuntEngineActionResult.fail(result.message() + " Local rollback also requires operator attention: " + conciseMessage(restoreFailure));
                }
                if (hostRollbackFailure != null) {
                    result = HuntEngineActionResult.fail(result.message() + " Remote host rollback also requires operator attention: " + hostRollbackFailure);
                }
            } finally {
                try {
                    deleteDirectoryIfExists(snapshot.transactionDirectory());
                } catch (final IOException ex) {
                    LOGGER.warn("Could not remove completed HuntEngine publish transaction {}", snapshot.transactionDirectory(), ex);
                }
            }
            return result;
        }

        private HuntEngineActionResult validateStagedPackages() {
            for (final HuntEngineContentPackage contentPackage : this.stagedPackages.values()) {
                if (contentPackage.state() == HuntEngineContentPackageState.PUBLISHED || contentPackage.state() == HuntEngineContentPackageState.BUILDING) {
                    continue;
                }
                final Path archive = this.stagingDirectory().resolve(contentPackage.id() + ".zip");
                try {
                    final String error = validateArchive(contentPackage.fileName(), Files.readAllBytes(archive));
                    if (error != null) {
                        final HuntEngineContentPackage invalid = new HuntEngineContentPackage(
                            contentPackage.id(), contentPackage.fileName(), contentPackage.size(), HuntEngineContentPackageState.INVALID, error
                        );
                        this.stagedPackages.put(invalid.id(), invalid);
                        this.writeStagedPackageMetadata(invalid);
                        return HuntEngineActionResult.fail(error);
                    }
                    if (contentPackage.state() != HuntEngineContentPackageState.VALID) {
                        final HuntEngineContentPackage valid = new HuntEngineContentPackage(
                            contentPackage.id(), contentPackage.fileName(), contentPackage.size(), HuntEngineContentPackageState.VALID,
                            "Validated and staged for the next HuntEngine build."
                        );
                        this.stagedPackages.put(valid.id(), valid);
                        this.writeStagedPackageMetadata(valid);
                    }
                } catch (final IOException ex) {
                    return HuntEngineActionResult.fail("Could not validate staged package " + contentPackage.fileName() + ": " + conciseMessage(ex));
                }
            }
            return HuntEngineActionResult.ok("All staged HuntEngine content packages are valid.");
        }

        private void importStagedPackages(final Collection<String> packageIds) throws IOException {
            final Path resources = this.resourcesDirectory();
            Files.createDirectories(resources);
            for (final String packageId : packageIds) {
                final HuntEngineContentPackage contentPackage = this.stagedPackages.get(packageId);
                if (contentPackage == null || contentPackage.state() != HuntEngineContentPackageState.VALID) {
                    throw new IOException("Staged package is not valid: " + packageId);
                }
                final Path destination = resources.resolve(contentPackage.id());
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) {
                        throw new IOException("existing HuntEngine resource package is not a safe directory: " + destination);
                    }
                    continue;
                }
                final Path temporary = resources.resolve('.' + contentPackage.id() + ".importing");
                deleteDirectoryIfExists(temporary);
                try {
                    extractArchive(this.stagingDirectory().resolve(contentPackage.id() + ".zip"), temporary);
                    atomicMove(temporary, destination);
                } finally {
                    deleteDirectoryIfExists(temporary);
                }
            }
        }

        private void reloadEngine() throws ReflectiveOperationException, InterruptedException, ExecutionException, TimeoutException {
            final Object engine = this.engine();
            final Executor syncExecutor = runnable -> Bukkit.getScheduler().runTask(this.plugin, runnable);
            final Object future = invoke(engine, "reloadPlugin", ForkJoinPool.commonPool(), syncExecutor, true);
            if (!(future instanceof CompletableFuture<?> reloadFuture)) {
                throw new IllegalStateException("HuntEngine reload did not return a CompletableFuture.");
            }
            final Object result = reloadFuture.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!booleanMethod(result, "success")) {
                throw new IllegalStateException("HuntEngine reported a failed reload.");
            }
        }

        private BuildSnapshot snapshotActiveBuildState() throws IOException, ReflectiveOperationException {
            final Path transaction = this.transactionsDirectory().resolve(UUID.randomUUID().toString());
            final Path resources = this.resourcesDirectory();
            final Path generatedPack = this.generatedPackPath();
            final boolean resourcesExisted = Files.exists(resources, LinkOption.NOFOLLOW_LINKS);
            if (resourcesExisted && (!Files.isDirectory(resources, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(resources))) {
                throw new IOException("active HuntEngine resources path is not a safe directory: " + resources);
            }
            final boolean generatedPackExisted = Files.exists(generatedPack, LinkOption.NOFOLLOW_LINKS);
            if (generatedPackExisted && (!Files.isRegularFile(generatedPack, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(generatedPack))) {
                throw new IOException("active HuntEngine resource-pack path is not a safe file: " + generatedPack);
            }
            Files.createDirectories(transaction);
            final Path resourcesBackup = transaction.resolve("resources");
            final Path packBackup = transaction.resolve("resource-pack.zip");
            try {
                if (resourcesExisted) {
                    copyDirectory(resources, resourcesBackup);
                }
                if (generatedPackExisted) {
                    copyFileAtomically(generatedPack, packBackup);
                }
                return new BuildSnapshot(
                    transaction,
                    resources,
                    resourcesBackup,
                    resourcesExisted,
                    generatedPack,
                    packBackup,
                    generatedPackExisted
                );
            } catch (final IOException ex) {
                try {
                    deleteDirectoryIfExists(transaction);
                } catch (final IOException cleanup) {
                    ex.addSuppressed(cleanup);
                }
                throw ex;
            }
        }

        private void restoreActiveBuildState(final BuildSnapshot snapshot) throws IOException {
            if (snapshot.resourcesExisted()) {
                replaceDirectory(snapshot.resourcesBackup(), snapshot.resourcesDirectory());
            } else {
                deleteDirectoryIfExists(snapshot.resourcesDirectory());
            }
            this.restoreGeneratedPack(snapshot);
        }

        /**
         * Restores the filesystem snapshot, reloads the original resources, and then reapplies
         * the original ZIP. Some HuntEngine reload paths regenerate a ZIP as a side effect; the
         * final copy preserves the exact pre-transaction live pack on both success and failure.
         */
        private void restoreActiveBuildStateAfterReload(final BuildSnapshot snapshot)
            throws IOException, ReflectiveOperationException, InterruptedException, ExecutionException, TimeoutException {
            this.restoreActiveBuildState(snapshot);
            this.reloadEngine();
            this.restoreGeneratedPack(snapshot);
        }

        private void restoreGeneratedPack(final BuildSnapshot snapshot) throws IOException {
            if (snapshot.generatedPackExisted()) {
                copyFileAtomically(snapshot.generatedPackBackup(), snapshot.generatedPack());
            } else {
                Files.deleteIfExists(snapshot.generatedPack());
            }
        }

        private void installCandidate(
            final Path candidateResources,
            final Path candidatePack,
            final String candidateRevision,
            final String resourcesRevision
        )
            throws IOException, ReflectiveOperationException {
            replaceDirectory(candidateResources, this.resourcesDirectory(), resourcesRevision);
            copyFileAtomically(candidatePack, this.generatedPackPath(), candidateRevision);
        }

        private CompletableFuture<?> startUpload(final Path immutablePack) throws Exception {
            return this.callOnPrimaryThread(() -> {
                final Object packManager = invoke(this.engine(), "packManager");
                final Object host = invoke(packManager, "resourcePackHost");
                if (!booleanMethod(host, "canUpload")) {
                    throw new IllegalStateException("The configured HuntEngine resource-pack host cannot upload packs.");
                }
                final Object upload = invoke(host, "upload", immutablePack);
                if (!(upload instanceof CompletableFuture<?> uploadFuture)) {
                    throw new IllegalStateException("HuntEngine resource-pack host did not return an upload future.");
                }
                return uploadFuture;
            });
        }

        /** Attempts to restore the host's former immutable pack after an ambiguous upload failure. */
        private String restorePreviouslyPublishedHost(final String previousRevision) {
            if (previousRevision == null || !previousRevision.matches("[a-f0-9]{64}")) {
                return "no previously published immutable pack is available for host rollback";
            }
            final Path previous = this.publicationPack(previousRevision);
            if (!Files.isRegularFile(previous, LinkOption.NOFOLLOW_LINKS)) {
                LOGGER.error("HuntEngine upload failed after starting, but previous immutable pack {} is unavailable for host rollback.", previousRevision);
                return "previous immutable pack " + previousRevision + " is unavailable";
            }
            try {
                verifyGeneratedPack(previous);
                if (!previousRevision.equals(digest(previous, "SHA-256"))) {
                    throw new IOException("previous immutable pack hash does not match its revision");
                }
                this.startUpload(previous).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return null;
            } catch (final Exception ex) {
                LOGGER.error("HuntEngine upload failed and the host rollback to {} also failed.", previousRevision, ex);
                return conciseMessage(ex);
            }
        }

        private Path dataDirectory() {
            return this.plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        }

        private Path resourcesDirectory() throws IOException {
            return this.managedPath(this.dataDirectory().resolve("resources"));
        }

        private Path generatedPackPath() throws ReflectiveOperationException, IOException {
            final Class<?> config = Class.forName(CONFIG_CLASS, true, this.plugin.getClass().getClassLoader());
            final Object configured = staticInvoke(config, "fileToUpload");
            if (!(configured instanceof Path path)) {
                throw new IOException("HuntEngine did not expose a resource-pack upload path.");
            }
            return this.managedPath(path);
        }

        private Path managedPath(final Path path) throws IOException {
            final Path dataDirectory = this.dataDirectory();
            final Path normalized = path.toAbsolutePath().normalize();
            if (!normalized.startsWith(dataDirectory)) {
                throw new IOException("HunterCore only manages HuntEngine build files inside " + dataDirectory + '.');
            }
            return normalized;
        }

        private Path transactionsDirectory() {
            return this.dataDirectory().resolve("huntercore-transactions");
        }

        private Path publicationsDirectory() {
            return this.dataDirectory().resolve("huntercore-publications");
        }

        private Path publicationPack(final String revision) {
            if (!validSha256(revision)) {
                throw new IllegalArgumentException("invalid HuntEngine publication revision");
            }
            return this.publicationsDirectory().resolve(revision + ".zip").normalize();
        }

        private Path publicationResources(final String revision) {
            if (!validSha256(revision)) {
                throw new IllegalArgumentException("invalid HuntEngine publication revision");
            }
            return this.publicationsDirectory().resolve(revision + ".resources").normalize();
        }

        private Path pendingBuildStateFile() {
            return this.dataDirectory().resolve("huntercore-pending-build.yml");
        }

        private void loadPendingBuild() {
            final Path stateFile = this.pendingBuildStateFile();
            if (!Files.isRegularFile(stateFile, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            final YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile.toFile());
            final String revision = state.getString("revision", "").trim();
            final String resourcesRevision = state.getString("resources-sha256", "").trim();
            try {
                if (!validSha256(revision)
                    || !validSha256(resourcesRevision)
                    || !Files.isRegularFile(this.publicationPack(revision), LinkOption.NOFOLLOW_LINKS)
                    || !revision.equals(digest(this.publicationPack(revision), "SHA-256"))
                    || !resourcesRevision.equals(digestDirectory(this.publicationResources(revision)))) {
                    LOGGER.warn("Ignoring incomplete or modified HuntEngine pending-build state at {}", stateFile);
                    return;
                }
                final List<String> packageIds = state.getStringList("package-ids").stream()
                    .filter(id -> id.matches("huntercraft-[a-f0-9]{32}"))
                    .sorted()
                    .toList();
                this.pendingBuild.set(new PendingBuild(revision, resourcesRevision, packageIds, instant(state.getString("built-at", ""))));
            } catch (final IOException ex) {
                LOGGER.warn("Ignoring unreadable HuntEngine pending-build state at {}", stateFile, ex);
            }
        }

        private void writePendingBuild(final PendingBuild pending) throws IOException {
            final Path stateFile = this.pendingBuildStateFile();
            Files.createDirectories(stateFile.getParent());
            final YamlConfiguration state = new YamlConfiguration();
            state.set("revision", pending.revision());
            state.set("pack-file", pending.revision() + ".zip");
            state.set("resources-directory", pending.revision() + ".resources");
            state.set("resources-sha256", pending.resourcesRevision());
            state.set("package-ids", pending.packageIds());
            state.set("built-at", pending.builtAt().toString());
            final Path temporary = Files.createTempFile(stateFile.getParent(), "huntercore-pending", ".tmp");
            try {
                state.save(temporary.toFile());
                atomicMove(temporary, stateFile);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        private void clearPendingBuild(final PendingBuild pending) throws IOException {
            if (!this.pendingBuild.compareAndSet(pending, null)) {
                throw new IOException("HuntEngine pending build changed while publishing.");
            }
            try {
                Files.deleteIfExists(this.pendingBuildStateFile());
            } catch (final IOException ex) {
                this.pendingBuild.compareAndSet(null, pending);
                throw ex;
            }
        }

        private void ensurePublishStateWritable() throws IOException {
            final Path stateFile = this.publishedPackStateFile();
            Files.createDirectories(stateFile.getParent());
            final Path probe = Files.createTempFile(stateFile.getParent(), "huntercore-publish-probe", ".tmp");
            Files.deleteIfExists(probe);
        }

        private void updatePackageStates(
            final Collection<String> packageIds,
            final HuntEngineContentPackageState state,
            final String message
        ) throws IOException {
            for (final String packageId : packageIds) {
                final HuntEngineContentPackage existing = this.stagedPackages.get(packageId);
                if (existing == null) {
                    continue;
                }
                final HuntEngineContentPackage updated = new HuntEngineContentPackage(
                    existing.id(), existing.fileName(), existing.size(), state, message
                );
                this.stagedPackages.put(updated.id(), updated);
                this.writeStagedPackageMetadata(updated);
            }
        }

        private record BuildSnapshot(
            Path transactionDirectory,
            Path resourcesDirectory,
            Path resourcesBackup,
            boolean resourcesExisted,
            Path generatedPack,
            Path generatedPackBackup,
            boolean generatedPackExisted
        ) {
        }

        private record PendingBuild(String revision, String resourcesRevision, List<String> packageIds, Instant builtAt) {
            private PendingBuild {
                if (!validSha256(revision) || !validSha256(resourcesRevision)) {
                    throw new IllegalArgumentException("HuntEngine pending builds require SHA-256 revisions.");
                }
                packageIds = List.copyOf(packageIds);
                builtAt = builtAt == null ? Instant.now() : builtAt;
            }
        }

        private <T> T callOnPrimaryThread(final Callable<T> callable) throws Exception {
            if (Bukkit.isPrimaryThread()) {
                return callable.call();
            }
            final CompletableFuture<T> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                try {
                    future.complete(callable.call());
                } catch (final Exception ex) {
                    future.completeExceptionally(ex);
                }
            });
            try {
                return future.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw new CompletionException(cause);
            }
        }

        private Object engine() throws ReflectiveOperationException {
            final Class<?> type = Class.forName(ENGINE_CLASS, true, this.plugin.getClass().getClassLoader());
            final Object engine = staticInvoke(type, "instance");
            if (engine == null) {
                throw new IllegalStateException("HuntEngine has not initialized its Bukkit engine yet.");
            }
            return engine;
        }

        private Path stagingDirectory() {
            return this.dataDirectory().resolve("huntercore-staging");
        }

        private Path publishedPackStateFile() {
            return this.dataDirectory().resolve("huntercore-publish-state.yml");
        }

        private void loadPublishedPackRevision() {
            final Path stateFile = this.publishedPackStateFile();
            if (!Files.isRegularFile(stateFile, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            final String revision = YamlConfiguration.loadConfiguration(stateFile.toFile()).getString("revision", "").trim();
            if (!validSha256(revision)) {
                LOGGER.warn("Ignoring invalid HuntEngine published-pack state at {}", stateFile);
                return;
            }
            try {
                final Path immutablePack = this.publicationPack(revision);
                if (Files.isRegularFile(immutablePack, LinkOption.NOFOLLOW_LINKS)
                    && revision.equals(digest(immutablePack, "SHA-256"))) {
                    this.publishedPackRevision.set(revision);
                } else {
                    LOGGER.warn("Ignoring incomplete or modified HuntEngine published-pack state at {}", stateFile);
                }
            } catch (final IOException ex) {
                LOGGER.warn("Ignoring unreadable HuntEngine published-pack state at {}", stateFile, ex);
            }
        }

        private void recordPublishedPackRevision(final String revision) throws IOException {
            if (!validSha256(revision)) {
                throw new IOException("invalid immutable HuntEngine publication revision");
            }
            final Path immutablePack = this.publicationPack(revision);
            if (!Files.isRegularFile(immutablePack, LinkOption.NOFOLLOW_LINKS)
                || !revision.equals(digest(immutablePack, "SHA-256"))) {
                throw new IOException("immutable HuntEngine publication pack is missing or modified");
            }
            final Path stateFile = this.publishedPackStateFile();
            Files.createDirectories(stateFile.getParent());
            final YamlConfiguration state = new YamlConfiguration();
            state.set("revision", revision);
            state.set("published-at", Instant.now().toString());
            final Path temporary = Files.createTempFile(stateFile.getParent(), "huntercore-publish", ".tmp");
            try {
                state.save(temporary.toFile());
                atomicMove(temporary, stateFile);
                this.publishedPackRevision.set(revision);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        private void loadStagedPackages() {
            final Path directory = this.stagingDirectory();
            if (!Files.isDirectory(directory)) {
                return;
            }
            try (var paths = Files.list(directory)) {
                for (final Path metadata : paths.filter(path -> path.getFileName().toString().endsWith(".yml")).toList()) {
                    final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(metadata.toFile());
                    final String id = yaml.getString("id", "");
                    final Path archive = directory.resolve(id + ".zip");
                    if (!id.matches("huntercraft-[a-f0-9]{32}") || !Files.isRegularFile(archive)) {
                        continue;
                    }
                    this.stagedPackages.put(id, new HuntEngineContentPackage(
                        id,
                        yaml.getString("file-name", id + ".zip"),
                        Files.size(archive),
                        contentPackageState(yaml.getString("state", "staged")),
                        yaml.getString("message", "Staged content package recovered from disk.")
                    ));
                }
            } catch (final IOException ignored) {
                // The staging workspace is optional; a corrupt metadata file must not disable the engine.
            }
        }

        private void writeStagedPackageMetadata(final HuntEngineContentPackage contentPackage) throws IOException {
            final Path directory = this.stagingDirectory();
            Files.createDirectories(directory);
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("id", contentPackage.id());
            yaml.set("file-name", contentPackage.fileName());
            yaml.set("size", contentPackage.size());
            yaml.set("state", contentPackage.state().name().toLowerCase(Locale.ROOT));
            yaml.set("message", contentPackage.message());
            final Path target = directory.resolve(contentPackage.id() + ".yml");
            final Path temporary = Files.createTempFile(directory, contentPackage.id(), ".tmp");
            try {
                yaml.save(temporary.toFile());
                atomicMove(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        private static HuntEngineContentPackageState contentPackageState(final String raw) {
            try {
                return HuntEngineContentPackageState.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException ex) {
                return HuntEngineContentPackageState.STAGED;
            }
        }

        private final class ResourcePackListener implements Listener {
            @EventHandler
            public void onResourcePackStatus(final PlayerResourcePackStatusEvent event) {
                final boolean ready = event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
                resourcePackReady.put(event.getPlayer().getUniqueId(), ready);
            }
        }
    }

    private static void appendManagerMapContents(
        final Map<String, HuntEngineContent> contents,
        final Object engine,
        final String managerMethod,
        final String contentsMethod,
        final HuntEngineContentKind kind,
        final String category
    ) {
        try {
            final Object manager = invoke(engine, managerMethod);
            final Object loaded = invoke(manager, contentsMethod);
            if (loaded instanceof Map<?, ?> entries) {
                for (final Map.Entry<?, ?> entry : entries.entrySet()) {
                    addCatalogueContent(contents, String.valueOf(entry.getKey()), kind, category);
                }
            }
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            // A content manager can be disabled by an upstream configuration. Its absence must
            // not make the entire HuntEngine catalogue unavailable.
        }
    }

    private static void appendManagerCollectionContents(
        final Map<String, HuntEngineContent> contents,
        final Object engine,
        final String managerMethod,
        final String contentsMethod,
        final HuntEngineContentKind kind,
        final String category
    ) {
        try {
            final Object manager = invoke(engine, managerMethod);
            final Object loaded = invoke(manager, contentsMethod);
            if (loaded instanceof Collection<?> entries) {
                for (final Object entry : entries) {
                    addCatalogueContent(contents, reflectedContentId(entry), kind, category);
                }
            }
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            // See appendManagerMapContents: display what this Community Edition runtime exposes.
        }
    }

    private static void appendRecipeContents(
        final Map<String, HuntEngineContent> contents,
        final Object engine,
        final ClassLoader engineClassLoader
    ) {
        try {
            final Object manager = invoke(engine, "recipeManager");
            final Class<?> recipeType = Class.forName(
                "net.momirealms.craftengine.core.item.recipe.RecipeType", true, engineClassLoader
            );
            final Object[] types = recipeType.getEnumConstants();
            if (types == null) {
                return;
            }
            for (final Object type : types) {
                final Object loaded = invoke(manager, "recipesByType", type);
                if (loaded instanceof Collection<?> recipes) {
                    for (final Object recipe : recipes) {
                        addCatalogueContent(contents, reflectedContentId(recipe), HuntEngineContentKind.RECIPE, "recipes");
                    }
                }
            }
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            // Recipes stay represented by their native package if an upstream runtime omits the
            // recipe manager. Import/validate/build/publish still remains available.
        }
    }

    private static String reflectedContentId(final Object content) {
        if (content == null) {
            return "";
        }
        for (final String method : List.of("id", "key")) {
            try {
                final Object id = invoke(content, method);
                if (id != null) {
                    return String.valueOf(id);
                }
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                // Continue through the stable Community Edition descriptor shapes.
            }
        }
        return "";
    }

    private static void addCatalogueContent(
        final Map<String, HuntEngineContent> contents,
        final String rawId,
        final HuntEngineContentKind kind,
        final String category
    ) {
        final String id = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        if (!stableIdentifier(id) || !stableIdentifier(category)) {
            return;
        }
        final HuntEngineContent existing = contents.get(id);
        if (existing != null && existing.kind() != HuntEngineContentKind.ITEM) {
            return;
        }
        contents.put(id, new HuntEngineContent(
            id,
            kind,
            id,
            "HuntEngine " + kind.name().toLowerCase(Locale.ROOT),
            List.of(category),
            null,
            true
        ));
    }

    private static HuntEngineContentKind contentKind(final Object definition) {
        try {
            final Object behavior = invoke(definition, "behavior");
            final String type = behavior == null ? "" : behavior.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (type.contains("furniture")) {
                return HuntEngineContentKind.FURNITURE;
            }
            if (type.contains("block")) {
                return HuntEngineContentKind.BLOCK;
            }
        } catch (final ReflectiveOperationException ignored) {
            // Item is the safe default for unknown upstream behavior types.
        }
        return HuntEngineContentKind.ITEM;
    }

    private static List<ItemStack> stacks(final ItemStack template, final int amount) {
        final List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        final int max = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            final ItemStack stack = template.clone();
            final int count = Math.min(max, remaining);
            stack.setAmount(count);
            stacks.add(stack);
            remaining -= count;
        }
        return stacks;
    }

    /**
     * Preserves the former HunterAssets grants for the 2.9.x migration window.
     *
     * <p>The new plural {@code huntengine.items.give} node is the normal management capability;
     * per-item grants remain available for player-facing catalogues. An explicit new permission
     * policy can still be enforced by removing the corresponding legacy nodes before 2.10.0.</p>
     */
    private static boolean canGiveContent(final Player player, final String contentId) {
        return player.hasPermission("huntengine.admin")
            || player.hasPermission("huntengine.items.give")
            || player.hasPermission("huntengine.item.*")
            || player.hasPermission("huntengine.item." + contentId)
            || player.hasPermission("hunterassets.admin")
            || player.hasPermission("hunterassets.give")
            || player.hasPermission("hunterassets.item.*")
            || player.hasPermission("hunterassets.item." + contentId);
    }

    private static boolean hasRoom(final Inventory inventory, final List<ItemStack> stacks) {
        final ItemStack[] contents = inventory.getStorageContents();
        final ItemStack[] simulated = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            simulated[index] = contents[index] == null ? null : contents[index].clone();
        }
        for (final ItemStack requested : stacks) {
            int remaining = requested.getAmount();
            for (final ItemStack existing : simulated) {
                if (existing != null && existing.getType() != Material.AIR && existing.isSimilar(requested)) {
                    final int added = Math.min(remaining, Math.max(0, existing.getMaxStackSize() - existing.getAmount()));
                    existing.setAmount(existing.getAmount() + added);
                    remaining -= added;
                }
            }
            if (remaining <= 0) {
                continue;
            }
            for (int index = 0; index < simulated.length && remaining > 0; index++) {
                if (simulated[index] == null || simulated[index].getType() == Material.AIR) {
                    final ItemStack placed = requested.clone();
                    final int added = Math.min(remaining, requested.getMaxStackSize());
                    placed.setAmount(added);
                    simulated[index] = placed;
                    remaining -= added;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static String validateArchive(final String fileName, final byte[] contents) {
        final String normalizedName = safeFileName(fileName);
        if (!normalizedName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return "HuntEngine content packages must be ZIP files.";
        }
        if (contents.length == 0 || contents.length > ReflectiveHuntEngineService.MAX_UPLOAD_BYTES) {
            return "HuntEngine content package exceeds the upload size limit.";
        }
        boolean packMetadata = false;
        long uncompressed = 0L;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(contents))) {
            ZipEntry entry;
            final byte[] buffer = new byte[8_192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > ReflectiveHuntEngineService.MAX_ARCHIVE_ENTRIES) {
                    return "HuntEngine content package has too many files.";
                }
                final String name = entry.getName().replace('\\', '/');
                if (!safeArchiveEntry(name)) {
                    return "HuntEngine content package contains an unsafe ZIP path.";
                }
                if (entry.getCompressedSize() > 0L && entry.getSize() > 0L
                    && entry.getSize() / entry.getCompressedSize() > 100L) {
                    return "HuntEngine content package exceeds the compression-ratio limit.";
                }
                if ("pack.yml".equals(name)) {
                    packMetadata = true;
                }
                if (!entry.isDirectory()) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        uncompressed += read;
                        if (uncompressed > ReflectiveHuntEngineService.MAX_UNCOMPRESSED_BYTES) {
                            return "HuntEngine content package exceeds the uncompressed size limit.";
                        }
                    }
                }
                zip.closeEntry();
            }
        } catch (final IOException ex) {
            return "HuntEngine content package is not a valid ZIP archive.";
        }
        return packMetadata ? null : "HuntEngine content package must contain a root pack.yml file.";
    }

    private static void extractArchive(final Path archive, final Path destination) throws IOException {
        Files.createDirectories(destination);
        long uncompressed = 0L;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            final byte[] buffer = new byte[8_192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                final String name = entry.getName().replace('\\', '/');
                if (entries > ReflectiveHuntEngineService.MAX_ARCHIVE_ENTRIES || !safeArchiveEntry(name)) {
                    throw new IOException("unsafe HuntEngine content package entry: " + name);
                }
                final Path target = destination.resolve(name).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("unsafe HuntEngine content package target: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (var output = Files.newOutputStream(target)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        uncompressed += read;
                        if (uncompressed > ReflectiveHuntEngineService.MAX_UNCOMPRESSED_BYTES) {
                            throw new IOException("HuntEngine content package is too large when extracted.");
                        }
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static boolean safeArchiveEntry(final String entry) {
        return !entry.isBlank() && !entry.startsWith("/") && !entry.startsWith("../") && !entry.contains("/../")
            && !entry.contains("\\") && !entry.matches("^[A-Za-z]:.*");
    }

    /** Ensures the engine-generated candidate is a non-empty Minecraft resource-pack archive. */
    private static void verifyGeneratedPack(final Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) == 0L) {
            throw new IOException("generated resource-pack ZIP is missing or empty: " + path);
        }
        boolean packMetadata = false;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final String name = entry.getName().replace('\\', '/');
                if (!safeArchiveEntry(name)) {
                    throw new IOException("generated resource-pack ZIP contains an unsafe entry: " + name);
                }
                if ("pack.mcmeta".equals(name)) {
                    packMetadata = true;
                }
                zip.closeEntry();
            }
        }
        if (!packMetadata) {
            throw new IOException("generated resource-pack ZIP does not contain pack.mcmeta");
        }
    }

    /**
     * Copies a candidate file into its SHA-addressed publication path without ever replacing an
     * existing candidate. A concurrent/corrupt collision is rejected and the original remains
     * readable for diagnostics.
     */
    static void copyImmutableFile(final Path source, final Path destination) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw new IOException("immutable HuntEngine publication source is not a safe file: " + source);
        }
        final String sourceRevision = digest(source, "SHA-256");
        Files.createDirectories(destination.getParent());
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            verifyImmutableFile(destination, sourceRevision);
            return;
        }
        final Path temporary = Files.createTempFile(destination.getParent(), ".huntercore-immutable-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            if (!sourceRevision.equals(digest(temporary, "SHA-256"))) {
                throw new IOException("immutable HuntEngine publication source changed while being copied");
            }
            try {
                moveNoReplace(temporary, destination);
            } catch (final FileAlreadyExistsException collision) {
                verifyImmutableFile(destination, sourceRevision);
            }
            verifyImmutableFile(destination, sourceRevision);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Copies a candidate resources tree into its publication path and returns its content hash.
     * The tree hash is persisted with the pending build, preventing a pack ZIP from being paired
     * with a modified or unrelated resources directory at publish time.
     */
    static String copyImmutableDirectory(final Path source, final Path destination) throws IOException {
        final String sourceRevision = digestDirectory(source);
        Files.createDirectories(destination.getParent());
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            verifyImmutableDirectory(destination, sourceRevision);
            return sourceRevision;
        }
        final Path temporary = destination.resolveSibling('.' + destination.getFileName().toString() + ".creating-" + UUID.randomUUID());
        try {
            copyDirectory(source, temporary);
            if (!sourceRevision.equals(digestDirectory(temporary))) {
                throw new IOException("immutable HuntEngine resources changed while being copied");
            }
            try {
                moveNoReplace(temporary, destination);
            } catch (final FileAlreadyExistsException collision) {
                verifyImmutableDirectory(destination, sourceRevision);
            }
            verifyImmutableDirectory(destination, sourceRevision);
            return sourceRevision;
        } finally {
            deleteDirectoryIfExists(temporary);
        }
    }

    private static void verifyImmutableFile(final Path path, final String expectedRevision) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(path)
            || !expectedRevision.equals(digest(path, "SHA-256"))) {
            throw new IOException("immutable HuntEngine publication collision at " + path);
        }
    }

    private static void verifyImmutableDirectory(final Path path, final String expectedRevision) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(path)
            || !expectedRevision.equals(digestDirectory(path))) {
            throw new IOException("immutable HuntEngine publication collision at " + path);
        }
    }

    private static void copyFileAtomically(final Path source, final Path destination) throws IOException {
        copyFileAtomically(source, destination, null);
    }

    private static void copyFileAtomically(final Path source, final Path destination, final String expectedRevision) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw new IOException("HuntEngine copy source is not a safe file: " + source);
        }
        if (expectedRevision != null && (!validSha256(expectedRevision) || !expectedRevision.equals(digest(source, "SHA-256")))) {
            throw new IOException("HuntEngine copy source no longer matches its expected SHA-256 revision");
        }
        Files.createDirectories(destination.getParent());
        final Path temporary = Files.createTempFile(destination.getParent(), ".huntercore-copy-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            if (expectedRevision != null && !expectedRevision.equals(digest(temporary, "SHA-256"))) {
                throw new IOException("HuntEngine copy source changed while being copied");
            }
            atomicMove(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void copyDirectory(final Path source, final Path destination) throws IOException {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw new IOException("directory snapshot source is unavailable: " + source);
        }
        deleteDirectoryIfExists(destination);
        try (var paths = Files.walk(source)) {
            for (final Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("symbolic links are not allowed in managed HuntEngine resources: " + path);
                }
                final Path relative = source.relativize(path);
                final Path target = destination.resolve(relative.toString()).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("unsafe managed HuntEngine resource path: " + path);
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    static void replaceDirectory(final Path source, final Path destination) throws IOException {
        replaceDirectory(source, destination, null);
    }

    /**
     * Replaces a live resources directory by moving the old directory aside first. Unlike a
     * delete-and-copy sequence, a failed replacement leaves the original tree available to move
     * back into place, which is essential for build and publish rollback.
     */
    private static void replaceDirectory(final Path source, final Path destination, final String expectedRevision) throws IOException {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw new IOException("replacement resources directory is unavailable: " + source);
        }
        if (expectedRevision != null && (!validSha256(expectedRevision) || !expectedRevision.equals(digestDirectory(source)))) {
            throw new IOException("replacement HuntEngine resources no longer match their expected SHA-256 revision");
        }
        final boolean destinationExisted = Files.exists(destination, LinkOption.NOFOLLOW_LINKS);
        if (destinationExisted && (!Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination))) {
            throw new IOException("active HuntEngine resources path is not a safe directory: " + destination);
        }
        final Path temporary = destination.resolveSibling('.' + destination.getFileName().toString() + ".restoring-" + UUID.randomUUID());
        final Path previous = destination.resolveSibling('.' + destination.getFileName().toString() + ".previous-" + UUID.randomUUID());
        boolean previousMoved = false;
        boolean replacementInstalled = false;
        try {
            copyDirectory(source, temporary);
            if (expectedRevision != null && !expectedRevision.equals(digestDirectory(temporary))) {
                throw new IOException("replacement HuntEngine resources changed while being copied");
            }
            if (destinationExisted) {
                moveNoReplace(destination, previous);
                previousMoved = true;
            }
            try {
                moveNoReplace(temporary, destination);
                replacementInstalled = true;
            } catch (final IOException installFailure) {
                if (previousMoved) {
                    try {
                        moveNoReplace(previous, destination);
                    } catch (final IOException rollbackFailure) {
                        installFailure.addSuppressed(rollbackFailure);
                    }
                }
                throw installFailure;
            }
            if (previousMoved) {
                try {
                    deleteDirectoryIfExists(previous);
                } catch (final IOException cleanupFailure) {
                    LOGGER.warn("Could not remove superseded HuntEngine resources directory {}", previous, cleanupFailure);
                }
            }
        } finally {
            if (!replacementInstalled && previousMoved && Files.exists(previous, LinkOption.NOFOLLOW_LINKS)
                && !Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    moveNoReplace(previous, destination);
                } catch (final IOException rollbackFailure) {
                    LOGGER.error("Could not restore the previous HuntEngine resources directory {}", previous, rollbackFailure);
                }
            }
            deleteDirectoryIfExists(temporary);
        }
    }

    private static void deleteDirectoryIfExists(final Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (final Path child : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(child);
            }
        }
    }

    private static void atomicMove(final Path source, final Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Uses the JDK's no-replace move contract for immutable candidates and directory swaps.
     * Do not add {@link StandardCopyOption#ATOMIC_MOVE} here: its replacement behaviour for an
     * existing target is implementation-specific, while publication integrity requires that a
     * collision never overwrite the existing candidate.
     */
    private static void moveNoReplace(final Path source, final Path destination) throws IOException {
        Files.move(source, destination);
    }

    private static String digest(final Path path, final String algorithm) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (var input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException(algorithm + " is not available", ex);
        }
    }

    /** Produces a deterministic digest over a safe resources directory's paths, types, and bytes. */
    static String digestDirectory(final Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw new IOException("HuntEngine resources directory is unavailable: " + directory);
        }
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final List<Path> paths;
            try (var stream = Files.walk(directory)) {
                paths = stream.sorted(Comparator.comparing(path -> directory.relativize(path).toString())).toList();
            }
            final byte[] buffer = new byte[8_192];
            for (final Path path : paths) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("symbolic links are not allowed in managed HuntEngine resources: " + path);
                }
                final Path relative = directory.relativize(path);
                final String name = relative.toString().replace('\\', '/');
                final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    digest.update((byte) 'D');
                    updateDigestLength(digest, nameBytes.length);
                    digest.update(nameBytes);
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("unsupported HuntEngine resources entry: " + path);
                }
                final long expectedSize = Files.size(path);
                digest.update((byte) 'F');
                updateDigestLength(digest, nameBytes.length);
                digest.update(nameBytes);
                updateDigestLong(digest, expectedSize);
                long copied = 0L;
                try (var input = Files.newInputStream(path)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        digest.update(buffer, 0, read);
                        copied += read;
                    }
                }
                if (copied != expectedSize) {
                    throw new IOException("HuntEngine resources entry changed while being hashed: " + path);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static void updateDigestLength(final MessageDigest digest, final int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateDigestLong(final MessageDigest digest, final long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static boolean stableIdentifier(final String value) {
        return value != null && value.matches("[a-z0-9_.:/-]+");
    }

    private static boolean validSha256(final String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private static String safeFileName(final String fileName) {
        final String raw = fileName == null ? "" : fileName.replace('\\', '/');
        final int separator = raw.lastIndexOf('/');
        final String leaf = separator < 0 ? raw : raw.substring(separator + 1);
        final String candidate = leaf.replaceAll("[^A-Za-z0-9._-]", "_");
        return candidate.isBlank() ? "content.zip" : candidate;
    }

    private static boolean booleanMethod(final Object target, final String method) throws ReflectiveOperationException {
        final Object value = invoke(target, method);
        return value instanceof Boolean bool && bool;
    }

    private static Object staticInvoke(final Class<?> type, final String name, final Object... args) throws ReflectiveOperationException {
        final Method method = method(type, name, args);
        try {
            return method.invoke(null, args);
        } catch (final InvocationTargetException ex) {
            throw targetException(ex);
        }
    }

    private static Object invoke(final Object target, final String name, final Object... args) throws ReflectiveOperationException {
        final Method method = method(target.getClass(), name, args);
        try {
            return method.invoke(target, args);
        } catch (final InvocationTargetException ex) {
            throw targetException(ex);
        }
    }

    private static Method method(final Class<?> type, final String name, final Object[] args) throws NoSuchMethodException {
        for (final Method candidate : type.getMethods()) {
            if (!candidate.getName().equals(name) || candidate.getParameterCount() != args.length) {
                continue;
            }
            final Class<?>[] parameters = candidate.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < parameters.length; index++) {
                if (args[index] != null && !wrap(parameters[index]).isInstance(args[index])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return candidate;
            }
        }
        throw new NoSuchMethodException(type.getName() + '#' + name + '/' + args.length);
    }

    private static Class<?> wrap(final Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static ReflectiveOperationException targetException(final InvocationTargetException exception) {
        final Throwable cause = exception.getCause();
        if (cause instanceof ReflectiveOperationException reflective) {
            return reflective;
        }
        return new ReflectiveOperationException(conciseMessage(cause), cause);
    }

    private static String string(final Object value, final String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static String nullableString(final Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static Instant instant(final String raw) {
        try {
            return raw == null || raw.isBlank() ? null : Instant.parse(raw);
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    private static HuntEngineMigrationState migrationState(final String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "running" -> HuntEngineMigrationState.RUNNING;
            case "completed" -> HuntEngineMigrationState.COMPLETED;
            case "completed-with-manual-work" -> HuntEngineMigrationState.COMPLETED_WITH_MANUAL_WORK;
            case "pending" -> HuntEngineMigrationState.PENDING;
            case "not-required" -> HuntEngineMigrationState.NOT_REQUIRED;
            default -> HuntEngineMigrationState.FAILED;
        };
    }

    private static HuntEngineMigrationEntryState migrationEntryState(final String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "detected" -> HuntEngineMigrationEntryState.DETECTED;
            case "backed_up", "backed-up" -> HuntEngineMigrationEntryState.BACKED_UP;
            case "isolated" -> HuntEngineMigrationEntryState.ISOLATED;
            case "preserved" -> HuntEngineMigrationEntryState.PRESERVED;
            case "drafted" -> HuntEngineMigrationEntryState.DRAFTED;
            case "manual_required", "manual-required" -> HuntEngineMigrationEntryState.MANUAL_REQUIRED;
            default -> HuntEngineMigrationEntryState.FAILED;
        };
    }

    private static String conciseMessage(final Throwable throwable) {
        final String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank()
            ? throwable == null ? "Unknown error" : throwable.getClass().getSimpleName()
            : message;
    }
}
