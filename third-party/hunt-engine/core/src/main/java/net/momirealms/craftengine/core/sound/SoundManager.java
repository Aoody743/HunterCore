package net.momirealms.craftengine.core.sound;

import net.momirealms.craftengine.core.plugin.Manageable;
import net.momirealms.craftengine.core.plugin.config.ConfigParser;
import net.momirealms.craftengine.core.util.Key;
import org.incendo.cloud.suggestion.Suggestion;

import java.util.List;
import java.util.Map;

public interface SoundManager extends Manageable {

    boolean isVanillaSoundEvent(Key key);

    ConfigParser[] parsers();

    List<Suggestion> cachedSoundSuggestions();

    Map<Key, SoundEvent> sounds();
}
