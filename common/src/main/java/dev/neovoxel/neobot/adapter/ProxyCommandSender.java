package dev.neovoxel.neobot.adapter;

public class ProxyCommandSender extends CommandSender {

    protected ProxyCommandSender() {
        super("INTERNAL PROXY COMMAND SENDER");
    }

    @Override
    public void sendMessage(String message) {}

    @Override
    public boolean hasPermission(String node) {
        return true;
    }
}
