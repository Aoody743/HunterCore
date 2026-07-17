package net.momirealms.craftengine.core.util;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MapColor {
    private static final MapColor[] COLORS = new MapColor[64];
    private static final Map<String, MapColor> BY_NAME = new HashMap<>();

    public static final MapColor CLEAR = new MapColor(0, 0, "clear", "none");
    public static final MapColor PALE_GREEN = new MapColor(1, 8368696, "pale_green", "grass");
    public static final MapColor PALE_YELLOW = new MapColor(2, 16247203, "pale_yellow", "sand");
    public static final MapColor WHITE_GRAY = new MapColor(3, 13092807, "white_gray", "wool");
    public static final MapColor BRIGHT_RED = new MapColor(4, 16711680, "bright_red", "fire");
    public static final MapColor PALE_PURPLE = new MapColor(5, 10526975, "pale_purple", "ice");
    public static final MapColor IRON_GRAY = new MapColor(6, 10987431, "iron_gray", "metal");
    public static final MapColor DARK_GREEN = new MapColor(7, 31744, "dark_green", "plant");
    public static final MapColor WHITE = new MapColor(8, 16777215, "white", "snow");
    public static final MapColor LIGHT_BLUE_GRAY = new MapColor(9, 10791096, "light_blue_gray", "clay");
    public static final MapColor DIRT_BROWN = new MapColor(10, 9923917, "dirt_brown", "dirt");
    public static final MapColor STONE_GRAY = new MapColor(11, 7368816, "stone_gray", "stone");
    public static final MapColor WATER_BLUE = new MapColor(12, 4210943, "water_blue", "water");
    public static final MapColor OAK_TAN = new MapColor(13, 9402184, "oak_tan", "wood");
    public static final MapColor OFF_WHITE = new MapColor(14, 16776437, "off_white", "quartz");
    public static final MapColor ORANGE = new MapColor(15, 14188339, "orange", "color_orange");
    public static final MapColor MAGENTA = new MapColor(16, 11685080, "magenta", "color_magenta");
    public static final MapColor LIGHT_BLUE = new MapColor(17, 6724056, "light_blue", "color_light_blue");
    public static final MapColor YELLOW = new MapColor(18, 15066419, "yellow", "color_yellow");
    public static final MapColor LIME = new MapColor(19, 8375321, "lime", "color_light_green");
    public static final MapColor PINK = new MapColor(20, 15892389, "pink", "color_pink");
    public static final MapColor GRAY = new MapColor(21, 5000268, "gray", "color_gray");
    public static final MapColor LIGHT_GRAY = new MapColor(22, 10066329, "light_gray", "color_light_gray");
    public static final MapColor CYAN = new MapColor(23, 5013401, "cyan", "color_cyan");
    public static final MapColor PURPLE = new MapColor(24, 8339378, "purple", "color_purple");
    public static final MapColor BLUE = new MapColor(25, 3361970, "blue", "color_blue");
    public static final MapColor BROWN = new MapColor(26, 6704179, "brown", "color_brown");
    public static final MapColor GREEN = new MapColor(27, 6717235, "green", "color_green");
    public static final MapColor RED = new MapColor(28, 10040115, "red", "color_red");
    public static final MapColor BLACK = new MapColor(29, 1644825, "black", "color_black");
    public static final MapColor GOLD = new MapColor(30, 16445005, "gold");
    public static final MapColor DIAMOND_BLUE = new MapColor(31, 6085589, "diamond_blue", "diamond");
    public static final MapColor LAPIS_BLUE = new MapColor(32, 4882687, "lapis_blue", "lapis");
    public static final MapColor EMERALD_GREEN = new MapColor(33, 55610, "emerald_green", "emerald");
    public static final MapColor SPRUCE_BROWN = new MapColor(34, 8476209, "spruce_brown", "podzol");
    public static final MapColor DARK_RED = new MapColor(35, 7340544, "dark_red", "nether");
    public static final MapColor TERRACOTTA_WHITE = new MapColor(36, 13742497, "terracotta_white");
    public static final MapColor TERRACOTTA_ORANGE = new MapColor(37, 10441252, "terracotta_orange");
    public static final MapColor TERRACOTTA_MAGENTA = new MapColor(38, 9787244, "terracotta_magenta");
    public static final MapColor TERRACOTTA_LIGHT_BLUE = new MapColor(39, 7367818, "terracotta_light_blue");
    public static final MapColor TERRACOTTA_YELLOW = new MapColor(40, 12223780, "terracotta_yellow");
    public static final MapColor TERRACOTTA_LIME = new MapColor(41, 6780213, "terracotta_lime", "terracotta_light_green");
    public static final MapColor TERRACOTTA_PINK = new MapColor(42, 10505550, "terracotta_pink");
    public static final MapColor TERRACOTTA_GRAY = new MapColor(43, 3746083, "terracotta_gray");
    public static final MapColor TERRACOTTA_LIGHT_GRAY = new MapColor(44, 8874850, "terracotta_light_gray");
    public static final MapColor TERRACOTTA_CYAN = new MapColor(45, 5725276, "terracotta_cyan");
    public static final MapColor TERRACOTTA_PURPLE = new MapColor(46, 8014168, "terracotta_purple");
    public static final MapColor TERRACOTTA_BLUE = new MapColor(47, 4996700, "terracotta_blue");
    public static final MapColor TERRACOTTA_BROWN = new MapColor(48, 4993571, "terracotta_brown");
    public static final MapColor TERRACOTTA_GREEN = new MapColor(49, 5001770, "terracotta_green");
    public static final MapColor TERRACOTTA_RED = new MapColor(50, 9321518, "terracotta_red");
    public static final MapColor TERRACOTTA_BLACK = new MapColor(51, 2430480, "terracotta_black");
    public static final MapColor DULL_RED = new MapColor(52, 12398641, "dull_red", "crimson_nylium");
    public static final MapColor DULL_PINK = new MapColor(53, 9715553, "dull_pink", "crimson_stem");
    public static final MapColor DARK_CRIMSON = new MapColor(54, 6035741, "dark_crimson", "crimson_hyphae");
    public static final MapColor TEAL = new MapColor(55, 1474182, "teal", "warped_nylium");
    public static final MapColor DARK_AQUA = new MapColor(56, 3837580, "dark_aqua", "warped_stem");
    public static final MapColor DARK_DULL_PINK = new MapColor(57, 5647422, "dark_dull_pink", "warped_hyphae");
    public static final MapColor BRIGHT_TEAL = new MapColor(58, 1356933, "bright_teal", "warped_wart_block");
    public static final MapColor DEEPSLATE_GRAY = new MapColor(59, 6579300, "deepslate_gray", "deepslate");
    public static final MapColor RAW_IRON_PINK = new MapColor(60, 14200723, "raw_iron_pink", "raw_iron");
    public static final MapColor LICHEN_GREEN = new MapColor(61, 8365974, "lichen_green", "glow_lichen");

    public final int color;
    public final int id;
    public final String[] name;

    private MapColor(int id, int color, String... names) {
        if (id >= 0 && id <= 63) {
            this.id = id;
            this.color = color;
            this.name = names;
            COLORS[id] = this;
            if (names != null) {
                for (String name : names) {
                    BY_NAME.put(name, this);
                }
            }
        } else {
            throw new IndexOutOfBoundsException("Map colour ID must be between 0 and 63 (inclusive)");
        }
    }

    public static MapColor byName(String name) {
        if (name == null) {
            return CLEAR;
        }
        return BY_NAME.getOrDefault(name.toLowerCase(Locale.ROOT), CLEAR);
    }

    public static MapColor byId(int id) {
        Preconditions.checkPositionIndex(id, COLORS.length, "material id");
        return getUnchecked(id);
    }

    private static MapColor getUnchecked(int id) {
        MapColor mapColor = COLORS[id];
        return mapColor != null ? mapColor : CLEAR;
    }

    public static MapColor byColor(Color color) {
        if (color == null) {
            return CLEAR;
        }
        return byColor(color.color());
    }

    public static MapColor byColor(int rgb) {
        int targetRed = Color.red(rgb);
        int targetGreen = Color.green(rgb);
        int targetBlue = Color.blue(rgb);
        MapColor nearest = CLEAR;
        double bestDistance = Double.MAX_VALUE;
        for (MapColor candidate : COLORS) {
            if (candidate == null) continue;
            double distance = colorDistance(targetRed, targetGreen, targetBlue,
                    Color.red(candidate.color), Color.green(candidate.color), Color.blue(candidate.color));
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static double colorDistance(int r1, int g1, int b1, int r2, int g2, int b2) {
        double redAverage = (r1 + r2) / 2.0;
        int deltaR = r1 - r2;
        int deltaG = g1 - g2;
        int deltaB = b1 - b2;
        double redWeight = 2.0 + redAverage / 256.0;
        double blueWeight = 2.0 + (255.0 - redAverage) / 256.0;
        return redWeight * deltaR * deltaR + 4.0 * deltaG * deltaG + blueWeight * deltaB * deltaB;
    }
}