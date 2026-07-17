package org.huntercore.gui;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.huntercore.api.gui.HunterGuiAction;
import org.huntercore.api.gui.HunterGuiActionContext;
import org.huntercore.api.gui.HunterGuiConfirmation;
import org.huntercore.api.gui.HunterGuiConfirmationContext;
import org.huntercore.api.gui.HunterGuiConfirmationResult;
import org.huntercore.api.gui.HunterGuiConfirmationValidator;
import org.huntercore.api.gui.HunterGuiOpenResult;
import org.huntercore.api.gui.HunterGuiPresentation;
import org.huntercore.api.gui.HunterGuiRegistration;
import org.huntercore.api.gui.HunterGuiRegistrationOptions;
import org.huntercore.api.gui.HunterGuiRenderContext;
import org.huntercore.api.gui.HunterGuiRoute;
import org.huntercore.api.gui.HunterGuiScreen;
import org.huntercore.api.gui.HunterGuiService;
import org.huntercore.api.gui.HunterGuiSlot;
import org.huntercore.api.gui.HunterGuiView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Server-side implementation of HunterCore's plugin-scoped inventory GUI runtime. */
public final class HunterGuiManager implements HunterGuiService {
    private static final int MAX_HISTORY_DEPTH = 32;
    private static final int MAX_PENDING_CONFIRMATIONS = 32;

    private final Clock clock;
    private final Map<UUID, GuiSession> sessions = new HashMap<>();
    private final Set<ManagedRegistration> registrations = Collections.newSetFromMap(new IdentityHashMap<>());

    public HunterGuiManager() {
        this(Clock.systemUTC());
    }

