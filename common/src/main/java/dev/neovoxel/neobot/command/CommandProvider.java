package dev.neovoxel.neobot.command;

import dev.neovoxel.nbapi.client.NBotClient;
import dev.neovoxel.nbapi.client.OBWSClient;
import dev.neovoxel.nbapi.client.OBWSServer;
import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.adapter.CommandSender;
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

    protected CommandProvider(NeoBot plugin) {
        this.plugin = plugin;
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
                            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.status.data.bot_head"));
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
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("reload")) {
                    if (sender.hasPermission("neobot.command.script")) {
                        plugin.getNeoLogger().info("Reloading scripts...");
                        plugin.getScriptScheduler().cancelAllTasks();
                        plugin.getScriptProvider().setScriptSystemLoaded(false);
                        plugin.getBotProvider().getBotListener().reset();
                        plugin.getGameEventListener().reset();
                        plugin.getScriptProvider().unloadScript();
                        try {
                            plugin.getScriptProvider().loadScript(plugin);
                        } catch (Throwable e) {
                            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.script.reload.error")
                                    .replace("${error}", e.getMessage()));
                        }
                        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.script.reload.success"));
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                }
            } else if (args[0].equalsIgnoreCase("repo")) {
                if (args[1].equalsIgnoreCase("list")) {
                    if (sender.hasPermission("neobot.command.repo")) {
                        DatabaseTable table = plugin.getStorageProvider().getStorage()
                                .table("neobot_repo");
                        table.create()
                                .column("url", "TEXT", "PRIMARY KEY")
                                .column("name", "TEXT")
                                .execute();
                        List<Row> result = table.select("url", "name").execute().map();
                        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.list.head"));
                        for (Row row : result) {
                            String name = row.getString("name");
                            String url = row.getString("url");
                            if (Objects.equals(name, url)) {
                                for (String message : plugin.getMessageConfig().getStringArray("internal.repo.list.single-without-name")) {
                                    message = message.replace("${url}", url);
                                    sender.sendMessage(message);
                                }
                            } else {
                                for (String message : plugin.getMessageConfig().getStringArray("internal.repo.list.single-with-name")) {
                                    message = message
                                            .replace("${name}", name)
                                            .replace("${url}", url);
                                    sender.sendMessage(message);
                                }
                            }
                        }
                    } else {
                        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("script")) {
                if (args[1].equalsIgnoreCase("load")) {
                    if (sender.hasPermission("neobot.command.script")) {
                        sender.sendMessage(plugin.getScriptProvider().loadScript(plugin, args[2]));
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("unload")) {
                    if (sender.hasPermission("neobot.command.script")) {
                        boolean result = plugin.getScriptProvider().unloadScript(args[2]);
                        if (result) {
                            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.script.unload.success"));
                        } else {
                            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.script.unload.error"));
                        }
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("info")) {
                    if (sender.hasPermission("neobot.command.script")) {
                        Script script = plugin.getScriptProvider().getScriptInfo(args[2]);
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
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                }
            } else if (args[0].equalsIgnoreCase("repo")) {
                if (args[1].equalsIgnoreCase("add")) {
                    if (sender.hasPermission("neobot.command.repo")) {
                        String url = args[2];
                        DatabaseTable table = plugin.getStorageProvider().getStorage()
                                .table("neobot_repo");
                        table.create()
                                .column("url", "TEXT", "PRIMARY KEY")
                                .column("name", "TEXT")
                                .execute();
                        if (!table.select("url").where("url", url).execute().map().isEmpty()) {
                            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.add.exists"));
                            return;
                        }
                        plugin.getStorageProvider().getStorage()
                                .table("neobot_repo")
                                .insert()
                                .column("url", url)
                                .column("name", url)
                                .execute();
                        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.add.success")
                                .replace("${url}", url));
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("remove")) {
                    if (sender.hasPermission("neobot.command.repo")) {
                        String url = args[2];
                        DatabaseTable table = plugin.getStorageProvider().getStorage()
                                .table("neobot_repo");
                        table.create()
                                .column("url", "TEXT", "PRIMARY KEY")
                                .column("name", "TEXT")
                                .execute();
                        if (table.select("url").where("url", url).execute().map().isEmpty()) {
                            List<Row> results = table.select("url").where("name", url).execute().map();
                            if (results.isEmpty()) {
                                sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.remove.not-found"));
                            } else {
                                table.delete()
                                        .where("name", url)
                                        .execute();
                                sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.remove.success")
                                        .replace("${name}", url)
                                        .replace("${url}", results.get(0).getString("url")));
                            }
                        } else {
                            table.delete()
                                    .where("url", url)
                                    .execute();
                            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.remove.success")
                                    .replace("${name}", url)
                                    .replace("${url}", url));
                        }
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("scriptlist")) {
                    if (sender.hasPermission("neobot.command.repo")) {
                        String arg = args[2];
                        String url;
                        DatabaseTable table = plugin.getStorageProvider().getStorage()
                                .table("neobot_repo");
                        table.create()
                                .column("url", "TEXT", "PRIMARY KEY")
                                .column("name", "TEXT")
                                .execute();
                        if (table.select("url").where("url", arg).execute().map().isEmpty()) {
                            if (table.select("url").where("name", arg).execute().map().isEmpty()) {
                                sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.scriptlist.not-found"));
                                return;
                            } else {
                                url = table.select("url")
                                        .where("name", arg)
                                        .execute()
                                        .getFirst()
                                        .getString("url");
                            }
                        } else {
                            url = arg;
                        }
                        plugin.submitAsync(() -> {
                            Repository repo = new Repository(url);
                            try {
                                sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.scriptlist.head"));
                                repo.fetch(plugin.getGeneralConfig().getBoolean("repository.use-github-proxy"));
                                repo.getScripts().forEach(script -> {
                                    for (String message : plugin.getMessageConfig().getStringArray("internal.repo.scriptlist.single")) {
                                        sender.sendMessage(message
                                                .replace("${id}", script.getId())
                                                .replace("${name}", script.getName())
                                                .replace("${version}", script.getVersion())
                                                .replace("${author}", script.getAuthor())
                                                .replace("${description}", script.getDescription()));
                                    }
                                });
                            } catch (Exception e) {
                                sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.scriptlist.error")
                                        .replace("${error}", e.getMessage()));
                            }
                        });
                    } else sender.sendMessage(plugin.getMessageConfig().getMessage("internal.no-permission"));
                } else if (args[1].equalsIgnoreCase("install")) {
                    if (sender.hasPermission("neobot.command.repo")) {
                        String id = args[2];
                        plugin.submitAsync(() -> {
                            DatabaseTable table = plugin.getStorageProvider().getStorage()
                                    .table("neobot_repo");
                            table.create()
                                    .column("url", "TEXT", "PRIMARY KEY")
                                    .column("name", "TEXT")
                                    .execute();
                            List<Row> list = table.select("name", "url")
                                    .execute()
                                    .map();
                            for (Row row : list) {
                                sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.install.fetching")
                                        .replace("${name}", row.getString("name")));
                                String url = row.getString("url");
                                Repository repository = new Repository(url);
                                boolean finished = false;
                                try {
                                    repository.fetch(plugin.getGeneralConfig().getBoolean("repository.use-github-proxy"));
                                    for (RemoteScript script : repository.getScripts()) {
                                        if (script.getId().equalsIgnoreCase(id)) {
                                            finished = true;
                                            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.install.start"));
                                            String dir = script.download(plugin);
                                            if (dir.isEmpty()) {
                                                sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.install.success-download")
                                                        .replace("${id}", script.getId())
                                                        .replace("${name}", script.getName())
                                                        .replace("${author}", script.getAuthor())
                                                        .replace("${version}", script.getVersion()));
                                            } else {
                                                plugin.getScriptProvider().loadScript(plugin, dir);
                                                sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.install.success")
                                                    .replace("${id}", script.getId())
                                                    .replace("${name}", script.getName())
                                                    .replace("${author}", script.getAuthor())
                                                    .replace("${version}", script.getVersion()));
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.install.error")
                                            .replace("${error}", e.getMessage()));
                                }
                                if (finished) break;
                            }
                        });
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
