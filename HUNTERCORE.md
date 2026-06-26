# HunterCore Notes

HunterCore is an independent Minecraft server core with a bundled plugin layer, a preferences file, HunterTools runtime modules, and a small extension API.

## Build

Use the Paperweight patch/build flow:

```bash
GIT_CONFIG_COUNT=1 \
GIT_CONFIG_KEY_0=url.git@github.com:.insteadOf \
GIT_CONFIG_VALUE_0=https://github.com/ \
./gradlew packageHunterCoreRelease --no-daemon --no-configuration-cache
```

The release jar is generated at:

```text
divinemc-server/build/libs/HunterCore-1.5.0-build.1-MinecraftServer-26.1.2-release.jar
```

`packageHunterCoreRelease` trims bundled Zstd and SQLite native jars to common server platforms so the release artifact stays below 100MB. It keeps Linux, macOS, and Windows x86_64/aarch64 native libraries, plus Linux-Musl x86_64/aarch64 for SQLite. Use `:divinemc-server:createPaperclipJar` when a fully universal upstream-style paperclip jar is needed.

The `Build HunterCore` GitHub Actions workflow runs on `main`, pull requests targeting `main`, and manual dispatches. Release jars are built by the `Release HunterCore` workflow.

## Bundled Plugins

HunterCore installs bundled plugins before Paper scans the plugin directory. On first startup it writes:

```text
plugins/HunterCore/preferences.yml
```

That file can disable the entire installer, disable individual bundled plugins, stop automatic replacement of changed bundled jars, and toggle HunterTools runtime modules/commands.

Current first batch:

```text
ViaVersion 5.9.1
ViaBackwards 5.9.1
ViaRewind 4.1.1
BlueMap 5.20
Chunky 1.5.3
PlaceholderAPI 2.12.2
Vault 1.7.3
ProtocolLib 5.4.0
WorldEdit 7.4.3
WorldGuard 7.0.17
Multiverse-Core 5.7.0
LuckPerms 5.5.55
CoreProtect 23.2
HunterTPA builtin
HunterAuth builtin
HunterTools builtin
```

External plugins are prepared by:

```bash
scripts/prepare-bundled-plugins.sh
```

To add another external bundled plugin, extend that script with a download/build step and call `manifest_entry`. To add another built-in plugin, add a subproject under `huntercore-plugins/`, copy its jar into `META-INF/huntercore/bundled-plugins` from `build.gradle.kts`, and add a resource entry to `divinemc-server/src/main/resources/META-INF/huntercore/bundled-plugins.yml`.

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
/hc admin web user <name> <admin|player> <password>
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
```

`/about` is HunterCore-specific. `/hc system` prints JVM, OS, CPU, memory, uptime, player count, and plugin directory information.

HunterTools provides TPS actionbar/sidebar display, a built-in MOTD module, and essentials-style commands such as `/heal`, `/feed`, `/fly`, `/gm`, `/day`, `/night`, `/sun`, `/rain`, `/thunder`, `/broadcast`, `/clearchat`, `/speed`, `/spawn`, `/setspawn`, `/back`, `/hat`, `/craft`, `/enderchest`, and `/trash`. The client F3 server brand defaults to `"HunterCore" Server` and can be changed from the web panel.

`/npc` creates managed Villager or Mannequin NPCs. Villagers are suitable for functional NPCs, while Mannequins are suitable for player-like display NPCs and support official Minecraft skin loading.

These lightweight actors are not real `ServerPlayer` connections, so they do not occupy player slots, load chunks, or run Carpet-style continuous use/attack/jump/sneak actions.

`/player` is the real `ServerPlayer` bot module. It joins the online player list, fires the normal join/quit flow, participates in chunk loading, supports Carpet-like continuous `use`, `attack`, and `jump` loops, and can open its inventory for direct admin editing with `/player inv <name>`. Real player bots are runtime-only and have Bukkit persistence disabled; disabling `real-fake-players` or unloading HunterTools removes them according to preferences.

## Web Panel And Map

HunterTools includes a lightweight built-in web panel. It defaults to `http://127.0.0.1:8088/`; set `modules.web-panel.bind-address` to `0.0.0.0` and change `modules.web-panel.port` to expose it.

Guests can view public status, health alerts, and the configured BlueMap URL. Logged-in player users can view detailed player/plugin data and run only `modules.web-panel.player-allowed-commands` or their per-user `allowed-commands`; admin users can run console commands when `modules.web-panel.admin-command-execution` is enabled, and can also be restricted with per-user command lists. Admin users also get Actors, Operations, and Web Users panels for spawning/removing fake players and NPCs, toggling HunterTools modules and built-in module commands, and creating/updating/removing web users with player/admin roles, command execution toggles, and allowed-command lists. The web UI blocks deleting or demoting the last password-configured admin. The `web-panel` module is self-protected from web shutdown. Web commands capture command output when possible, capped by `modules.web-panel.command-output-lines` and `modules.web-panel.command-output-chars`. Logged-in POST requests require a session CSRF token by default.

Create web users from console or an op account:

```text
/hc admin web user admin admin <password>
/hc admin web user player player <password>
/hc admin web allow player list spawn
/hc admin web execution player on
```

BlueMap is bundled for the web map, and Chunky is bundled for chunk pre-generation/performance prep. HunterCore prepares `plugins/BlueMap/core.conf` with `accept-download: true` on first startup so BlueMap can download Mojang client resources and start rendering without a manual config edit.

PlaceholderAPI, Vault, ProtocolLib, WorldEdit, and WorldGuard are bundled as a common server foundation for placeholders, economy/permission bridging, packet/protocol extensions, map editing, and region protection. HunterTools provides lightweight built-in MOTD and utility commands without bundling EssentialsX or MiniMOTD. Each bundled plugin can still be disabled under `bundled-plugins.plugins.<plugin-id>`.

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
