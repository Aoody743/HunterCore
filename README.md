# HunterCore

面向 Minecraft 26.2 的一体化高性能服务端。HunterCore 保留 Paper/Purpur 插件生态，把高性能运行时、HuntEngine 自定义内容、网页运维、认证、地图、常用服主管理能力和多代理网络接入收束为一个可发布版本。

[下载 v2.9.16-fixed](https://github.com/Aoody743/HunterCore/releases/tag/v2.9.16-fixed) | [问题反馈](https://github.com/Aoody743/HunterCore/issues) | [官网](https://core.huntmc.club)

## 亮点

- **官方 DivineMC 26.2 基线**：动态属性注册表容量，修复旧实验构建的 26.2 启动崩溃；保留 C2ME、Lithium、异步区块发送、区域化 ticking 与安全种子优化。
- **HuntEngine 内容系统**：GPL-3.0 Community Edition 派生集成，提供自定义物品、方块、家具、配方、资源包构建与游戏内目录；WebPanel 可校验、构建、发布原生内容包。
- **网页与游戏共用账号**：HunterAuth 的游戏注册账号即 WebPanel 账号，用户名和密码一致；网页角色须显式授权，不因 OP 或同名自动取得管理权。
- **完整运维工作台**：服务器健康、玩家、世界、BlueMap、插件、权限、命令、AI、内容包、迁移诊断和资源包发布均可按角色管理。
- **混合入口网络**：同一后端可同时接受直连、多个 BungeeCord 与多个 Velocity；代理玩家共享指定群组的 Tab/聊天，直连玩家只看本服玩家。
- **开箱可用组件**：BlueMap、LuckPerms、CoreProtect、WorldEdit、WorldGuard、Multiverse、Chunky、Geyser/Floodgate、ViaVersion 系列、SkinsRestorer、Vault、ProtocolLib 等均由发行物校验后内置。

## 快速开始

下载 Release 中的 `HunterCore-*-MinecraftServer-26.2-release.jar`，接受 EULA 后启动：

```bash
java -Xms2G -Xmx7.5G -XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -Dfile.encoding=UTF-8 -jar HunterCore-2.9.16-fixed-build.1-MinecraftServer-26.2-release.jar nogui
```

首次启动会准备内置插件与 `plugins/HunterCore/` 配置。WebPanel 默认仅绑定本机：`http://127.0.0.1:8088/`。

玩家在游戏内 `/register <password> <password>` 后，可用相同用户名和密码登录网页。控制台或已有管理员可授权网页角色：

```text
/hc admin web user <player> admin
```

公网部署时请保持面板绑定 `127.0.0.1`，通过 Caddy 或 Nginx 反向代理提供 HTTPS；确认 HTTPS 后将 `modules.web-panel.secure-cookies` 设置为 `true`。不要直接暴露 8088。

## HuntEngine 与资源包

使用 `/huntengine` 或 `/he` 浏览内容。HuntEngine 是资源包的唯一生命周期所有者：认证模块只在玩家资源包就绪后使用增强 GUI，拒绝或下载失败自动回退原版 GUI。

WebPanel 的 HuntEngine 工作台支持目录、草稿包上传、ZIP 安全校验、构建、发布、重载和资源包发送。发布产物使用不可变 hash 版本，失败不会替换当前生效包。旧 HunterAssets 数据会迁入 `huntercraft-legacy` 草稿包；无法可靠转换的内容会写入迁移报告。

## Network Jar：直连、BungeeCord 与 Velocity

Release 中的 `HunterCore-Network-Bungee-*.jar` 与 `HunterCore-Network-Velocity-*.jar` **只安装在代理端**。它们不是后端必需插件：不安装时后端仍可正常接受直连玩家，只是没有跨服 Tab/聊天同步。

1. 在每个 BungeeCord 或 Velocity 代理的 `plugins/` 目录放入对应 Jar 并启动一次。
2. 编辑代理生成的 `plugins/HunterCore-Network/network.properties`：为每个代理填写唯一 `node-id`，并设置共同的 `network` 名称。
3. 编辑后端 `plugins/HunterCore/proxies.yml`，为每个代理定义同名 `id`、`type: bungeecord` 或 `velocity`、仅 IP/CIDR 的 `trusted-addresses`、相同 `network`、`online-authenticated`；Velocity 还必须填写与代理 forwarding 一致的 `secret`。
4. 重启代理与后端。不要把代理信任地址写成 `0.0.0.0/0`。

行为规则：代理接入玩家会看到其 `network` 内的远程玩家和聊天，也会看到本后端的直连玩家；直连玩家会看到本后端全部玩家（包含从代理进入本服的人），但不会收到其他后端的群组 Tab 条目或聊天。多个 BungeeCord 与 Velocity 可以同时接入同一后端，只要节点 ID 唯一且信任边界准确。

HunterAuth 默认对直连离线玩家要求密码；正版直连和可信代理转发玩家绕过密码流程。登录或验证后会请求 SkinsRestorer，查不到正版皮肤时回退 Steve。

## 运维与管理

- `/hc admin modules`、`/hc admin optimize`、`/hc admin web status` 管理核心模块、优化与网页服务。
- `/player` 提供真实 ServerPlayer 测试假人；`/npc` 提供交互 NPC。
- `/huntengine`、`/he` 管理内容；旧 `/hunterassets`、`/ha`、`/hassets` 在 2.9.x 仅提供迁移提示。
- `preferences.yml` 集中管理内置模块；`proxies.yml` 管理入口信任；WebPanel 的危险操作继续要求角色、CSRF 和确认。

## 发行物与校验

Release 包含主服务端 Jar、WebPanel ZIP、Bungee/Velocity Network Jar 和 `SHA256SUMS.txt`。下载后校验：

```bash
shasum -a 256 HunterCore-*-MinecraftServer-26.2-release.jar
```

## 从源码构建

需要 Java 25、Git、Bash、curl、unzip、tar、perl。HuntEngine 独立构建，HunterCore 不使用 Gradle composite build：

```bash
(cd third-party/hunt-engine && ./gradlew assembleHuntEngine --no-daemon)
bash scripts/verify-hunt-engine-vendor.sh third-party/hunt-engine/target/HuntEngine.jar
./gradlew packageHunterCoreRelease packageHunterCoreWebPanel verifyHunterCoreRelease --no-daemon --no-configuration-cache
```

## 许可证

HunterCore 及其服务器上游适用 GPL-3.0 要求，详见 [LICENSE](LICENSE)。HuntEngine 的上游、提交、变更和源码获取说明位于项目的第三方合规文件中。
