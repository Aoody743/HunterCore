package net.momirealms.craftengine.core.plugin.text.minimessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.momirealms.craftengine.core.util.AdventureHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface FormattedLine {
    TagResolver[] CUSTOM_RESOLVERS = new TagResolver[]{
            createDummyResolvers("expr"),
            createDummyResolvers("image"),
            createDummyResolvers("arg"),
            createDummyResolvers("shift"),
            createDummyResolvers("i18n"),
            createDummyResolvers("l10n"),
            createDummyResolvers("global"),
            createDummyResolvers("papi"),
            createDummyResolvers("rel_papi")
    };

    class Companion {
        public static TagResolver[] LATEST_RESOLVERS = CUSTOM_RESOLVERS;

        public static void resetWithCustomResolvers(List<String> customResolvers) {
            TagResolver[] resolvers = new TagResolver[customResolvers.size() + CUSTOM_RESOLVERS.length];
            System.arraycopy(CUSTOM_RESOLVERS, 0, resolvers, 0, CUSTOM_RESOLVERS.length);
            for (int i = 0; i < customResolvers.size(); i++) {
                resolvers[CUSTOM_RESOLVERS.length + i] = createDummyResolvers(customResolvers.get(i));
            }
            LATEST_RESOLVERS = resolvers;
        }
    }

    Component parse(net.momirealms.craftengine.core.plugin.context.Context context);

    private static TagResolver createDummyResolvers(String tag) {
        return new TagResolver() {
            @Override
            public boolean has(@NotNull String name) {
                return tag.equals(name);
            }

            @Override
            public @Nullable Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) throws ParsingException {
                return null;
            }
        };
    }

    static FormattedLine create(String line) {
        if (line.equals(AdventureHelper.customMiniMessage().stripTags(line, Companion.LATEST_RESOLVERS))) {
            return new PreParsedLine(AdventureHelper.miniMessage().deserialize(line));
        } else {
            return new DynamicLine(line);
        }
    }

    class PreParsedLine implements FormattedLine {
        private final Component parsed;

        public PreParsedLine(Component parsed) {
            this.parsed = parsed;
        }

        @Override
        public Component parse(net.momirealms.craftengine.core.plugin.context.Context context) {
            return this.parsed;
        }
    }

    class DynamicLine implements FormattedLine {
        private final String content;

        public DynamicLine(String content) {
            this.content = content;
        }

        @Override
        public Component parse(net.momirealms.craftengine.core.plugin.context.Context context) {
            return AdventureHelper.miniMessage().deserialize(this.content, context.tagResolvers());
        }
    }
}

