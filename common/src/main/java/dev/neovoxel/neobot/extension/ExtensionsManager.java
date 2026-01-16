package dev.neovoxel.neobot.extension;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.misc.EventListener;
import lombok.Getter;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ExtensionsManager {
    PluginManager pluginManager;
    @Getter
    Map<String, EventListener> listenerMap = new HashMap<>();
    public void loadExtensions(NeoBot plugin){
        // 初始化插件管理器
        Path path = Paths.get(plugin.getDataFolder().getAbsolutePath(),"extensions");
        try {
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            }
        }catch(IOException e){
            plugin.getNeoLogger().error("创建目录失败: " + e.getMessage());
        }
        pluginManager = new DefaultPluginManager(path);
        pluginManager.loadPlugins();

        // 启动插件
        pluginManager.startPlugins();

        // 获取所有实现了 ListenerProvider 的扩展
        List<ListenerProvider> extensions = pluginManager.getExtensions(ListenerProvider.class);
        for (ListenerProvider ext : extensions) {
            org.pf4j.PluginWrapper wrapper = pluginManager.whichPlugin(ext.getClass());
            if (wrapper == null){
                continue;
            }
            org.pf4j.PluginDescriptor descriptor = wrapper.getDescriptor();
            String pluginId = descriptor.getPluginId();
            String version = descriptor.getVersion();
            String provider = descriptor.getProvider();
            if(!Objects.equals(ext.getRequiredPlatform(), "Common") && !Objects.equals(ext.getRequiredPlatform(), plugin.getPlatform())){
                plugin.getNeoLogger().warn(String.format("扩展[%s]要求在[%s]上加载! 当前环境[%s]已自动跳过.",
                        pluginId, ext.getRequiredPlatform(), plugin.getPlatform()));
                continue;
            }
            plugin.getNeoLogger().info(String.format("加载扩展: %s | 版本: %s | 作者:%s ",
                    pluginId, version, provider));

            listenerMap.put(pluginId,ext.getListener(plugin));
        }
    }

    public void unloadExtensions(){
        for(Map.Entry<String, EventListener> entry : listenerMap.entrySet()){
            entry.getValue().reset();
        }
        pluginManager.stopPlugins();
    }

}
