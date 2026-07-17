package net.momirealms.craftengine.core.util;

public final class ExceptionUtils {
    private ExceptionUtils() {}

    public static boolean hasException(Throwable t, Exception e) {
        while (t != null) {
            if (t == e) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
