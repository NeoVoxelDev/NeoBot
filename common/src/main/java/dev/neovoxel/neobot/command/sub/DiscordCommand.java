package dev.neovoxel.neobot.command.sub;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.adapter.CommandSender;
import dev.neovoxel.neobot.discord.model.DiscordAccountBinding;
import dev.neovoxel.neobot.discord.model.DiscordChannelBinding;

import java.util.List;

public class DiscordCommand {
    private final NeoBot plugin;
    public DiscordCommand(NeoBot plugin) { this.plugin = plugin; }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("neobot.command.discord")) { sender.sendMessage("&cYou don't have permission to manage Discord."); return; }
        if (args.length < 2 || "help".equalsIgnoreCase(args[1])) { help(sender); return; }
        if ("channel".equalsIgnoreCase(args[1])) channel(sender, args);
        else if ("account".equalsIgnoreCase(args[1])) account(sender, args);
        else sender.sendMessage("&cUnknown Discord command. Use /neobot discord help.");
    }
    private void channel(CommandSender sender, String[] args) {
        if (args.length == 3 && "list".equalsIgnoreCase(args[2])) {
            List<DiscordChannelBinding> list = plugin.getDiscordService().channels();
            if (list.isEmpty()) sender.sendMessage("&eNo Discord channels are bound.");
            for (DiscordChannelBinding binding : list) sender.sendMessage("&aGuild " + binding.getGuildId() + " channel " + binding.getChannelId());
            return;
        }
        if (args.length != 5) { sender.sendMessage("&cUsage: /neobot discord channel <bind|unbind> <guild-id> <channel-id>"); return; }
        boolean bind = "bind".equalsIgnoreCase(args[2]);
        boolean unbind = "unbind".equalsIgnoreCase(args[2]);
        if (!bind && !unbind) { sender.sendMessage("&cUse bind or unbind."); return; }
        boolean result = bind ? plugin.getDiscordService().bindChannel(args[3], args[4]) : plugin.getDiscordService().unbindChannel(args[3], args[4]);
        sender.sendMessage(result ? "&aDiscord channel binding updated." : bind ? "&cInvalid or already-bound Discord guild/channel ID." : "&cThat Discord channel is not bound.");
    }
    private void account(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage("&cUsage: /neobot discord account <bind|unlink|status> ..."); return; }
        String action = args[2].toLowerCase(java.util.Locale.ROOT);
        if ("bind".equals(action) && args.length == 5) {
            dev.neovoxel.neobot.adapter.OfflinePlayer player = plugin.getOfflinePlayer(args[3]);
            sender.sendMessage(plugin.getDiscordService().adminBind(player.getName(), player.getUuid(), args[4]) ? "&aDiscord 账号绑定成功。" : "&c无效的 Discord ID、该玩家已被绑定，或已达到绑定数量上限。");
        } else if ("unlink".equals(action) && args.length == 4) {
            sender.sendMessage(plugin.getDiscordService().unbindAccount(plugin.getOfflinePlayer(args[3]).getUuid()) ? "&aDiscord 账号解绑成功。" : "&c该 Minecraft 账号未绑定 Discord。");
        } else if ("status".equals(action) && args.length == 4) {
            DiscordAccountBinding binding = plugin.getDiscordService().account(plugin.getOfflinePlayer(args[3]).getUuid());
            sender.sendMessage(binding == null ? "&eNo Discord binding." : "&aDiscord user: " + binding.getDiscordUserId());
        } else sender.sendMessage("&cInvalid account command.");
    }
    private void help(CommandSender sender) {
        sender.sendMessage("&6/neobot discord channel bind <guild-id> <channel-id>");
        sender.sendMessage("&6/neobot discord channel unbind <guild-id> <channel-id>");
        sender.sendMessage("&6/neobot discord channel list");
        sender.sendMessage("&6/neobot discord account bind <player> <discord-user-id>");
        sender.sendMessage("&6/neobot discord account unlink|status <player>");
    }
}
