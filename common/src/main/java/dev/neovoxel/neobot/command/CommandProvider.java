package dev.neovoxel.neobot.command;

import dev.neovoxel.nbapi.client.NBotClient;
import dev.neovoxel.nbapi.client.OBWSClient;
import dev.neovoxel.nbapi.client.OBWSServer;
import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.adapter.CommandSender;
import dev.neovoxel.neobot.command.sub.RepositoryCommand;
import dev.neovoxel.neobot.command.sub.ScriptCommand;
import dev.neovoxel.neobot.migrate.ConfigMigration;
import dev.neovoxel.neobot.migrate.DataMigration;
import dev.neovoxel.neobot.script.Script;
import dev.neovoxel.neobot.script.remote.RemoteScript;
import dev.neovoxel.neobot.script.remote.Repository;
import dev.neovoxel.neobot.util.HttpUtil;
import dev.neovoxel.nsapi.entity.Row;
import dev.neovoxel.nsapi.table.DatabaseTable;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class CommandProvider {
    private final List<Value> methods = new ArrayList<>();

    protected final NeoBot plugin;

    private final ScriptCommand scriptCommand;
    private final RepositoryCommand repositoryCommand;

    protected CommandProvider(NeoBot plugin) {
        this.plugin = plugin;
        this.scriptCommand = new ScriptCommand(plugin);
        this.repositoryCommand = new RepositoryCommand(plugin);
    }

    public abstract void registerCommand();

    public void onCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender.hasPermission("neobot.command.help")) {
                for (String message : plugin.getMessageConfig().getStringArray("internal.help")) {
                    sender.sendMessage(message);
                }
            } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
            return;
        } else if (args.length == 1) {
            if (args[0].equalsIgnoreCase("help")) {
                if (sender.hasPermission("neobot.command.help")) {
                    for (String message : plugin.getMessageConfig().getStringArray("internal.help")) {
                        sender.sendMessage(message);
                    }
                } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                return;
            } else if (args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("neobot.command.reload")) {
                    sender.sendMessage(plugin.getMessageConfig().getMessage("internal.reload.reloading"));
                    plugin.reload(sender);
                } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                return;
            } else if (args[0].equalsIgnoreCase("migrate")) {
                if (sender.hasPermission("neobot.command.migrate")) {
                    try {
                        ConfigMigration.migrate(plugin, sender);
                        DataMigration.migrate(plugin, sender);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
            } else if (args[0].equalsIgnoreCase("status")) {
                if (sender.hasPermission("neobot.command.status")) {
                    sender.sendMessage(plugin.getMessageConfig().getMessage("internal.status.fetching"));
                    plugin.submitAsync(() -> {
                        boolean needGithubProxy = plugin.getGeneralConfig().getBoolean("repository.use-github-proxy");
                        String currentVersion = plugin.getVersion();
                        try {
                            String latestVersion = HttpUtil.getLatestVersion(needGithubProxy);
                            String latestCommit = HttpUtil.getLatestCommit(needGithubProxy);
                            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.status.data.head"));
                            for (String message : plugin.getMessageConfig().getStringArray("internal.status.data.basic")) {
                                message = message
                                        .replace("${version}", currentVersion)
                                        .replace("${latest_version}", latestVersion)
                                        .replace("${latest_commit}", latestCommit);
                                sender.sendMessage(message);
                            }
                            sender.sendMessage(plugin.getMessageConfig().getString("internal.status.data.bot_head"));
                            for (NBotClient client : plugin.getBotProvider().getBot()) {
                                if (client instanceof OBWSServer) {
                                    sender.sendMessage(plugin.getMessageConfig().getString("internal.status.data.bot")
                                            .replace("${type}", "onebot11-ws-reverse")
                                            .replace("${connected}", client.isConnected() ? "在线" : "离线"));
                                } else if (client instanceof OBWSClient) {
                                    sender.sendMessage(plugin.getMessageConfig().getString("internal.status.data.bot")
                                            .replace("${type}", "onebot11-ws")
                                            .replace("${connected}", client.isConnected() ? "在线" : "离线"));
                                }
                            }
                        } catch (IOException e) {
                            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.status.error")
                                    .replace("${error}", e.toString()));
                        }
                    });
                } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
            } else if (args[0].equalsIgnoreCase("init")) {
                if (sender.hasPermission("neobot.command.init")) {
                    sender.sendMessage(plugin.getMessageConfig().getMessage("internal.init.starting"));
                    repositoryCommand.add("https://raw.githubusercontent.com/NeoVoxelDev/NeoBotScriptsRepo/main/repo.json", sender);
                    repositoryCommand.install("default_scripts", sender, () -> {
                        scriptCommand.reload(sender);
                        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.init.complete"));
                    });
                } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("migrate")) {
                if (args[1].equalsIgnoreCase("config")) {
                    if (sender.hasPermission("neobot.command.migrate")) {
                        try {
                            ConfigMigration.migrate(plugin, sender);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("data")) {
                    if (sender.hasPermission("neobot.command.migrate")) {
                        try {
                            DataMigration.migrate(plugin, sender);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                }
            } else if (args[0].equalsIgnoreCase("script")) {
                if (args[1].equalsIgnoreCase("list")) {
                    if (sender.hasPermission("neobot.command.script")) {
                        scriptCommand.list(sender);
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("reload")) {
                    if (sender.hasPermission("neobot.command.script")) {
                        scriptCommand.reload(sender);
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                }
            } else if (args[0].equalsIgnoreCase("repo")) {
                if (args[1].equalsIgnoreCase("list")) {
                    if (sender.hasPermission("neobot.command.repo")) {
                        repositoryCommand.list(sender);
                    } else {
                        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("script")) {
                if (args[1].equalsIgnoreCase("load")) {
                    if (sender.hasPermission("neobot.command.script")) {
                        scriptCommand.load(args[2], sender);
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("unload")) {
                    if (sender.hasPermission("neobot.command.script")) {
                        scriptCommand.unload(args[2], sender);
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("info")) {
                    if (sender.hasPermission("neobot.command.script")) {
                        scriptCommand.info(args[2], sender);
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                }
            } else if (args[0].equalsIgnoreCase("repo")) {
                if (args[1].equalsIgnoreCase("add")) {
                    if (sender.hasPermission("neobot.command.repo")) {
                        String url = args[2];
                        repositoryCommand.add(url, sender);
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("remove")) {
                    if (sender.hasPermission("neobot.command.repo")) {
                        String arg = args[2];
                        repositoryCommand.remove(arg, sender);
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("scriptlist")) {
                    if (sender.hasPermission("neobot.command.repo")) {
                        String arg = args[2];
                        repositoryCommand.scriptlist(arg, sender);
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("install")) {
                    if (sender.hasPermission("neobot.command.repo")) {
                        String id = args[2];
                        repositoryCommand.install(id, sender);
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                }
            }
        }
        methods.forEach(method -> method.execute(sender, args));
    }

    @HostAccess.Export
    public void onCommand(Value value) {
        if (value.canExecute()) {
            methods.add(value);
        }
    }

    public void clearUuidContext(String uuid) {
        methods.removeIf(value -> value.getContext().getBindings("js").getMember("__uuid__").asString().equals(uuid));
    }
}
