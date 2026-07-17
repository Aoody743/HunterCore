package org.huntercore.plugins.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class HunterAdminCompletionsTest {
    private static final List<String> MODULES = List.of("tps-display", "sidebar", "motd", "ai", "web-panel");
    private static final List<String> ESSENTIALS_COMMANDS = List.of("back", "fly", "spawn");
    private static final List<String> MANAGEMENT_COMMANDS = List.of("plugins", "memory", "threads");
    private static final List<String> ACTOR_COMMANDS = List.of("spawn", "remove", "skin");
    private static final List<String> REAL_FAKE_PLAYER_COMMANDS = List.of("spawn", "stop", "status");
    private static final List<String> HELP_TOPICS = List.of("all", "admin", "auth", "ai");

    @Test
    void completesWebBranchesInDeclaredOrder() {
        assertEquals(List.of("port", "public-map"), complete("web", "p"));
        assertEquals(List.of("0.0.0.0"), complete("WEB", "address", "0"));
        assertEquals(List.of("player"), complete("web", "user", "Steve", "p"));
        assertEquals(List.of("inherit"), complete("web", "allow", "Steve", "i"));
        assertEquals(List.of("on", "off"), complete("web", "execution", "Steve", ""));
    }

    @Test
    void completesAiBranchesAndAliases() {
        assertEquals(List.of("model", "max-tokens"), complete("ai", "m"));
        assertEquals(List.of("on", "off"), complete("AI", "chat", ""));
        assertEquals(List.of("gpt-4.1-mini", "gpt-4.1"), complete("ai", "model", "gpt-4.1"));
        assertEquals(List.of("HUNTERCORE_AI_API_KEY"), complete("ai", "api-key-env", "H"));
    }

    @Test
    void completesConfiguredModulesWithoutReorderingThem() {
        assertEquals(List.of("sidebar"), complete("module", "s"));
        assertEquals(List.of("on", "off"), complete("module", "sidebar", ""));
    }

    @Test
    void routesCommandCompletionToTheConfiguredCommandSet() {
        assertEquals(
            List.of("essentials", "management", "fake-players", "real-fake-players", "npcs"),
            complete("command", "")
        );
        assertEquals(List.of("back"), complete("command", "ESSENTIALS", "b"));
        assertEquals(List.of("plugins"), complete("command", "management", "p"));
        assertEquals(List.of("spawn", "skin"), complete("command", "fake-players", "s"));
        assertEquals(List.of("spawn", "skin"), complete("command", "npcs", "s"));
        assertEquals(List.of("stop", "status"), complete("command", "real-fake-players", "st"));
    }

    @Test
    void completesCommandToggleInDeclaredOrder() {
        assertEquals(List.of("on", "off"), complete("command", "essentials", "back", ""));
        assertEquals(List.of("off"), complete("command", "essentials", "back", "of"));
    }

    @Test
    void usesInjectedHelpTopics() {
        assertEquals(List.of("all", "admin", "auth", "ai"), complete("help", "a"));
    }

    private static List<String> complete(final String... args) {
        return HunterAdminCompletions.complete(
            args,
            MODULES,
            ESSENTIALS_COMMANDS,
            MANAGEMENT_COMMANDS,
            ACTOR_COMMANDS,
            REAL_FAKE_PLAYER_COMMANDS,
            HELP_TOPICS
        );
    }
}
