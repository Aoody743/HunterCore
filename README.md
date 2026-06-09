# HunterCore

HunterCore 是一个基于 [DivineMC](https://github.com/BX-Team/DivineMC) 的独立 Minecraft 服务器核心。它保留 DivineMC / Purpur / Paper 的插件生态兼容性，同时加入 HunterCore 自己的内置插件层、扩展 API 和服务器管理命令。

这个仓库已经独立于 GitHub fork network。上游同步通过 `upstream` remote、手动检查工作流和分支合并完成，不依赖 GitHub fork 关系。

## 项目定位

- 基于 DivineMC、Purpur 和 Paper 的高性能服务端核心。
- 默认内置一批常用插件，首启前安装到 `plugins/`，减少开服时的重复配置。
- 提供 HunterCore API，方便后续内置模块或外部插件扩展 `/huntercore` 命令。
- 保持独立仓库发布节奏，同时保留从 DivineMC 上游同步的能力。

## 当前内置插件

HunterCore 会在 Paper 扫描插件目录之前准备内置插件。首次启动后会生成：

```text
plugins/HunterCore/bundled-plugins.yml
```

这个配置可以关闭整个内置插件安装器、关闭单个插件，或禁止自动替换已变化的内置 jar。

当前内置插件：

```text
ViaVersion 5.9.1
Multiverse-Core 5.7.0
LuckPerms 5.5.55
CoreProtect 23.2
HunterTPA builtin
HunterAuth builtin
```

外部内置插件由脚本准备：

```bash
scripts/prepare-bundled-plugins.sh
```

内置自研插件放在 `huntercore-plugins/` 下。

## 命令

```text
/about
/huntercore
/huntercore system
/huntercore plugins
/huntercore reload
/hc
```

`/about` 会显示 HunterCore 信息。`/huntercore system` 会输出 JVM、系统、CPU、内存、运行时间、在线玩家数量和插件目录信息。

## 构建

需要 Java 25。

```bash
GIT_CONFIG_COUNT=1 \
GIT_CONFIG_KEY_0=url.git@github.com:.insteadOf \
GIT_CONFIG_VALUE_0=https://github.com/ \
./gradlew applyAllPatches createPaperclipJar --no-daemon
```

构建产物会生成在：

```text
divinemc-server/build/libs/
```

可直接运行的 paperclip jar 通常类似：

```text
divinemc-server/build/libs/divinemc-paperclip-26.1.2.local-SNAPSHOT.jar
```

GitHub Actions 会在发布时把它重命名为 `HunterCore-<version>-paperclip.jar` 作为 release asset。

## 下载和运行

推荐从 [GitHub Releases](https://github.com/AndyXeCM/HunterCore/releases) 下载 `HunterCore-*-paperclip.jar`。

```bash
java -Xms2G -Xmx4G -jar HunterCore-26.1.2-huntercore.1-paperclip.jar nogui
```

首次启动会生成 EULA 和配置文件。接受 Minecraft EULA 后再次启动即可。

## 发布版本

发布使用 `.github/workflows/release.yml`。

通过 tag 发布：

```bash
git tag v26.1.2-huntercore.1
git push origin v26.1.2-huntercore.1
```

也可以在 GitHub Actions 页面手动运行 `Release HunterCore`，输入版本号，例如：

```text
v26.1.2-huntercore.1
```

工作流会构建 paperclip jar，生成 SHA256 校验文件，并创建或更新 GitHub Release。

## 同步 DivineMC 上游

本地保留只读上游 remote：

```bash
git remote -v
# upstream  git@github.com:BX-Team/DivineMC.git (fetch)
# upstream  DISABLED (push)
```

推荐同步流程：

```bash
git fetch upstream
git checkout main
git checkout -b codex/sync-divinemc-YYYYMMDD
git merge upstream/ver/26.1.2
./gradlew applyAllPatches createPaperclipJar --no-daemon
```

如果 DivineMC 的当前维护分支变化，更新上面的 `upstream/ver/26.1.2` 即可。仓库也提供 `Check DivineMC Upstream` 手动工作流，用来查看上游差异并执行 dry-run merge。

## HunterCore API

API 入口：

```java
org.huntercore.api.HunterCoreProvider.get()
```

插件可以注册未来的 `/huntercore` 子命令扩展：

```java
HunterCoreProvider.get().registerCommandExtension(extension);
```

扩展接口：

```java
org.huntercore.api.HunterCommandExtension
```

## 许可证和致谢

HunterCore 继承 DivineMC / Paper 系列项目的 GPL-3.0 许可证要求。详见 [LICENSE](LICENSE)。

HunterCore 基于 DivineMC 构建，DivineMC 本身包含来自 Purpur、Paper、Pufferfish、Leaves、SparklyPaper 等项目的补丁和优化。感谢这些项目为 Minecraft 服务端生态做出的工作。
