package dev.neovoxel.neobot.adapter;

import com.hypixel.hytale.server.core.Message;
import org.graalvm.polyglot.HostAccess;
import com.hypixel.hytale.server.core.command.system.CommandSender;
public class HytaleCommandSender extends dev.neovoxel.neobot.adapter.CommandSender {
    private final CommandSender sender;

    public HytaleCommandSender(CommandSender sender) {
        super(sender.getDisplayName());
        this.sender = sender;
    }

    @HostAccess.Export
    @Override
    public void sendMessage(String message) {
        sender.sendMessage(Message.parse(message));
    }

    @HostAccess.Export
    @Override
    public boolean hasPermission(String node) {
        return sender.hasPermission(node);
    }
}
