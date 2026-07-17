package net.momirealms.craftengine.core.util;

import org.joml.Vector3f;

import java.util.Arrays;

public final class Color {
    private static final int BIT_MASK = 0xff;
    private static final byte DEFAULT_ALPHA = (byte) 255;
    private final int color;

    public Color(int color) {
        this.color = color;
    }

    public Color(int r, int g, int b) {
        this(DEFAULT_ALPHA, r, g, b);
    }

    public Color(int a, int r, int g, int b) {
        this(toDecimal(a, r, g, b));
    }

    public int color() {
        return color;
    }

    public static int toDecimal(int a, int r, int g, int b) {
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int toDecimal(int r, int g, int b) {
        return DEFAULT_ALPHA << 24 | r << 16 | g << 8 | b;
    }

    public static Color fromDecimal(int decimal) {
        return new Color(decimal);
    }

    public static Color fromHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            throw new IllegalArgumentException("Hex string cannot be null or empty");
        }
        String cleanHex = hex.startsWith("#") ? hex.substring(1) : hex;
        int length = cleanHex.length();
        int a, r, g, b;
        try {
            if (length == 3) {
                r = Integer.parseInt(cleanHex.substring(0, 1), 16);
                g = Integer.parseInt(cleanHex.substring(1, 2), 16);
                b = Integer.parseInt(cleanHex.substring(2, 3), 16);
                r = (r << 4) | r;
                g = (g << 4) | g;
                b = (b << 4) | b;
                a = DEFAULT_ALPHA & BIT_MASK;
            } else if (length == 4) {
                a = Integer.parseInt(cleanHex.substring(0, 1), 16);
                r = Integer.parseInt(cleanHex.substring(1, 2), 16);
                g = Integer.parseInt(cleanHex.substring(2, 3), 16);
                b = Integer.parseInt(cleanHex.substring(3, 4), 16);
                a = (a << 4) | a;
                r = (r << 4) | r;
                g = (g << 4) | g;
                b = (b << 4) | b;
            } else if (length == 6) {
                r = Integer.parseInt(cleanHex.substring(0, 2), 16);
                g = Integer.parseInt(cleanHex.substring(2, 4), 16);
                b = Integer.parseInt(cleanHex.substring(4, 6), 16);
                a = DEFAULT_ALPHA & BIT_MASK;
            } else if (length == 8) {
                a = Integer.parseInt(cleanHex.substring(0, 2), 16);
                r = Integer.parseInt(cleanHex.substring(2, 4), 16);
                g = Integer.parseInt(cleanHex.substring(4, 6), 16);
                b = Integer.parseInt(cleanHex.substring(6, 8), 16);
            } else {
                throw new IllegalArgumentException("Invalid hex format: " + hex + ". Expected 3, 4, 6, or 8 digits (with optional # prefix)");
            }
            return new Color(a, r, g, b);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex string: " + hex, e);
        }
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    public static Color fromVector3f(Vector3f vec) {
        return new Color(0 << 24 /*不可省略*/ | MiscUtils.floor(vec.x) << 16 | MiscUtils.floor(vec.y) << 8 | MiscUtils.floor(vec.z));
    }

    public static int opaque(int color) {
        return color | -16777216;
    }

    public static int transparent(int color) {
        return color & 16777215;
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return color >> 16 & BIT_MASK;
    }

    public static int green(int color) {
        return color >> 8 & BIT_MASK;
    }

    public static int blue(int color) {
        return color & BIT_MASK;
    }

    public static Color fromStrings(String[] strings) {
        if (strings.length == 3) {
            // rgb
            return fromDecimal(toDecimal(Integer.parseInt(strings[0]), Integer.parseInt(strings[1]), Integer.parseInt(strings[2])));
        } else if (strings.length == 4) {
            // argb
            return fromDecimal(toDecimal(Integer.parseInt(strings[0]), Integer.parseInt(strings[1]), Integer.parseInt(strings[2]), Integer.parseInt(strings[3])));
        } else {
            throw new IllegalArgumentException("Invalid color format: " + Arrays.toString(strings));
        }
    }

    public int a() {
        return alpha(color);
    }

    public int b() {
        return blue(color);
    }

    public int g() {
        return green(color);
    }

    public int r() {
        return red(color);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Color color1)) return false;
        return this.color == color1.color;
    }

    @Override
    public int hashCode() {
        return Math.abs(this.color);
    }
}
