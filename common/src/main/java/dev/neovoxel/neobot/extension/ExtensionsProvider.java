package dev.neovoxel.neobot.extension;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.misc.EventListener;

import java.util.Map;

public interface ExtensionsProvider {
    Map<String, EventListener> getListenerMap();
    void loadExtensions(NeoBot plugin);
    void unloadExtensions();
}
