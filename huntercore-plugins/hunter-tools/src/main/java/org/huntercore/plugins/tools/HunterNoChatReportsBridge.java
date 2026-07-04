package org.huntercore.plugins.tools;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class HunterNoChatReportsBridge {
    private static final String CONFIG_CLASS = "org.bxteam.divinemc.config.DivineConfig";
    private static final String NETWORK_CLASS = "org.bxteam.divinemc.config.DivineConfig$NetworkCategory";
    private static final String KEY_PREFIX = "network.no-chat-reports.";

    private HunterNoChatReportsBridge() {
    }

    static Settings read() {
        try {
            final Class<?> network = Class.forName(NETWORK_CLASS);
            return new Settings(
                bool(network, "noChatReportsEnabled", true),
                bool(network, "noChatReportsAddQueryData", true),
                bool(network, "noChatReportsConvertToGameMessage", true),
                bool(network, "noChatReportsDemandOnClient", false),
                bool(network, "noChatReportsDebugLog", false),
                string(network, "noChatReportsDisconnectDemandOnClientMessage", "You do not have No Chat Reports, and this server is configured to require it on client!")
            );
        } catch (final ReflectiveOperationException | LinkageError ex) {
            return Settings.defaults();
        }
    }

    static void save(final Settings settings) {
        try {
            final Class<?> divine = Class.forName(CONFIG_CLASS);
            final Class<?> network = Class.forName(NETWORK_CLASS);
            setStatic(network, "noChatReportsEnabled", settings.enabled());
            setStatic(network, "noChatReportsAddQueryData", settings.addQueryData());
            setStatic(network, "noChatReportsConvertToGameMessage", settings.convertToGameMessage());
            setStatic(network, "noChatReportsDemandOnClient", settings.demandOnClient());
            setStatic(network, "noChatReportsDebugLog", settings.debugLog());
            setStatic(network, "noChatReportsDisconnectDemandOnClientMessage", settings.disconnectMessage());

            final Field configField = divine.getField("config");
            final Object config = configField.get(null);
            final Method set = config.getClass().getMethod("set", String.class, Object.class);
            set.invoke(config, KEY_PREFIX + "enabled", settings.enabled());
            set.invoke(config, KEY_PREFIX + "add-query-data", settings.addQueryData());
            set.invoke(config, KEY_PREFIX + "convert-to-game-message", settings.convertToGameMessage());
            set.invoke(config, KEY_PREFIX + "demand-on-client", settings.demandOnClient());
            set.invoke(config, KEY_PREFIX + "debug-log", settings.debugLog());
            set.invoke(config, KEY_PREFIX + "disconnect-demand-on-client-message", settings.disconnectMessage());

            final Field fileField = divine.getDeclaredField("configFile");
            fileField.setAccessible(true);
            final File file = (File) fileField.get(null);
            if (file != null) {
                final Method save = config.getClass().getMethod("save", File.class);
                save.invoke(config, file);
            }
        } catch (final ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("Failed to save HunterCore no-chat-reports settings", ex);
        }
    }

    private static boolean bool(final Class<?> type, final String field, final boolean fallback) throws ReflectiveOperationException {
        final Field value = type.getField(field);
        return value.getBoolean(null);
    }

    private static String string(final Class<?> type, final String field, final String fallback) throws ReflectiveOperationException {
        final Field value = type.getField(field);
        final Object raw = value.get(null);
        return raw instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static void setStatic(final Class<?> type, final String field, final Object newValue) throws ReflectiveOperationException {
        final Field value = type.getField(field);
        value.set(null, newValue);
    }

    record Settings(
        boolean enabled,
        boolean addQueryData,
        boolean convertToGameMessage,
        boolean demandOnClient,
        boolean debugLog,
        String disconnectMessage
    ) {
        static Settings defaults() {
            return new Settings(true, true, true, false, false, "You do not have No Chat Reports, and this server is configured to require it on client!");
        }

        Settings withEnabled(final boolean value) {
            return new Settings(value, this.addQueryData, this.convertToGameMessage, this.demandOnClient, this.debugLog, this.disconnectMessage);
        }

        Settings withAddQueryData(final boolean value) {
            return new Settings(this.enabled, value, this.convertToGameMessage, this.demandOnClient, this.debugLog, this.disconnectMessage);
        }

        Settings withConvertToGameMessage(final boolean value) {
            return new Settings(this.enabled, this.addQueryData, value, this.demandOnClient, this.debugLog, this.disconnectMessage);
        }

        Settings withDemandOnClient(final boolean value) {
            return new Settings(this.enabled, this.addQueryData, this.convertToGameMessage, value, this.debugLog, this.disconnectMessage);
        }

        Settings withDebugLog(final boolean value) {
            return new Settings(this.enabled, this.addQueryData, this.convertToGameMessage, this.demandOnClient, value, this.disconnectMessage);
        }

        Settings withDisconnectMessage(final String value) {
            return new Settings(this.enabled, this.addQueryData, this.convertToGameMessage, this.demandOnClient, this.debugLog, value);
        }
    }
}
