package net.momirealms.craftengine.core.plugin.compatibility;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.momirealms.craftengine.core.plugin.context.Context;

public interface TagResolverProvider {

    String name();

    TagResolver getTagResolver(Context context);
}
