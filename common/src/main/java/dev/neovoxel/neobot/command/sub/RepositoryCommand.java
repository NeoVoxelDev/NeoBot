package dev.neovoxel.neobot.command.sub;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.adapter.CommandSender;
import dev.neovoxel.neobot.script.remote.RemoteScript;
import dev.neovoxel.neobot.script.remote.Repository;
import dev.neovoxel.nsapi.entity.Row;
import dev.neovoxel.nsapi.table.DatabaseTable;

import java.util.List;
import java.util.Objects;

public class RepositoryCommand {
    private final NeoBot plugin;

    public RepositoryCommand(NeoBot plugin) {
        this.plugin = plugin;
    }

    public void add(String url, CommandSender sender) {
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
    }

    public void install(String id, CommandSender sender) {
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
                if (finished) return;
            }
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.install.not-found"));
        });
    }

    public void install(String id, CommandSender sender, Runnable callback) {
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
                            callback.run();
                        }
                    }
                } catch (Exception e) {
                    sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.install.error")
                            .replace("${error}", e.getMessage()));
                }
                if (finished) return;
            }
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.install.not-found"));
        });
    }

    public void list(CommandSender sender) {
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
    }

    public void remove(String arg, CommandSender sender) { // url can be a name
        DatabaseTable table = plugin.getStorageProvider().getStorage()
                .table("neobot_repo");
        table.create()
                .column("url", "TEXT", "PRIMARY KEY")
                .column("name", "TEXT")
                .execute();
        if (table.select("url").where("url", arg).execute().map().isEmpty()) {
            List<Row> results = table.select("url").where("name", arg).execute().map();
            if (results.isEmpty()) {
                sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.remove.not-found"));
            } else {
                table.delete()
                        .where("name", arg)
                        .execute();
                sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.remove.success")
                        .replace("${name}", arg)
                        .replace("${url}", results.get(0).getString("url")));
            }
        } else {
            table.delete()
                    .where("url", arg)
                    .execute();
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.repo.remove.success")
                    .replace("${name}", arg)
                    .replace("${url}", arg));
        }
    }

    public void scriptlist(String arg, CommandSender sender) {
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
    }
}