    HunterGuiManager(@NotNull final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public @NotNull HunterGuiRegistration register(
        @NotNull final Plugin owner,
        @NotNull final Collection<? extends HunterGuiScreen> screens,
        @NotNull final HunterGuiRegistrationOptions options
    ) {
        requirePrimaryThread("register GUI screens");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(screens, "screens");
        Objects.requireNonNull(options, "options");

        final Map<String, HunterGuiScreen> indexedScreens = new LinkedHashMap<>();
        for (final HunterGuiScreen screen : screens) {
            final HunterGuiScreen nonNullScreen = Objects.requireNonNull(screen, "screen");
            final String id = HunterGuiRoute.of(nonNullScreen.id()).screenId();
            if (indexedScreens.putIfAbsent(id, nonNullScreen) != null) {
                throw new IllegalArgumentException("duplicate GUI screen id: " + id);
            }
        }
        if (indexedScreens.isEmpty()) {
            throw new IllegalArgumentException("at least one GUI screen is required");
        }

        final ManagedRegistration registration = new ManagedRegistration(owner, indexedScreens, options);
        this.registrations.add(registration);
        try {
            owner.getServer().getPluginManager().registerEvents(registration.listener, owner);
        } catch (final RuntimeException exception) {
            this.registrations.remove(registration);
            registration.deactivate();
            HandlerList.unregisterAll(registration.listener);
            throw exception;
        }
        return registration;
    }

    private @NotNull HunterGuiOpenResult open(
        @NotNull final ManagedRegistration registration,
        @NotNull final Player player,
        @NotNull final HunterGuiRoute route
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(route, "route");
        if (!isPrimaryThread()) {
            return HunterGuiOpenResult.WRONG_THREAD;
        }
        if (!registration.active) {
            return HunterGuiOpenResult.REGISTRATION_CLOSED;
        }
        if (!registration.screens.containsKey(route.screenId())) {
            return HunterGuiOpenResult.UNKNOWN_SCREEN;
        }

        final GuiSession candidate = new GuiSession(UUID.randomUUID(), player, registration, route);
        final PreparedView prepared = this.prepareView(candidate, player, route);
        if (prepared == null) {
            return HunterGuiOpenResult.RENDER_FAILED;
        }

        candidate.apply(prepared, route);
        try {
            player.openInventory(prepared.inventory);
        } catch (final RuntimeException exception) {
            this.closeSession(candidate, true);
            this.log(registration, "Could not open GUI screen '" + route.screenId() + "'.", exception);
            return HunterGuiOpenResult.RENDER_FAILED;
        }

        final GuiSession previous = this.sessions.put(player.getUniqueId(), candidate);
        if (previous != null && previous != candidate) {
            previous.clear();
        }
        return HunterGuiOpenResult.OPENED;
    }

    private @NotNull HunterGuiOpenResult navigate(
        @NotNull final GuiSession session,
        @NotNull final Player player,
        @NotNull final HunterGuiRoute route,
        final boolean deferInventoryOpen
    ) {
        if (!isPrimaryThread()) {
            return HunterGuiOpenResult.WRONG_THREAD;
        }
        if (!this.isCurrent(session, player) || !session.registration.active) {
            return HunterGuiOpenResult.NO_ACTIVE_SESSION;
        }
        if (!session.registration.screens.containsKey(route.screenId())) {
            return HunterGuiOpenResult.UNKNOWN_SCREEN;
        }
        final PreparedView prepared = this.prepareView(session, player, route);
        if (prepared == null) {
            return HunterGuiOpenResult.RENDER_FAILED;
        }
        if (deferInventoryOpen && !this.scheduleInventoryOpen(session, player, prepared.inventory)) {
            return HunterGuiOpenResult.RENDER_FAILED;
        }

        session.pushHistory(session.route);
        session.apply(prepared, route);
        if (!deferInventoryOpen && !this.openInventoryNow(session, player, prepared.inventory)) {
            return HunterGuiOpenResult.RENDER_FAILED;
        }
        return HunterGuiOpenResult.OPENED;
    }

    private @NotNull HunterGuiOpenResult refresh(
        @NotNull final GuiSession session,
        @NotNull final Player player,
        final boolean deferInventoryOpen
    ) {
        if (!isPrimaryThread()) {
            return HunterGuiOpenResult.WRONG_THREAD;
        }
        if (!this.isCurrent(session, player) || !session.registration.active) {
            return HunterGuiOpenResult.NO_ACTIVE_SESSION;
        }
        final PreparedView prepared = this.prepareView(session, player, session.route);
        if (prepared == null) {
            return HunterGuiOpenResult.RENDER_FAILED;
        }
        if (deferInventoryOpen && !this.scheduleInventoryOpen(session, player, prepared.inventory)) {
            return HunterGuiOpenResult.RENDER_FAILED;
        }
        session.apply(prepared, session.route);
        if (!deferInventoryOpen && !this.openInventoryNow(session, player, prepared.inventory)) {
            return HunterGuiOpenResult.RENDER_FAILED;
        }
        return HunterGuiOpenResult.OPENED;
    }

    private boolean back(@NotNull final GuiSession session, @NotNull final Player player, final boolean deferInventoryOpen) {
        if (!isPrimaryThread()) {
            return false;
        }
        if (!this.isCurrent(session, player) || !session.registration.active || session.history.isEmpty()) {
            return false;
        }
        final HunterGuiRoute prior = session.history.peekLast();
        final PreparedView prepared = this.prepareView(session, player, prior);
        if (prepared == null) {
            return false;
        }
        if (deferInventoryOpen && !this.scheduleInventoryOpen(session, player, prepared.inventory)) {
            return false;
        }
        session.history.removeLast();
        session.apply(prepared, prior);
        if (!deferInventoryOpen && !this.openInventoryNow(session, player, prepared.inventory)) {
            return false;
        }
        return true;
    }

    private @Nullable PreparedView prepareView(
        @NotNull final GuiSession session,
        @NotNull final Player player,
        @NotNull final HunterGuiRoute route
    ) {
        final HunterGuiScreen screen = session.registration.screens.get(route.screenId());
        if (screen == null) {
            return null;
        }

        final long nextRevision = session.revision == Long.MAX_VALUE ? 1L : session.revision + 1L;
        final HunterGuiPresentation presentation = this.resolvePresentation(session.registration, player, route);
        final HunterGuiView view;
        try {
            view = Objects.requireNonNull(
                screen.render(new ManagedRenderContext(player, route, presentation, nextRevision)),
                "screen.render returned null"
            );
        } catch (final RuntimeException exception) {
            this.log(session.registration, "Could not render GUI screen '" + route.screenId() + "'.", exception);
            return null;
        }

        try {
            final ManagedGuiInventoryHolder holder = new ManagedGuiInventoryHolder(session.id, nextRevision);
            final Inventory inventory = Bukkit.createInventory(holder, view.size(), view.title());
            holder.bind(inventory);
            final Map<Integer, BoundAction> actions = new HashMap<>();
            for (final Map.Entry<Integer, HunterGuiSlot> entry : view.slots().entrySet()) {
                final int slot = entry.getKey();
                final HunterGuiSlot definition = entry.getValue();
                inventory.setItem(slot, definition.item());
                if (definition.interactive()) {
                    actions.put(slot, new BoundAction(definition.actionId(), definition.action()));
                }
            }
            return new PreparedView(inventory, nextRevision, actions, presentation);
        } catch (final RuntimeException exception) {
            this.log(session.registration, "Could not construct GUI inventory for screen '" + route.screenId() + "'.", exception);
            return null;
        }
    }

    private @NotNull HunterGuiPresentation resolvePresentation(
        @NotNull final ManagedRegistration registration,
        @NotNull final Player player,
        @NotNull final HunterGuiRoute route
    ) {
        try {
            final HunterGuiPresentation presentation = registration.options.presentationResolver().resolve(player, route);
            if (presentation != null) {
                return presentation;
            }
            this.log(registration, "GUI presentation resolver returned null; falling back to vanilla presentation.", null);
        } catch (final RuntimeException exception) {
            this.log(registration, "GUI presentation resolver failed; falling back to vanilla presentation.", exception);
        }
        final Locale locale = player.locale();
        return HunterGuiPresentation.vanilla(locale == null ? Locale.ENGLISH : locale);
    }

    private boolean close(@NotNull final ManagedRegistration registration, @NotNull final Player player) {
        if (!isPrimaryThread()) {
            return false;
        }
        final GuiSession session = this.sessions.get(player.getUniqueId());
        if (session == null || session.registration != registration) {
            return false;
        }
        this.closeSession(session, true);
        return true;
    }

    private int closeAll(@NotNull final ManagedRegistration registration) {
        if (!isPrimaryThread()) {
            return 0;
        }
        final List<GuiSession> ownedSessions = new ArrayList<>();
        for (final GuiSession session : this.sessions.values()) {
            if (session.registration == registration) {
                ownedSessions.add(session);
            }
        }
        for (final GuiSession session : ownedSessions) {
            this.closeSession(session, true);
        }
        return ownedSessions.size();
    }

    private void closeSession(@NotNull final GuiSession session, final boolean closeInventory) {
        this.closeSession(session, closeInventory, false);
    }

    private void closeSession(
        @NotNull final GuiSession session,
        final boolean closeInventory,
        final boolean deferInventoryClose
    ) {
        final Inventory visibleInventory = this.visibleInventory(session);
        this.sessions.remove(session.playerId, session);
        session.clear();
        if (closeInventory && visibleInventory != null) {
            if (deferInventoryClose) {
                this.scheduleInventoryClose(session, visibleInventory);
            } else {
                session.player.closeInventory();
            }
        }
    }

    private @Nullable Inventory visibleInventory(@NotNull final GuiSession session) {
        final Inventory topInventory = session.player.getOpenInventory().getTopInventory();
        return this.belongsToSession(session, topInventory) ? topInventory : null;
    }

    private boolean openInventoryNow(
        @NotNull final GuiSession session,
        @NotNull final Player player,
        @NotNull final Inventory inventory
    ) {
        final Inventory previousInventory = player.getOpenInventory().getTopInventory();
        final boolean replacingManagedView = this.belongsToSession(session, previousInventory);
        if (replacingManagedView) {
            session.beginProgrammaticInventoryOpen(previousInventory);
        }
        try {
            player.openInventory(inventory);
            return true;
        } catch (final RuntimeException exception) {
            this.log(session.registration, "Could not open a managed GUI view transition.", exception);
            this.closeSession(session, true);
            return false;
        } finally {
            if (replacingManagedView) {
                session.finishProgrammaticInventoryOpen(previousInventory);
            }
        }
    }

    private boolean scheduleInventoryOpen(
        @NotNull final GuiSession session,
        @NotNull final Player player,
        @NotNull final Inventory inventory
    ) {
        try {
            session.registration.owner.getServer().getScheduler().runTask(session.registration.owner, () -> {
                if (!session.registration.active || !this.isCurrent(session, player) || session.inventory != inventory) {
                    return;
                }

                final Inventory previousInventory = player.getOpenInventory().getTopInventory();
                if (!player.isOnline() || !this.belongsToSession(session, previousInventory)) {
                    this.closeSession(session, false);
                    return;
                }

                session.beginProgrammaticInventoryOpen(previousInventory);
                try {
                    player.openInventory(inventory);
                } catch (final RuntimeException exception) {
                    this.log(session.registration, "Could not open a scheduled managed GUI view transition.", exception);
                    this.closeSession(session, true);
                } finally {
                    session.finishProgrammaticInventoryOpen(previousInventory);
                }
            });
            return true;
        } catch (final RuntimeException exception) {
            this.log(session.registration, "Could not schedule a managed GUI view transition.", exception);
            return false;
        }
    }

    private void scheduleInventoryClose(@NotNull final GuiSession session, @NotNull final Inventory inventory) {
        try {
            session.registration.owner.getServer().getScheduler().runTask(session.registration.owner, () -> {
                if (session.player.getOpenInventory().getTopInventory() == inventory) {
                    session.player.closeInventory();
                }
            });
        } catch (final RuntimeException exception) {
            this.log(session.registration, "Could not schedule a managed GUI close.", exception);
        }
    }

    private boolean isCurrent(@NotNull final GuiSession session, @NotNull final Player player) {
        return session.player == player && this.sessions.get(player.getUniqueId()) == session;
    }

    private boolean isActionCurrent(@NotNull final GuiSession session, @NotNull final Player player, final long revision) {
        return isPrimaryThread()
            && session.registration.active
            && this.isCurrent(session, player)
            && session.revision == revision;
    }

    private void handleClick(@NotNull final ManagedRegistration registration, @NotNull final InventoryClickEvent event) {
        final Inventory topInventory = event.getView().getTopInventory();
        final ManagedGuiInventoryHolder holder = managedHolder(topInventory);
        if (holder == null) {
            return;
        }

        // Every managed top inventory is immutable. This covers shift-click, hotbar swaps,
        // number-key actions, cursor placement, and clicks in the player's lower inventory.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final GuiSession session = this.sessions.get(player.getUniqueId());
        if (!this.matches(registration, session, player, topInventory, holder)) {
            return;
        }
        final int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= topInventory.getSize()) {
            return;
        }
        final BoundAction action = session.actions.get(rawSlot);
        if (action == null) {
            return;
        }

        try {
            action.action.execute(new ManagedActionContext(this, session, player, action.id, event.getClick()));
        } catch (final RuntimeException exception) {
            this.log(registration, "GUI action '" + action.id + "' failed on route '" + session.route.screenId() + "'.", exception);
        }
    }

