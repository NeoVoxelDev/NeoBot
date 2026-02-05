package dev.neovoxel.neobot.loader;

import com.velocitypowered.api.plugin.PluginManager;
import dev.neovoxel.jarflow.loader.ExternalLoader;
import dev.neovoxel.neobot.NeoBotVelocity;

import java.io.File;

public class VelocityLibraryLoader extends ExternalLoader {

    private final NeoBotVelocity plugin;
    private final PluginManager pluginManager;

    public VelocityLibraryLoader(NeoBotVelocity plugin, PluginManager pluginManager) {
        this.plugin = plugin;
        this.pluginManager = pluginManager;
    }

    @Override
    public void load(File file) {
        pluginManager.addToClasspath(plugin, file.toPath());
    }
}
