package dev.neovoxel.neobot.command.sub;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.adapter.CommandSender;
import dev.neovoxel.neobot.script.Script;
import dev.neovoxel.neobot.script.remote.RemoteScript;
import dev.neovoxel.neobot.script.remote.Repository;
import dev.neovoxel.nsapi.entity.Row;
import dev.neovoxel.nsapi.table.DatabaseTable;

import java.util.List;
import java.util.Objects;

public class ScriptCommand {
    private final NeoBot plugin;

    public ScriptCommand(NeoBot plugin) {
        this.plugin = plugin;
    }

    public void list(CommandSender sender) {
        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.script.list.head"));
        for (Script script : plugin.getScriptProvider().getLoadedScripts()) {
            for (String message : plugin.getMessageConfig().getStringArray("internal.script.list.single")) {
                message = message
                        .replace("${id}", script.getId())
                        .replace("${name}", script.getName())
                        .replace("${version}", script.getVersion())
                        .replace("${author}", script.getAuthor())
                        .replace("${description}", script.getDescription());
                sender.sendMessage(message);
            }
        }
    }

    public void reload(CommandSender sender) {
        plugin.getNeoLogger().info("Reloading scripts...");
        plugin.getScriptScheduler().cancelAllTasks();
        plugin.getScriptProvider().setScriptSystemLoaded(false);
        plugin.getBotProvider().resetListeners();
        plugin.getGameEventListener().reset();
        plugin.getScriptProvider().unloadScript();
        try {
            plugin.getScriptProvider().loadScript(plugin);
        } catch (Throwable e) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.script.reload.error")
                    .replace("${error}", e.getMessage()));
        }
        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.script.reload.success"));
    }

    public void load(String arg, CommandSender sender) {
        sender.sendMessage(plugin.getScriptProvider().loadScript(plugin, arg));
    }

    public void unload(String arg, CommandSender sender) {
        boolean result = plugin.getScriptProvider().unloadScript(arg);
        if (result) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.script.unload.success"));
        } else {
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.script.unload.error"));
        }
    }

    public void info(String arg, CommandSender sender) {
        Script script = plugin.getScriptProvider().getScriptInfo(arg);
        if (script == null) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.script.info.not-found"));
            return;
        }
        for (String s : plugin.getMessageConfig().getStringArray("internal.script.info.single")) {
            sender.sendMessage(s
                    .replace("${id}", script.getId())
                    .replace("${name}", script.getName())
                    .replace("${version}", script.getVersion())
                    .replace("${author}", script.getAuthor())
                    .replace("${description}", script.getDescription()));
        }
    }
}
