# HunterCore Notes

HunterCore is an independent DivineMC-based server core with a bundled plugin layer, a preferences file, HunterTools runtime modules, and a small extension API.

## Build

Use the same Paperweight flow as DivineMC:

```bash
GIT_CONFIG_COUNT=1 \
GIT_CONFIG_KEY_0=url.git@github.com:.insteadOf \
GIT_CONFIG_VALUE_0=https://github.com/ \
./gradlew applyAllPatches createPaperclipJar --no-daemon
```

The final runnable jar is generated at:

```text
divinemc-server/build/libs/divinemc-paperclip-26.1.2.local-SNAPSHOT.jar
```

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
/huntercore
/huntercore system
/huntercore plugins
/huntercore preferences
/huntercore preferences bundled <plugin-id> <on|off>
/huntercore preferences module <module> <on|off>
/huntercore preferences command <essentials|management> <command> <on|off>
/huntercore reload
/hc
/htps
/hunteradmin modules
/hunteradmin module <module> <on|off>
/hunteradmin command <essentials|management> <command> <on|off>
/hunteradmin memory
/hunteradmin gc
/hunteradmin threads
/hunteradmin optimize
/fakeplayer spawn <name> [world x y z [yaw pitch]]
/fakeplayer remove <name>
/fakeplayer list
/fakeplayer tp <name> [world x y z [yaw pitch]]
/fakeplayer clear
/npc spawn <name> [villager|mannequin] [world x y z [yaw pitch]]
/npc remove <name>
/npc list
/npc tp <name> [world x y z [yaw pitch]]
/npc clear
```

`/about` is HunterCore-specific. `/huntercore system` prints JVM, OS, CPU, memory, uptime, player count, and plugin directory information.

HunterTools provides TPS actionbar/sidebar display plus essentials-style commands such as `/heal`, `/feed`, `/fly`, `/gm`, `/day`, `/night`, `/sun`, `/rain`, `/thunder`, `/broadcast`, `/clearchat`, `/speed`, `/spawn`, `/setspawn`, and `/back`.

`/fakeplayer` creates lightweight Mannequin-based fake players. `/npc` creates managed Villager or Mannequin NPCs. Both are persisted in `plugins/HunterCore/preferences.yml` and rebuilt on startup/reload.

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
optimizations.cpu.enabled
optimizations.cpu.paper-worker-threads
optimizations.cpu.divine-worker-threads
optimizations.cpu.netty-io-threads
optimizations.cpu.common-pool-parallelism
```

Bundled plugin install work is parallelized across different jar files. HunterTools renders sidebar text, loads fake player/NPC definitions, saves preferences, and requests GC off the main thread, then returns to the Bukkit main thread for player/server mutations.

HunterCore also applies CPU-aware startup defaults for Paper/DivineMC worker threads, Netty IO threads, and ForkJoin common pool parallelism. Existing JVM flags are preserved by default.

## API

The API entrypoint is:

```java
org.huntercore.api.HunterCoreProvider.get()
```

Plugins can register future `/huntercore` subcommands with:

```java
HunterCoreProvider.get().registerCommandExtension(extension);
```

The extension interface is `org.huntercore.api.HunterCommandExtension`.
