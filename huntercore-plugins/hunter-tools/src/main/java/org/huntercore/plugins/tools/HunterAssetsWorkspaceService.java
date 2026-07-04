package org.huntercore.plugins.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class HunterAssetsWorkspaceService {
    private final HunterToolsPlugin plugin;
    private final HunterToolsPreferences preferences;

    HunterAssetsWorkspaceService(final HunterToolsPlugin plugin, final HunterToolsPreferences preferences) {
        this.plugin = plugin;
        this.preferences = preferences;
    }

    Path root() {
        return Bukkit.getPluginsFolder().toPath().resolve("HunterAssets");
    }

    Path packsDirectory() {
        return this.root().resolve("packs");
    }

    Path itemsDirectory() {
        return this.root().resolve("items");
    }

    Path imagesDirectory() {
        return this.root().resolve("images");
    }

    Path presetsDirectory() {
        return this.root().resolve("presets");
    }

    Path legacyConfigPath() {
        return this.root().resolve("config.yml");
    }

    void ensureLayout() throws IOException {
        Files.createDirectories(this.root());
        Files.createDirectories(this.packsDirectory());
        Files.createDirectories(this.itemsDirectory());
        Files.createDirectories(this.imagesDirectory());
        Files.createDirectories(this.presetsDirectory());
    }

    List<AssetItemDefinition> loadItems() {
        final Map<String, AssetItemDefinition> items = new LinkedHashMap<>();
        final YamlConfiguration legacy = YamlConfiguration.loadConfiguration(this.legacyConfigPath().toFile());
        final ConfigurationSection legacySection = legacy.getConfigurationSection("items");
        if (legacySection != null) {
            for (final String id : legacySection.getKeys(false)) {
                final ConfigurationSection section = legacySection.getConfigurationSection(id);
                if (section != null) {
                    items.put(HunterToolsPreferences.normalize(id), readItem(id, section));
                }
            }
        }
        try {
            this.ensureLayout();
            if (Files.isDirectory(this.itemsDirectory())) {
                try (var stream = Files.list(this.itemsDirectory())) {
                    for (final Path path : stream.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                        .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                        .toList()) {
                        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
                        final String id = HunterToolsPreferences.normalize(yaml.getString("id", path.getFileName().toString().replaceFirst("\\.yml$", "")));
                        items.put(id, readItem(id, yaml));
                    }
                }
            }
        } catch (final IOException ex) {
            this.plugin.getLogger().warning("Failed to scan HunterAssets items: " + ex.getMessage());
        }
        return new ArrayList<>(items.values());
    }

    AssetItemDefinition saveItem(final AssetItemDefinition item) throws IOException {
        this.ensureLayout();
        final String id = HunterToolsPreferences.normalize(item.id());
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", id);
        yaml.set("enabled", item.enabled());
        yaml.set("category", item.category());
        yaml.set("material", item.material());
        yaml.set("amount", item.amount());
        yaml.set("custom-model-data", item.customModelData());
        yaml.set("permission", item.permission());
        yaml.set("pack", item.pack());
        yaml.set("icon", item.icon());
        yaml.set("description", item.description());
        yaml.set("name-zh-cn", item.nameZhCn());
        yaml.set("name-en-us", item.nameEnUs());
        yaml.set("lore-zh-cn", item.loreZhCn());
        yaml.set("lore-en-us", item.loreEnUs());
        final Path target = this.itemsDirectory().resolve(id + ".yml");
        yaml.save(target.toFile());
        return new AssetItemDefinition(
            id,
            item.enabled(),
            item.category(),
            item.material(),
            item.amount(),
            item.customModelData(),
            item.permission(),
            item.pack(),
            item.icon(),
            item.description(),
            item.nameZhCn(),
            item.nameEnUs(),
            item.loreZhCn(),
            item.loreEnUs()
        );
    }

    boolean removeItem(final String id) throws IOException {
        this.ensureLayout();
        final Path target = this.itemsDirectory().resolve(HunterToolsPreferences.normalize(id) + ".yml");
        return Files.deleteIfExists(target);
    }

    UploadedAsset upload(final String scope, final String fileName, final String base64) throws IOException {
        this.ensureLayout();
        final byte[] bytes = Base64.getDecoder().decode(base64);
        final String safeName = sanitizeFileName(fileName);
        final Path directory = switch (HunterToolsPreferences.normalize(scope)) {
            case "pack", "packs", "resource-pack" -> this.packsDirectory();
            case "image", "images" -> this.imagesDirectory();
            default -> this.presetsDirectory();
        };
        final Path target = uniqueTarget(directory.resolve(safeName));
        Files.write(target, bytes);
        return new UploadedAsset(target.getFileName().toString(), target, bytes.length, sha1Hex(bytes));
    }

    AssetPromptBundle generatePrompt(final PromptRequest request, final String language) {
        final boolean zh = language != null && language.toLowerCase(Locale.ROOT).startsWith("zh");
        final String subject = String.join(" ", List.of(request.category(), request.useCase(), request.theme()).stream()
            .filter(value -> value != null && !value.isBlank()).toList());
        final String style = request.style().isBlank() ? (zh ? "Minecraft 像素风" : "Minecraft pixel-art style") : request.style();
        final String color = request.colorPalette().isBlank() ? (zh ? "高识别度配色" : "high-contrast palette") : request.colorPalette();
        final String transparency = request.transparentBackground() ? (zh ? "透明背景" : "transparent background") : (zh ? "纯色背景" : "solid background");
        final String modelPrompt = zh
            ? "请为 Minecraft 资源包生成 " + subject + " 的贴图设计，风格为 " + style + "，主题是 " + request.theme()
                + "，材质感偏 " + request.materialFeel() + "，配色采用 " + color + "，尺寸 " + request.resolution()
                + "，" + transparency + "，保留 Minecraft 可读性，避免复杂摄影背景。"
            : "Create a Minecraft resource-pack texture for " + subject + " in " + style + ", themed around " + request.theme()
                + ", with a " + request.materialFeel() + " material feel, using " + color + ", resolution " + request.resolution()
                + ", " + transparency + ", and strong Minecraft readability without a noisy background.";
        final String uiPrompt = zh
            ? "请设计与 " + subject + " 配套的 Minecraft 风格 UI 图标或面板元素，风格为 " + style + "，主题 " + request.theme()
                + "，颜色 " + color + "，适合服务器网页管理界面与资源包菜单展示。"
            : "Design a matching Minecraft-style UI icon or panel element for " + subject + " using " + style + ", theme " + request.theme()
                + ", and colors " + color + ", suitable for a server web admin UI and resource-pack menus.";
        final List<String> steps = zh
            ? List.of(
                "1. 把生成的 PNG 放进 HunterAssets 资源包目录。",
                "2. 在网页 Assets 工作台里填写物品名称、基础材质和 custom-model-data。",
                "3. 绑定资源包文件后执行部署检查。",
                "4. 发布资源包并向测试玩家发送。"
            )
            : List.of(
                "1. Put the generated PNG into the HunterAssets resource-pack files.",
                "2. Fill in item name, base material, and custom-model-data in the Assets workbench.",
                "3. Run deployment validation after linking the resource pack.",
                "4. Publish the pack and send it to a test player."
            );
        final String example = zh
            ? "物品定义建议: id=" + HunterToolsPreferences.normalize(request.useCase()) + ", category=" + request.category() + ", theme=" + request.theme()
            : "Suggested item definition: id=" + HunterToolsPreferences.normalize(request.useCase()) + ", category=" + request.category() + ", theme=" + request.theme();
        return new AssetPromptBundle(modelPrompt, uiPrompt, steps, example);
    }

    ValidationResult validate() {
        final List<AssetItemDefinition> items = this.loadItems();
        final Set<Integer> seenModelIds = new TreeSet<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final Set<String> packs = new TreeSet<>();
        try {
            this.ensureLayout();
            try (var stream = Files.list(this.packsDirectory())) {
                for (final Path file : stream.filter(Files::isRegularFile).toList()) {
                    packs.add(file.getFileName().toString());
                }
            }
        } catch (final IOException ex) {
            errors.add("packs directory unavailable: " + ex.getMessage());
        }
        for (final AssetItemDefinition item : items) {
            if (item.customModelData() > 0 && !seenModelIds.add(item.customModelData())) {
                errors.add("custom-model-data conflict: " + item.customModelData());
            }
            if (item.material().isBlank() || Material.matchMaterial(item.material()) == null) {
                errors.add("invalid material for item " + item.id());
            }
            if (item.pack() != null && !item.pack().isBlank() && !packs.contains(item.pack())) {
                warnings.add("missing pack file for item " + item.id() + ": " + item.pack());
            }
            if (item.nameZhCn().isBlank() || item.nameEnUs().isBlank()) {
                warnings.add("item " + item.id() + " is missing bilingual display names");
            }
        }
        final String configuredUrl = hunterAssetsValue("resource-pack.url", "");
        if (!configuredUrl.isBlank() && !configuredUrl.startsWith("http")) {
            warnings.add("resource-pack.url is not a public http(s) URL");
        }
        return new ValidationResult(items.size(), packs.size(), errors, warnings);
    }

    PublishedPack publishSelectedPack(final String fileName, final boolean required, final boolean sendOnJoin, final String externalBaseUrl) throws IOException {
        this.ensureLayout();
        final Path file = this.packsDirectory().resolve(sanitizeFileName(fileName));
        if (!Files.isRegularFile(file)) {
            throw new IOException("Pack file not found: " + fileName);
        }
        final byte[] bytes = Files.readAllBytes(file);
        final String sha1 = sha1Hex(bytes);
        final String base = normalizeBaseUrl(externalBaseUrl.isBlank() ? this.preferences.stringValue("modules.web-panel.external-url", "") : externalBaseUrl);
        final String publicBase = base.isBlank() ? normalizeBaseUrl(this.plugin.webPanelAddress()) : base;
        final String url = publicBase + "/api/assets/download/" + file.getFileName();
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.legacyConfigPath().toFile());
        yaml.set("resource-pack.enabled", true);
        yaml.set("resource-pack.required", required);
        yaml.set("resource-pack.send-on-join", sendOnJoin);
        yaml.set("resource-pack.url", url);
        yaml.set("resource-pack.sha1", sha1);
        yaml.save(this.legacyConfigPath().toFile());
        return new PublishedPack(file.getFileName().toString(), url, sha1, required, sendOnJoin);
    }

    String summaryJson(final String language) {
        final List<AssetItemDefinition> items = this.loadItems();
        final ValidationResult validation = this.validate();
        final StringBuilder json = new StringBuilder(4096);
        json.append('{');
        json.append("\"root\":").append(string(this.root().toString())).append(',');
        json.append("\"resourcePack\":{");
        json.append("\"enabled\":").append(hunterAssetsBoolean("resource-pack.enabled", false)).append(',');
        json.append("\"required\":").append(hunterAssetsBoolean("resource-pack.required", false)).append(',');
        json.append("\"sendOnJoin\":").append(hunterAssetsBoolean("resource-pack.send-on-join", false)).append(',');
        json.append("\"url\":").append(string(hunterAssetsValue("resource-pack.url", ""))).append(',');
        json.append("\"sha1\":").append(string(hunterAssetsValue("resource-pack.sha1", "")));
        json.append("},");
        json.append("\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            final AssetItemDefinition item = items.get(i);
            json.append('{');
            json.append("\"id\":").append(string(item.id())).append(',');
            json.append("\"enabled\":").append(item.enabled()).append(',');
            json.append("\"category\":").append(string(item.category())).append(',');
            json.append("\"material\":").append(string(item.material())).append(',');
            json.append("\"amount\":").append(item.amount()).append(',');
            json.append("\"customModelData\":").append(item.customModelData()).append(',');
            json.append("\"permission\":").append(string(item.permission())).append(',');
            json.append("\"pack\":").append(string(item.pack())).append(',');
            json.append("\"icon\":").append(string(item.icon())).append(',');
            json.append("\"description\":").append(string(item.description())).append(',');
            json.append("\"name\":").append(string(language != null && language.toLowerCase(Locale.ROOT).startsWith("zh") ? item.nameZhCn() : item.nameEnUs()));
            json.append('}');
        }
        json.append("],\"packs\":").append(directoryListJson(this.packsDirectory())).append(',');
        json.append("\"images\":").append(directoryListJson(this.imagesDirectory())).append(',');
        json.append("\"presets\":").append(directoryListJson(this.presetsDirectory())).append(',');
        json.append("\"validation\":").append(validation.toJson());
        json.append('}');
        return json.toString();
    }

    byte[] downloadPack(final String fileName) throws IOException {
        return Files.readAllBytes(this.packsDirectory().resolve(sanitizeFileName(fileName)));
    }

    private AssetItemDefinition readItem(final String id, final ConfigurationSection section) {
        return new AssetItemDefinition(
            HunterToolsPreferences.normalize(id),
            section.getBoolean("enabled", true),
            section.getString("category", "items"),
            section.getString("material", "PAPER"),
            Math.max(1, section.getInt("amount", 1)),
            Math.max(0, section.getInt("custom-model-data", 0)),
            section.getString("permission", ""),
            section.getString("pack", ""),
            section.getString("icon", ""),
            section.getString("description", ""),
            section.getString("name-zh-cn", id),
            section.getString("name-en-us", id),
            section.getStringList("lore-zh-cn"),
            section.getStringList("lore-en-us")
        );
    }

    private boolean hunterAssetsBoolean(final String path, final boolean fallback) {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.legacyConfigPath().toFile());
        return yaml.getBoolean(path, fallback);
    }

    private String hunterAssetsValue(final String path, final String fallback) {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.legacyConfigPath().toFile());
        return yaml.getString(path, fallback);
    }

    private static String normalizeBaseUrl(final String value) {
        return Objects.requireNonNullElse(value, "").trim().replaceAll("/+$", "");
    }

    private static String directoryListJson(final Path directory) {
        final StringBuilder json = new StringBuilder("[");
        try {
            if (Files.isDirectory(directory)) {
                boolean first = true;
                for (final Path file : Files.list(directory).filter(Files::isRegularFile).sorted().toList()) {
                    if (!first) {
                        json.append(',');
                    }
                    first = false;
                    json.append('{')
                        .append("\"name\":").append(string(file.getFileName().toString())).append(',')
                        .append("\"size\":").append(file.toFile().length())
                        .append('}');
                }
            }
        } catch (final IOException ignored) {
        }
        json.append(']');
        return json.toString();
    }

    private static Path uniqueTarget(final Path target) throws IOException {
        if (!Files.exists(target)) {
            return target;
        }
        final String fileName = target.getFileName().toString();
        final int dot = fileName.lastIndexOf('.');
        final String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
        final String ext = dot >= 0 ? fileName.substring(dot) : "";
        int index = 2;
        Path candidate;
        do {
            candidate = target.resolveSibling(base + "-" + index++ + ext);
        } while (Files.exists(candidate));
        return candidate;
    }

    private static String sanitizeFileName(final String value) {
        return value.replace("\\", "_").replace("/", "_").replace("..", "_").trim();
    }

    private static String sha1Hex(final byte[] bytes) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            final byte[] hash = digest.digest(bytes);
            final StringBuilder hex = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (final Exception ex) {
            return "";
        }
    }

    private static String string(final String value) {
        return "\"" + (value == null ? "" : value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")) + "\"";
    }

    record AssetItemDefinition(
        String id,
        boolean enabled,
        String category,
        String material,
        int amount,
        int customModelData,
        String permission,
        String pack,
        String icon,
        String description,
        String nameZhCn,
        String nameEnUs,
        List<String> loreZhCn,
        List<String> loreEnUs
    ) {
    }

    record UploadedAsset(String name, Path path, long size, String sha1) {
        String toJson() {
            return "{\"name\":" + string(this.name) + ",\"size\":" + this.size + ",\"sha1\":" + string(this.sha1) + "}";
        }
    }

    record PromptRequest(
        String style,
        String useCase,
        String theme,
        String category,
        String colorPalette,
        String materialFeel,
        String resolution,
        boolean transparentBackground
    ) {
    }

    record AssetPromptBundle(String modelPrompt, String uiPrompt, List<String> steps, String itemDefinitionHint) {
        String toJson() {
            final StringBuilder json = new StringBuilder("{");
            json.append("\"modelPrompt\":").append(string(this.modelPrompt)).append(',');
            json.append("\"uiPrompt\":").append(string(this.uiPrompt)).append(',');
            json.append("\"itemDefinitionHint\":").append(string(this.itemDefinitionHint)).append(',');
            json.append("\"steps\":[");
            for (int i = 0; i < this.steps.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(string(this.steps.get(i)));
            }
            json.append("]}");
            return json.toString();
        }
    }

    record ValidationResult(int itemCount, int packCount, List<String> errors, List<String> warnings) {
        String toJson() {
            final StringBuilder json = new StringBuilder("{");
            json.append("\"itemCount\":").append(this.itemCount).append(',');
            json.append("\"packCount\":").append(this.packCount).append(',');
            json.append("\"errors\":[");
            for (int i = 0; i < this.errors.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(string(this.errors.get(i)));
            }
            json.append("],\"warnings\":[");
            for (int i = 0; i < this.warnings.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(string(this.warnings.get(i)));
            }
            json.append("]}");
            return json.toString();
        }
    }

    record PublishedPack(String fileName, String url, String sha1, boolean required, boolean sendOnJoin) {
        String toJson() {
            return "{\"fileName\":" + string(this.fileName) + ",\"url\":" + string(this.url) + ",\"sha1\":" + string(this.sha1)
                + ",\"required\":" + this.required + ",\"sendOnJoin\":" + this.sendOnJoin + "}";
        }
    }
}
