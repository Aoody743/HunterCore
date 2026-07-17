# HunterCore Notes

HunterCore is an independent Minecraft server core with a bundled plugin layer, a preferences file, HunterTools runtime modules, and a small extension API.

## Build

Use the Paperweight patch/build flow:

```bash
(cd third-party/hunt-engine && ./gradlew assembleHuntEngine --no-daemon)
GIT_CONFIG_COUNT=1 \
GIT_CONFIG_KEY_0=url.git@github.com:.insteadOf \
GIT_CONFIG_VALUE_0=https://github.com/ \
./gradlew packageHunterCoreRelease --no-daemon --no-configuration-cache
```

The build requires Java 25, Git, Bash 3.2 or newer, `curl`, `unzip`, `tar`, and `perl`. Windows developers should use WSL. Build HuntEngine first with its own fixed Gradle wrapper, then HunterCore embeds only `third-party/hunt-engine/target/HuntEngine.jar`; HunterCore does not use a Gradle composite build.

The Minecraft 26.2 optimization baseline is pinned to DivineMC `b584fd628023e2bcbf5fc165ed84b1b9e367f399` and Purpur `1fc15cbbf7f1060b3f5ac3b1b1cbec812fb9b90e`. HunterCore retains the corresponding C2ME, Lithium, Matter secure-seed, regionized ticking, and Leaves protocol implementations before applying its own patches.

The release jar is generated at:

```text
divinemc-server/build/libs/HunterCore-2.9.16-build.1-MinecraftServer-26.2-release.jar
```

`packageHunterCoreRelease` trims bundled Zstd and SQLite native jars to reduce the release size. It keeps Linux, macOS, and Windows x86_64/aarch64 native libraries, plus Linux-Musl x86_64/aarch64 for SQLite. Artifact size varies with the server and bundled plugin set; there is no fixed size promise. Use `:divinemc-server:createPaperclipJar` when a fully universal upstream-style paperclip jar is needed.

The `Build HunterCore` GitHub Actions workflow runs on `main`, `codex/mc-26.2-experimental`, `codex/v2.9.16-release`, matching pull requests, and manual dispatches. It builds HuntEngine independently, applies patches, then runs targeted API/plugin/server checks before assembling release assets. The release workflow uses the same targeted verification.

## Bundled Plugins

HunterCore installs bundled plugins before Paper scans the plugin directory. On first startup it writes:

```text
plugins/HunterCore/preferences.yml
```

That file can disable the entire installer, disable individual bundled plugins, stop automatic replacement of changed bundled jars, and toggle HunterTools runtime modules/commands.

Current first batch:

```text
ViaVersion 5.10.0
ViaBackwards 5.10.0
ViaRewind 4.1.2
BlueMap 5.22
Chunky 1.5.3
PlaceholderAPI 2.12.2
SkinsRestorer 15.12.4
Vault 1.7.3
ProtocolLib 5.4.0
WorldEdit 7.4.3
WorldGuard 7.0.17
Multiverse-Core 5.7.1
LuckPerms 5.5.53
CoreProtect 24.0
HunterTPA builtin
HunterAuth builtin
HunterTools builtin
HuntEngine builtin (GPL-3.0 Community Edition fork)
```

CoreProtect 24.0 is bundled and enabled by default. This release targets Minecraft 26.2 and includes CraftEngine-compatible custom block logging support.

External plugins are prepared by:

```bash
scripts/prepare-bundled-plugins.sh
```

To add another external bundled plugin, extend that script with a download/build step and call `manifest_entry`. To add another built-in plugin, add a subproject under `huntercore-plugins/`, copy its jar into `META-INF/huntercore/bundled-plugins` from `build.gradle.kts`, and add a resource entry to `divinemc-server/src/main/resources/META-INF/huntercore/bundled-plugins.yml`.

## HuntEngine

HuntEngine is HunterCore's GPL-3.0 Community Edition distribution of CraftEngine, pinned to upstream `22fe37c` (`26.7.3`) and the HunterCore 26.2 Paper build-1 baseline. Its source, license, attribution, and change log are included under `third-party/hunt-engine/`; only the independently verified `target/HuntEngine.jar` is embedded in the server release. The jar itself carries GPLv3, provenance, change-log, notice, and complete third-party license text in `META-INF/huntengine/`. It owns the custom-content catalogue and resource-pack lifecycle. HunterAuth asks the service to send its UI pack, then cleanly retains the normal inventory GUI if a player declines or the pack cannot be used.

Use `bash scripts/verify-hunt-engine-vendor.sh third-party/hunt-engine/target/HuntEngine.jar` after an independent HuntEngine build. The verifier is network-free and checks the vendored source restrictions, fixed coordinates, plugin identity, legal records, proxy layout, and duplicate zip entries. It is not a substitute for the required pre-publish cold-start smoke test, which also needs a verified Paper server fixture.

