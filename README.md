# HunterCore

HunterCore 是一个为 Minecraft 服主准备的高性能自定义服务器核心。它保留熟悉的 Bukkit、Spigot、Paper 插件兼容体验，同时把网页管理、地图入口、权限快捷管理、假人调试、常用服主工具和一批基础插件直接整合进核心。

简单说：你下载一个 jar，就能得到一个更适合开服、调试、运营和远程管理的服务器核心。

- [下载最新版本](https://github.com/Aoody743/HunterCore/releases)
- [查看源码](https://github.com/Aoody743/HunterCore)

## 为什么选择 HunterCore

开服最烦的事情，往往不是把服务器跑起来，而是把一堆基础插件、权限、地图、网页、管理命令、假人测试工具、MOTD、性能参数一点点拼起来。HunterCore 想解决的就是这件事。

- 开箱即用：内置 ViaVersion、BlueMap、LuckPerms、CoreProtect、WorldEdit、WorldGuard、Multiverse、Chunky 等常用基础能力。
- 自带网页面板：打开浏览器就能看地图、服务器状态、玩家、世界、插件、命令输出、健康告警和管理入口。
- 管理更直观：网页端支持插件启用、停用、重载和从 URL 更新插件，也支持模块开关、命令开关和网页用户权限管理。
- 假人更像真玩家：`/player` 提供真实 `ServerPlayer` Bot，可以进入在线列表，支持背包编辑、持续右键、左键、跳跃、潜行、疾跑等调试动作。
- 不堆重复插件：MOTD、基础传送、常用生存服命令和管理命令由 HunterTools 自研提供，不默认塞 EssentialsX、MiniMOTD、GSit 这类重叠插件。
- 远程管理友好：网页端口、绑定地址、服务器名称、地图地址都能在网页或游戏内调整。
- 品牌更统一：客户端 F3 里的服务端名称默认显示为 `"HunterCore" Server`，也可以在网页面板里改成自己的品牌名。
- 配置集中：主要内置模块和开关统一放在 `plugins/HunterCore/preferences.yml`，不用在一堆插件配置里反复翻。

## 适合谁

HunterCore 很适合下面这些场景：

- 想快速搭一个生存服、建筑服、朋友服、测试服。
- 想要 BlueMap 地图和网页后台，但不想自己从零拼。
- 想在网页上看插件状态、执行允许的命令、做简单运维。
- 想测试红石、农场、刷怪塔、交互指令，需要 Carpet 风格假人能力。
- 想保留 Paper/Purpur 插件生态，又希望核心里有更多服主常用功能。

## 快速开始

从 [GitHub Releases](https://github.com/Aoody743/HunterCore/releases) 下载最新的：

```text
HunterCore-<version>-MinecraftServer-<mcVersion>-release.jar
```

然后启动：

```bash
java -Xms2G -Xmx4G -jar HunterCore-<version>-MinecraftServer-<mcVersion>-release.jar nogui
```

首次启动会生成 EULA 和配置文件。接受 Minecraft EULA 后再次启动即可。

如果你要开启网页管理账号，进控制台输入：

```text
/hc admin web user admin admin <你的密码>
```

默认网页面板地址：

```text
http://127.0.0.1:8088/
```

需要对局域网或公网开放时，可以在游戏内或控制台调整：

```text
/hc admin web bind 0.0.0.0
/hc admin web port 8088
/hc admin web restart
```

## 网页面板

HunterCore 的网页面板不是一个简单状态页，而是面向服主日常运维做的控制台。界面采用 Apple 风格的液态玻璃视觉，首页优先展示 BlueMap 或你配置的地图，登录后再按权限显示不同内容。

访客可以看到：

- 服务器基础状态。
- TPS、MSPT、在线人数、内存信息。
- 健康告警。
- BlueMap 地图入口。

普通玩家登录后可以看到：

- 更完整的服务器信息。
- 玩家和世界概览。
- 允许范围内的命令执行入口。

管理员登录后可以看到：

- 插件列表和插件状态。
- 插件启用、停用、重载。
- 从 URL 下载 jar 并更新插件。
- LuckPerms 用户、组和权限快捷操作。
- 假人、NPC、真实假人生成和移除。
- 假人点击执行命令配置。
- HunterTools 模块和命令开关。
- `/about`、`/plugins`、`/op` 无权限提示的自定义文案，支持 `&` 颜色和样式代码。
- 网页用户、角色、允许命令和命令执行权限管理。
- 网页端口、绑定地址、地图地址、服务器名称设置。
- 原生 AI 接入设置：OpenAI 兼容 Base URL、模型、API key/env、聊天触发词、NPC Prompt、NPC 命令白名单和在线测试。

网页端会读取根目录的 `server-icon.png` 作为服务器标志。面板支持中文和英文切换，会根据浏览器语言自动选择，也可以手动切换。

提示：Minecraft 插件的热启停、热重载和热更新取决于插件自身是否安全支持。HunterCore 提供这个能力是为了让调试和维护更方便，但正式服更新关键插件前仍建议先测试。面板会保护承载网页面板的核心插件，避免把自己关掉。

## 原生 AI 接入

HunterCore 现在自带 AI 接入系统，不需要额外写插件就能把 ChatGPT 或其他 OpenAI-compatible 服务接进服务器。

- 聊天栏 AI：玩家在聊天里提到已配置的 AI 名字，服务器会调用对应模型结合上下文回复。
- NPC AI：NPC 没有点击指令时，可以直接由 AI 回复玩家，还能执行安全白名单动作。
- 网页管理：管理员可以在后台配置 Base URL、模型、API key、环境变量、Prompt、冷却时间、NPC 可见半径和命令白名单。
- 灵活接入：默认兼容 OpenAI 的 `/v1/chat/completions`，也可以换成支持同协议的第三方或自建模型网关。
- 安全默认：AI 模块默认关闭，API key 不会回显到网页；NPC 只能执行白名单命令。

快速启用：

```text
/hc admin ai key <你的 API key>
/hc admin ai model gpt-4o-mini
/hc admin ai enable
```

如果你更喜欢用环境变量，可以设置 `OPENAI_API_KEY`，或者在网页后台修改 `api-key-env`。

## 剧情模式

HunterTools 内置了一个实验性的 Story Mode，用于演示真实假人、AI 行为和阶段式事件编排。它默认不开放，首次启动生成的 `plugins/HunterCore/preferences.yml` 会把 `modules.story-mode.enabled` 设为 `false`，普通服务器不会自动出现剧情假人或失控事件。

管理员可以在测试服或拍摄环境中手动启用：

```text
/story enable
/start
```

`/story disable` 会关闭剧情模式并清理正在运行的剧情假人。这个功能依赖真实假人和 AI 配置，建议只在受控环境里开启，不建议作为公开服默认玩法开放。

## 地图和 BlueMap

HunterCore 内置 BlueMap。网页面板默认会把地图地址指向：

```text
http://%host%:8100/
```

如果你把 BlueMap 改到别的端口，或者想接入其他网页地图，可以在网页后台或配置里改 `map-url`。

BlueMap 首次运行需要你在：

```text
plugins/BlueMap/core.conf
```

确认资源下载选项。BlueMap 会下载 Mojang 客户端资源用于地图渲染，这是 BlueMap 的正常流程。

## 假人、NPC 和交互玩法

HunterCore 现在有三类可控实体，适合不同场景：

- `/npc`：支持 villager 和 mannequin 类型，适合做功能 NPC、传送 NPC、菜单 NPC；mannequin 可加载正版玩家皮肤。
- `/player`：真实 `ServerPlayer` Bot，适合红石、农场、刷怪、区块加载、背包编辑和玩家行为调试。

真实假人支持：

```text
/player spawn <name>
/player remove <name>
/player inv <name>
/player skin <name> <minecraftName|clear>
/player tp <name>
/player tphere <name>
/player sneak <name> <on|off>
/player sprint <name> <on|off>
/player jump <name> [once|continuous|stop]
/player use <name> [once|continuous|stop]
/player attack <name> [once|continuous|stop]
/player stop <name>
/player drop <name>
/player dropstack <name>
/player swap <name>
/player gm <name> <survival|creative|adventure|spectator>
/player slot <name> <1-9>
```

点击命令也支持在游戏内或网页端配置：

```text
/player click <name> say %player% clicked %actor%
/npc click <name> lp user %player% permission set example.node true
```

可用占位符包括：

```text
%player%
%player_uuid%
%actor%
%actor_name%
%actor_uuid%
%module%
%world%
%x%
%y%
%z%
```

## 自研服主工具

HunterTools 内置了一组轻量实用功能，覆盖很多小服和测试服每天都会用到的操作：

```text
/htps
/hc admin modules
/hc admin module <module> <on|off>
/hc admin command <module> <command> <on|off>
/hc admin memory
/hc admin gc
/hc admin threads
/hc admin optimize
/hc admin motd <status|line1|line2|max>
/hc admin web <status|restart|bind|port|map|public-map|user|remove|users|allow|execution>
/heal [player]
/feed [player]
/fly [player] [on|off]
/gm <mode> [player]
/day [world]
/night [world]
/sun [world]
/rain [world]
/thunder [world]
/broadcast <message>
/clearchat
/speed <1-10> [player] [walk|fly]
/spawn [player]
/setspawn
/back
/hat
/craft
/enderchest [player]
/trash
/start
/story <start|enable|disable|status|skip|stop|line|meltdown>
```

这些功能可以通过 `preferences.yml` 或 `/hc admin` 开关。你可以只保留自己需要的部分，把不用的模块关掉。

## 内置插件

HunterCore 会在服务器扫描插件目录前准备内置插件。首次启动后会生成：

```text
plugins/HunterCore/preferences.yml
```

当前内置插件包括：

```text
ViaVersion 5.10.0
ViaBackwards 5.10.0
ViaRewind 4.1.2
BlueMap 5.22
Chunky 1.5.3
PlaceholderAPI 2.12.2
Vault 1.7.3
ProtocolLib 5.4.0
WorldEdit 7.4.3
WorldGuard 7.0.17
Multiverse-Core 5.7.1
LuckPerms 5.5.58
CoreProtect 23.2
HunterTPA builtin
HunterAuth builtin
HunterTools builtin
```

外部内置插件可以在 `bundled-plugins.plugins.<plugin-id>` 里单独关闭。关闭已经加载的插件通常仍需要重启服务器，网页端热操作适合调试和插件自身支持热重载的场景。

## 性能和优化

HunterCore 继承成熟 Minecraft 服务端生态的优化基础，并额外加入一组偏保守、适合开服默认使用的设置：

- 按 CPU 自动设置 Paper / core worker threads。
- 自动设置 Netty IO threads 和 ForkJoin common pool parallelism。
- 内置插件并行准备，减少首次启动等待。
- HunterTools 异步渲染、异步保存、玩家缓存。
- 假人和 NPC 配置异步加载、批量保存。
- 网页面板使用独立 worker，游客状态接口带缓存。
- 健康告警监测 TPS、MSPT、堆内存、区块、实体和禁用插件。

如果你已经用 JVM 参数手动指定线程相关设置，HunterCore 会尊重你的配置，不会强行覆盖。

## 常用配置

主配置文件：

```text
plugins/HunterCore/preferences.yml
```

网页面板相关配置示例：

```yaml
modules:
  web-panel:
    enabled: true
    bind-address: 127.0.0.1
    port: 8088
    server-name: HunterCore
    public-map: true
    map-url: http://%host%:8100/
    require-csrf: true
    command-output-lines: 80
    command-output-chars: 12000
```

常用游戏内管理命令：

```text
/hc admin web status
/hc admin web bind <address>
/hc admin web port <1-65535>
/hc admin web map <url>
/hc admin web public-map <on|off>
/hc admin web user <name> <admin|player> <password>
/hc admin web allow <name> <inherit|none|*|command...>
/hc admin web execution <name> <on|off>
```

## 下载、发布和校验

推荐始终从 [Releases](https://github.com/Aoody743/HunterCore/releases) 下载 `HunterCore-*-MinecraftServer-*-release.jar`。

每个发布版本都会附带：

```text
HunterCore-<version>-MinecraftServer-<mcVersion>-release.jar
HunterCore-<version>-WebPanel-<mcVersion>-release.zip
```

发行 jar 默认控制在 100MB 以内，并保留 Linux、macOS、Windows 的 x86_64/aarch64 常见原生库；SQLite 额外保留 Linux-Musl x86_64/aarch64。非常规架构可以从源码构建未瘦身的 `divinemc-paperclip` jar。

如果你要检查文件完整性：

```bash
shasum -a 256 HunterCore-<version>-MinecraftServer-<mcVersion>-release.jar
```

## 从源码构建

需要 Java 25。

```bash
GIT_CONFIG_COUNT=1 \
GIT_CONFIG_KEY_0=url.git@github.com:.insteadOf \
GIT_CONFIG_VALUE_0=https://github.com/ \
./gradlew packageHunterCoreRelease --no-daemon --no-configuration-cache
```

构建产物会生成在：

```text
divinemc-server/build/libs/
```

可直接发布的 HunterCore jar 会生成在：

```text
divinemc-server/build/libs/HunterCore-1.5.0-build.1-MinecraftServer-26.1.2-release.jar
```

如果你需要未瘦身的通用 paperclip jar，也可以单独运行 `./gradlew :divinemc-server:createPaperclipJar`，产物是 `divinemc-server/build/libs/divinemc-paperclip-<mcVersion>.local-SNAPSHOT.jar`。

## 开发者 API

HunterCore 提供 API 入口：

```java
org.huntercore.api.HunterCoreProvider.get()
```

插件可以注册 `/hc` 子命令扩展：

```java
HunterCoreProvider.get().registerCommandExtension(extension);
```

扩展接口：

```java
org.huntercore.api.HunterCommandExtension
```

## 上游和许可证

HunterCore 继承 Minecraft 服务端生态相关项目的 GPL-3.0 许可证要求。详见 [LICENSE](LICENSE)。

感谢 Purpur、Paper、Pufferfish、Leaves、SparklyPaper 等项目为 Minecraft 服务端生态做出的长期贡献。