    private void handleDrag(@NotNull final InventoryDragEvent event) {
        if (managedHolder(event.getView().getTopInventory()) != null) {
            // A managed GUI never accepts source or destination changes from a drag operation.
            event.setCancelled(true);
        }
    }

    private void handleClose(@NotNull final ManagedRegistration registration, @NotNull final InventoryCloseEvent event) {
        final Inventory topInventory = event.getView().getTopInventory();
        final ManagedGuiInventoryHolder holder = managedHolder(topInventory);
        if (holder == null || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        final GuiSession session = this.sessions.get(player.getUniqueId());
        if (session != null
            && session.registration == registration
            && session.player == player
            && session.id.equals(holder.sessionId)
            && !session.isProgrammaticInventoryOpen(topInventory)) {
            this.closeSession(session, false);
        }
    }

    private void handleQuit(@NotNull final ManagedRegistration registration, @NotNull final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final GuiSession session = this.sessions.get(player.getUniqueId());
        if (session != null && session.registration == registration) {
            this.closeSession(session, false);
        }
    }

    private boolean matches(
        @NotNull final ManagedRegistration registration,
        @Nullable final GuiSession session,
        @NotNull final Player player,
        @NotNull final Inventory topInventory,
        @NotNull final ManagedGuiInventoryHolder holder
    ) {
        return session != null
            && session.registration == registration
            && session.player == player
            && session.inventory == topInventory
            && session.id.equals(holder.sessionId)
            && session.revision == holder.revision;
    }

    private boolean belongsToSession(@NotNull final GuiSession session, @NotNull final Inventory inventory) {
        final ManagedGuiInventoryHolder holder = managedHolder(inventory);
        return holder != null && session.id.equals(holder.sessionId);
    }

    private @NotNull HunterGuiConfirmation armConfirmation(
        @NotNull final GuiSession session,
        @NotNull final Player player,
        @NotNull final String confirmationId,
        @NotNull final Duration ttl,
        @NotNull final HunterGuiConfirmationValidator revalidator
    ) {
        if (!this.isCurrent(session, player)) {
            throw new IllegalStateException("GUI session is no longer active");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("confirmation ttl must be positive");
        }
        Objects.requireNonNull(revalidator, "revalidator");
        final Instant createdAt = this.clock.instant();
        final Instant expiresAt;
        try {
            expiresAt = createdAt.plus(ttl);
        } catch (final DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException("confirmation ttl is outside the supported range", exception);
        }
        final HunterGuiConfirmation confirmation = new HunterGuiConfirmation(confirmationId, expiresAt);
        this.pruneExpiredConfirmations(session, createdAt);
        if (!session.confirmations.containsKey(confirmation.id())
            && session.confirmations.size() >= MAX_PENDING_CONFIRMATIONS) {
            throw new IllegalStateException("too many pending GUI confirmations for this session");
        }
        session.confirmations.put(
            confirmation.id(),
            new PendingConfirmation(session.route, confirmation, createdAt, revalidator)
        );
        return confirmation;
    }

    private void pruneExpiredConfirmations(@NotNull final GuiSession session, @NotNull final Instant now) {
        session.confirmations.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().confirmation.expiresAt()));
    }

    private @NotNull HunterGuiConfirmationResult consumeConfirmation(
        @NotNull final GuiSession session,
        @NotNull final Player player,
        @NotNull final String confirmationId
    ) {
        if (!this.isCurrent(session, player)) {
            return HunterGuiConfirmationResult.MISSING;
        }
        final Instant now = this.clock.instant();
        final String id = new HunterGuiConfirmation(confirmationId, now).id();
        final PendingConfirmation pending = session.confirmations.remove(id);
        if (pending == null) {
            return HunterGuiConfirmationResult.MISSING;
        }
        if (!now.isBefore(pending.confirmation.expiresAt())) {
            return HunterGuiConfirmationResult.EXPIRED;
        }
        try {
            return pending.revalidator.validate(
                new HunterGuiConfirmationContext(player, pending.originRoute, pending.confirmation, pending.createdAt)
            ) ? HunterGuiConfirmationResult.CONFIRMED : HunterGuiConfirmationResult.REJECTED;
        } catch (final RuntimeException exception) {
            this.log(session.registration, "GUI confirmation '" + id + "' revalidation failed.", exception);
            return HunterGuiConfirmationResult.REJECTED;
        }
    }

    private void clearConfirmation(@NotNull final GuiSession session, @NotNull final Player player, @NotNull final String confirmationId) {
        if (!this.isCurrent(session, player)) {
            return;
        }
        final String id = new HunterGuiConfirmation(confirmationId, this.clock.instant()).id();
        session.confirmations.remove(id);
    }

    private void log(@NotNull final ManagedRegistration registration, @NotNull final String message, @Nullable final Throwable throwable) {
        if (throwable == null) {
            registration.owner.getLogger().warning(message);
        } else {
            registration.owner.getLogger().log(Level.WARNING, message, throwable);
        }
    }

    private static @Nullable ManagedGuiInventoryHolder managedHolder(@NotNull final Inventory inventory) {
        final InventoryHolder holder = inventory.getHolder(false);
        return holder instanceof ManagedGuiInventoryHolder managed ? managed : null;
    }

    private static boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }

    private static void requirePrimaryThread(@NotNull final String operation) {
        if (!isPrimaryThread()) {
            throw new IllegalStateException("HunterCore GUI runtime must " + operation + " on Bukkit's primary server thread");
        }
    }

    private final class ManagedRegistration implements HunterGuiRegistration {
        private final Plugin owner;
        private final Map<String, HunterGuiScreen> screens;
        private final HunterGuiRegistrationOptions options;
        private final RegistrationListener listener = new RegistrationListener(this);
        private volatile boolean active = true;

        private ManagedRegistration(
            @NotNull final Plugin owner,
            @NotNull final Map<String, HunterGuiScreen> screens,
            @NotNull final HunterGuiRegistrationOptions options
        ) {
            this.owner = owner;
            this.screens = Map.copyOf(screens);
            this.options = options;
        }

        @Override
        public @NotNull Plugin owner() {
            return this.owner;
        }

        @Override
        public @NotNull Listener listener() {
            return this.listener;
        }

        @Override
        public boolean active() {
            return this.active;
        }

        @Override
        public @NotNull HunterGuiOpenResult open(@NotNull final Player player, @NotNull final HunterGuiRoute route) {
            return HunterGuiManager.this.open(this, player, route);
        }

        @Override
        public @NotNull HunterGuiOpenResult refresh(@NotNull final Player player) {
            if (!isPrimaryThread()) {
                return HunterGuiOpenResult.WRONG_THREAD;
            }
            final GuiSession session = HunterGuiManager.this.sessions.get(player.getUniqueId());
            if (session == null || session.registration != this) {
                return HunterGuiOpenResult.NO_ACTIVE_SESSION;
            }
            return HunterGuiManager.this.refresh(session, player, false);
        }

        @Override
        public boolean back(@NotNull final Player player) {
            if (!isPrimaryThread()) {
                return false;
            }
            final GuiSession session = HunterGuiManager.this.sessions.get(player.getUniqueId());
            return session != null && session.registration == this && HunterGuiManager.this.back(session, player, false);
        }

        @Override
        public boolean close(@NotNull final Player player) {
            return HunterGuiManager.this.close(this, player);
        }

        @Override
        public int closeAll() {
            return HunterGuiManager.this.closeAll(this);
        }

        @Override
        public void close() {
            requirePrimaryThread("close a GUI registration");
            if (!this.active) {
                return;
            }
            this.deactivate();
            HunterGuiManager.this.closeAll(this);
            HunterGuiManager.this.registrations.remove(this);
            HandlerList.unregisterAll(this.listener);
        }

        private void deactivate() {
            this.active = false;
        }
    }

    private final class RegistrationListener implements Listener {
        private final ManagedRegistration registration;

        private RegistrationListener(@NotNull final ManagedRegistration registration) {
            this.registration = registration;
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onInventoryClick(@NotNull final InventoryClickEvent event) {
            HunterGuiManager.this.handleClick(this.registration, event);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onInventoryDrag(@NotNull final InventoryDragEvent event) {
            HunterGuiManager.this.handleDrag(event);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onInventoryClose(@NotNull final InventoryCloseEvent event) {
            HunterGuiManager.this.handleClose(this.registration, event);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerQuit(@NotNull final PlayerQuitEvent event) {
            HunterGuiManager.this.handleQuit(this.registration, event);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPluginDisable(@NotNull final PluginDisableEvent event) {
            if (event.getPlugin() == this.registration.owner) {
                this.registration.close();
            }
        }
    }

    private static final class GuiSession {
        private final UUID id;
        private final UUID playerId;
        private final Player player;
        private final ManagedRegistration registration;
        private final Deque<HunterGuiRoute> history = new ArrayDeque<>();
        private final Map<String, PendingConfirmation> confirmations = new HashMap<>();
        private HunterGuiRoute route;
        private Inventory inventory;
        private long revision;
        private Map<Integer, BoundAction> actions = Map.of();
        private HunterGuiPresentation presentation;
        private Inventory programmaticInventoryOpen;

        private GuiSession(
            @NotNull final UUID id,
            @NotNull final Player player,
            @NotNull final ManagedRegistration registration,
            @NotNull final HunterGuiRoute route
        ) {
            this.id = id;
            this.player = player;
            this.playerId = player.getUniqueId();
            this.registration = registration;
            this.route = route;
        }

        private void apply(@NotNull final PreparedView prepared, @NotNull final HunterGuiRoute route) {
            this.inventory = prepared.inventory;
            this.revision = prepared.revision;
            this.actions = prepared.actions;
            this.presentation = prepared.presentation;
            this.route = route;
        }

        private void pushHistory(@NotNull final HunterGuiRoute route) {
            while (this.history.size() >= MAX_HISTORY_DEPTH) {
                this.history.removeFirst();
            }
            this.history.addLast(route);
        }

        private void beginProgrammaticInventoryOpen(@NotNull final Inventory inventory) {
            this.programmaticInventoryOpen = inventory;
        }

        private boolean isProgrammaticInventoryOpen(@NotNull final Inventory inventory) {
            return this.programmaticInventoryOpen == inventory;
        }

        private void finishProgrammaticInventoryOpen(@NotNull final Inventory inventory) {
            if (this.programmaticInventoryOpen == inventory) {
                this.programmaticInventoryOpen = null;
            }
        }

        private void clear() {
            this.history.clear();
            this.confirmations.clear();
            this.actions = Map.of();
            this.inventory = null;
            this.programmaticInventoryOpen = null;
        }
    }

    private record BoundAction(@NotNull String id, @NotNull HunterGuiAction action) {
    }

    private record PreparedView(
        @NotNull Inventory inventory,
        long revision,
        @NotNull Map<Integer, BoundAction> actions,
        @NotNull HunterGuiPresentation presentation
    ) {
        private PreparedView {
            actions = Map.copyOf(actions);
        }
    }

    private record PendingConfirmation(
        @NotNull HunterGuiRoute originRoute,
        @NotNull HunterGuiConfirmation confirmation,
        @NotNull Instant createdAt,
        @NotNull HunterGuiConfirmationValidator revalidator
    ) {
    }

    private record ManagedRenderContext(
        @NotNull Player player,
        @NotNull HunterGuiRoute route,
        @NotNull HunterGuiPresentation presentation,
        long actionRevision
    ) implements HunterGuiRenderContext {
    }

    private static final class ManagedGuiInventoryHolder implements InventoryHolder {
        private final UUID sessionId;
        private final long revision;
        private Inventory inventory;

        private ManagedGuiInventoryHolder(@NotNull final UUID sessionId, final long revision) {
            this.sessionId = sessionId;
            this.revision = revision;
        }

        private void bind(@NotNull final Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (this.inventory == null) {
                throw new IllegalStateException("managed GUI inventory is not bound yet");
            }
            return this.inventory;
        }
    }

    private static final class ManagedActionContext implements HunterGuiActionContext {
        private final HunterGuiManager manager;
        private final GuiSession session;
        private final Player player;
        private final String actionId;
        private final ClickType clickType;
        private final HunterGuiRoute route;
        private final HunterGuiPresentation presentation;
        private final long actionRevision;

        private ManagedActionContext(
            @NotNull final HunterGuiManager manager,
            @NotNull final GuiSession session,
            @NotNull final Player player,
            @NotNull final String actionId,
            @NotNull final ClickType clickType
        ) {
            this.manager = manager;
            this.session = session;
            this.player = player;
            this.actionId = actionId;
            this.clickType = clickType;
            this.route = session.route;
            this.presentation = session.presentation;
            this.actionRevision = session.revision;
        }

        @Override
        public @NotNull Player player() {
            return this.player;
        }

        @Override
        public @NotNull HunterGuiRoute route() {
            return this.route;
        }

        @Override
        public @NotNull HunterGuiPresentation presentation() {
            return this.presentation;
        }

        @Override
        public long actionRevision() {
            return this.actionRevision;
        }

        @Override
        public @NotNull String actionId() {
            return this.actionId;
        }

        @Override
        public @NotNull ClickType clickType() {
            return this.clickType;
        }

        @Override
        public @NotNull HunterGuiOpenResult navigate(@NotNull final HunterGuiRoute route) {
            if (!this.manager.isActionCurrent(this.session, this.player, this.actionRevision)) {
                return HunterGuiOpenResult.NO_ACTIVE_SESSION;
            }
            return this.manager.navigate(this.session, this.player, route, true);
        }

        @Override
        public @NotNull HunterGuiOpenResult refresh() {
            if (!this.manager.isActionCurrent(this.session, this.player, this.actionRevision)) {
                return HunterGuiOpenResult.NO_ACTIVE_SESSION;
            }
            return this.manager.refresh(this.session, this.player, true);
        }

        @Override
        public boolean back() {
            if (!this.manager.isActionCurrent(this.session, this.player, this.actionRevision)) {
                return false;
            }
            return this.manager.back(this.session, this.player, true);
        }

        @Override
        public void close() {
            if (!this.manager.isActionCurrent(this.session, this.player, this.actionRevision)) {
                return;
            }
            this.manager.closeSession(this.session, true, true);
        }

        @Override
        public @NotNull HunterGuiConfirmation armConfirmation(
            @NotNull final String confirmationId,
            @NotNull final Duration ttl,
            @NotNull final HunterGuiConfirmationValidator revalidator
        ) {
            if (!this.manager.isActionCurrent(this.session, this.player, this.actionRevision)) {
                throw new IllegalStateException("GUI action is no longer current");
            }
            return this.manager.armConfirmation(this.session, this.player, confirmationId, ttl, revalidator);
        }

        @Override
        public @NotNull HunterGuiConfirmationResult consumeConfirmation(@NotNull final String confirmationId) {
            if (!this.manager.isActionCurrent(this.session, this.player, this.actionRevision)) {
                return HunterGuiConfirmationResult.MISSING;
            }
            return this.manager.consumeConfirmation(this.session, this.player, confirmationId);
        }

        @Override
        public void clearConfirmation(@NotNull final String confirmationId) {
            if (!this.manager.isActionCurrent(this.session, this.player, this.actionRevision)) {
                return;
            }
            this.manager.clearConfirmation(this.session, this.player, confirmationId);
        }
    }
}