Use `/huntengine` or `/he` for the engine. The old `/hunterassets`, `/ha`, and `/hassets` names remain migration notices during the 2.9.x line; the old plugin itself is not installed beside HuntEngine. Existing HunterAssets content is backed up and journaled before migration, with complex content preserved as a `huntercraft-legacy` draft for manual review rather than silently rewritten.

## Hybrid direct and proxy ingress

`plugins/HunterCore/proxies.yml` allows the same backend to accept direct players plus any number of trusted BungeeCord and Velocity nodes. Direct authentication defaults to `offline`; set `direct.authentication: online` or list selected entry hostnames under `direct.online-hostnames` to use Mojang authentication. Each proxy entry must define a unique `id`, `type`, IP/CIDR-only `trusted-addresses`, `network`, and `online-authenticated`; Velocity entries also require their forwarding `secret`.

Install `HunterCore-Network-Bungee-2.9.16.jar` or `HunterCore-Network-Velocity-2.9.16.jar` on each proxy when network-wide Tab and chat are required. The companion's generated `network.properties` must use a `node-id` matching the corresponding `proxies.yml` entry and the same `network` value. Direct players see everyone on the current backend. Proxy players additionally see remote players and remote chat in their configured network, while direct players never receive remote-network-only entries or messages.

HunterAuth now evaluates the verified connection rather than the global server mode: offline direct connections require the normal password flow, Mojang-authenticated direct connections and trusted proxy connections bypass it, and unknown sources fail closed. After authentication or bypass, SkinsRestorer is invoked asynchronously; an unavailable official skin falls back to its built-in `steve` skin without blocking login.

## Commands

```text
/about
/hc
/hc help
/hc about
/hc system
/hc plugins
/hc preferences
/hc preferences bundled <plugin-id> <on|off>
/hc preferences module <module> <on|off>
/hc preferences command <essentials|management|fake-players|real-fake-players|npcs> <command> <on|off>
/hc reload
/htps
/hc admin modules
/hc admin module <module> <on|off>
/hc admin command <essentials|management|fake-players|real-fake-players|npcs> <command> <on|off>
/hc admin memory
/hc admin gc
/hc admin threads
/hc admin optimize
/hc admin motd <status|line1 <text>|line2 <text>|max <number|default>>
/hc admin web status
/hc admin web restart
/hc admin web users
/hc admin web user <name> <admin|player>
/hc admin web allow <name> <inherit|none|*|command...>
/hc admin web execution <name> <on|off>
/hc admin web remove <name>
/player spawn <name> [world x y z [yaw pitch]]
/player remove <name>
/player list
/player inv <name>
/player skin <name> <minecraftName|clear>
/player tp <name> [world x y z [yaw pitch]]
/player tphere <name>
/player look <name> [yaw pitch|north|south|east|west|up|down]
/player move <name> <forward|back|left|right|stop|forwardValue> [sidewaysValue] [ticks] [jump]
/player sneak <name> <on|off>
/player sprint <name> <on|off>
/player jump <name> [once|continuous|stop]
/player use <name> [once|continuous|stop]
/player attack <name> [once|continuous|stop]
/player stop <name>
/player click <name> [command|clear]
/player drop <name>
/player dropstack <name>
/player swap <name>
/player gm <name> <survival|creative|adventure|spectator>
/player slot <name> <1-9>
/player ai <name> <status|on|off|goal|once|approve|deny> [text]
/player info [name]
/player clear
/npc spawn <name> [villager|mannequin] [world x y z [yaw pitch]]
/npc remove <name>
/npc list
/npc skin <name> <minecraftName|clear>
/npc tp <name> [world x y z [yaw pitch]]
/npc tphere <name>
/npc look <name> [yaw pitch|north|south|east|west|up|down]
/npc pose <name> <standing|sneaking|swimming|fall-flying|sleeping>
/npc click <name> [command|clear]
/npc info [name]
/npc clear
/start
/story <start|enable|disable|status|skip|stop|line|meltdown>
```

`/about` is HunterCore-specific. `/hc system` prints JVM, OS, CPU, memory, uptime, player count, and plugin directory information.

HunterTools provides TPS actionbar/sidebar display, a built-in MOTD module, and essentials-style commands such as `/heal`, `/feed`, `/fly`, `/gm`, `/day`, `/night`, `/sun`, `/rain`, `/thunder`, `/broadcast`, `/clearchat`, `/speed`, `/spawn`, `/setspawn`, `/back`, `/hat`, `/craft`, `/enderchest`, and `/trash`. The client F3 server brand defaults to `"HunterCore" Server` and can be changed from the web panel.

`/npc` creates managed Villager or Mannequin NPCs. Villagers are suitable for functional NPCs, while Mannequins are suitable for player-like display NPCs and support official Minecraft skin loading.

These lightweight actors are not real `ServerPlayer` connections, so they do not occupy player slots, load chunks, or run Carpet-style continuous use/attack/jump/sneak actions.

