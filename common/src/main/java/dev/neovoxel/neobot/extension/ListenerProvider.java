package dev.neovoxel.neobot.extension;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.misc.EventListener;
import org.pf4j.ExtensionPoint;

public interface ListenerProvider extends ExtensionPoint {
    EventListener getListener(NeoBot plugin);
    default String getRequiredPlatform() {
        return "Common";
    }
}