`/player` is the real `ServerPlayer` bot module. It joins the online player list, fires the normal join/quit flow, participates in chunk loading, supports Carpet-like continuous `use`, `attack`, and `jump` loops, and can open its inventory for direct admin editing with `/player inv <name>`. Real player bots are runtime-only and have Bukkit persistence disabled; disabling `real-fake-players` or unloading HunterTools removes them according to preferences.

Story Mode is an experimental HunterTools sequence that combines real fake players, AI persona overlays, and phase-based event scripting. It is not open by default: `modules.story-mode.enabled` defaults to `false`, `/start` refuses to run until an admin explicitly enables it with `/story enable`, and `/story disable` stops the running sequence and removes the story fake player. Keep it for controlled test or filming environments rather than public-server defaults.

## Web Panel And Map

HunterTools includes a lightweight built-in web panel. It defaults to `http://127.0.0.1:8088/`. The built-in service is HTTP-only: never expose its port directly to the public Internet. Public access must terminate HTTPS at a reverse proxy such as Caddy or Nginx, with a firewall restricting the backend port to that proxy. After confirming users always connect through HTTPS, set `modules.web-panel.secure-cookies` to `true` and restart the panel so Secure cookies and HSTS are enabled. Binding to `0.0.0.0` is appropriate only when those network controls are in place.

Guests can view public status, health alerts, and the configured BlueMap URL. Only logged-in player users with a verified, bound HunterAuth game UUID can view detailed player/plugin data and run `modules.web-panel.player-allowed-commands` or their per-user `allowed-commands`; admin users can run console commands when `modules.web-panel.admin-command-execution` is enabled, and can also be restricted with per-user command lists. Web roles are bound to a verified HunterAuth UUID, so they survive Minecraft name changes and cannot have a separate web password. The web UI blocks deleting or demoting the last identity-bound admin. The `web-panel` module is self-protected from web shutdown. Web commands capture command output when possible, capped by `modules.web-panel.command-output-lines` and `modules.web-panel.command-output-chars`. Logged-in POST requests require a session CSRF token by default.

Create web users from console or an op account:

```text
/hc admin web user admin admin
/hc admin web user player player
/hc admin web allow player list spawn
/hc admin web execution player on
```

Web registration and in-game `/register` share the same HunterAuth account, username reservation, and password. Before assigning any web role, the player must complete one in-game HunterAuth login so the account is bound to its real UUID. A HunterAuth player does not become a web administrator merely because the matching Minecraft name is an operator; an existing administrator must explicitly assign and authorize the web admin role.

On upgrade, legacy standalone web-password hashes are removed. Have each former web administrator sign in to the game once with HunterAuth, then bind the role again with the command above.

BlueMap is bundled for the web map, and Chunky is bundled for chunk pre-generation/performance prep. HunterCore prepares `plugins/BlueMap/core.conf` with `accept-download: true` on first startup so BlueMap can download Mojang client resources and start rendering without a manual config edit.

PlaceholderAPI, SkinsRestorer, Vault, ProtocolLib, WorldEdit, and WorldGuard are bundled as a common server foundation for placeholders, offline-mode skin restoration, economy/permission bridging, packet/protocol extensions, map editing, and region protection. HunterTools provides lightweight built-in MOTD and utility commands without bundling EssentialsX or MiniMOTD. Each bundled plugin can still be disabled under `bundled-plugins.plugins.<plugin-id>`.

## Optimizations

The first optimization batch is intentionally conservative:

```text
optimizations.bundled-plugin-parallel-install.enabled
optimizations.bundled-plugin-parallel-install.max-workers
optimizations.hunter-tools.async-rendering
optimizations.hunter-tools.async-save
optimizations.hunter-tools.player-cache
optimizations.hunter-tools.render-workers
optimizations.hunter-tools.actor-async-load
optimizations.hunter-tools.actor-batch-save
optimizations.hunter-tools.web-panel-workers
optimizations.cpu.enabled
optimizations.cpu.paper-worker-threads
optimizations.cpu.divine-worker-threads
optimizations.cpu.netty-io-threads
optimizations.cpu.common-pool-parallelism
```

Bundled plugin install work is parallelized across different jar files. HunterTools renders sidebar text, loads fake player/NPC definitions, serves the web panel, saves preferences, and requests GC off the main thread, then returns to the Bukkit main thread for player/server mutations. Public guest status responses are cached for 1 second by default with `modules.web-panel.status-cache-millis`. Web panel health alerts expose configurable thresholds for low TPS, high MSPT, heap pressure, per-world chunk/entity load, and disabled plugins.

HunterCore also applies CPU-aware startup defaults for Paper/core worker threads, Netty IO threads, and ForkJoin common pool parallelism. Existing JVM flags are preserved by default. The web panel exposes the same CPU, worker, Netty, ForkJoin, and HunterTools web worker settings for remote status checks.

## API

The API entrypoint is:

```java
org.huntercore.api.HunterCoreProvider.get()
```

Plugins can register future `/hc` subcommands with:

```java
HunterCoreProvider.get().registerCommandExtension(extension);
```

The extension interface is `org.huntercore.api.HunterCommandExtension`.
