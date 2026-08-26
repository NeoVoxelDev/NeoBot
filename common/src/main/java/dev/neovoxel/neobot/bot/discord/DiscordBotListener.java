package dev.neovoxel.neobot.bot.discord;

import dev.neovoxel.nbapi.discord.client.DiscordClient;
import dev.neovoxel.nbapi.discord.data.DiscordMessage;
import dev.neovoxel.nbapi.discord.event.DiscordEvent;
import dev.neovoxel.nbapi.discord.event.DiscordMessageDeleteEvent;
import dev.neovoxel.nbapi.discord.event.DiscordMessageEvent;
import dev.neovoxel.nbapi.discord.event.DiscordReadyEvent;
import dev.neovoxel.nbapi.listener.NBotEventHandler;
import dev.neovoxel.nbapi.listener.NBotListener;
import dev.neovoxel.neobot.NeoBot;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Collection;

/** Script-facing Discord bridge. Channel IDs stay Discord channel IDs. */
public class DiscordBotListener implements NBotListener {
    private final NeoBot plugin;
    private final Map<Value, String> handlers = new ConcurrentHashMap<>();

    public DiscordBotListener(NeoBot plugin) {
        this.plugin = plugin;
    }

    @HostAccess.Export
    public void sendMessage(long channelId, String message) {
        sendMessageString(Long.toString(channelId), message);
    }
    @HostAccess.Export
    public void sendMessage(String channelId, String message) {
        sendMessageString(channelId, message);
    }

    /** Connected and disconnected Discord clients, exposed independently from OneBot clients. */
    @HostAccess.Export
    public Collection<DiscordClient> getClients() {
        return plugin.getBotProvider().getDiscordClients();
    }
    public void sendMessageString(String channelId, String message) {
        sendMessageStringAndWait(channelId, message, 0);
    }

    public boolean sendMessageStringAndWait(String channelId, String message, int timeoutSeconds) {
        List<CompletableFuture<DiscordMessage>> sends = new ArrayList<>();
        for (DiscordClient client : plugin.getBotProvider().getDiscordClients()) {
            if (client.isConnected()) {
                try {
                    CompletableFuture<DiscordMessage> send = client.sendMessage(
                            dev.neovoxel.nbapi.discord.DiscordSnowflake.parse(channelId), message);
                    sends.add(send);
                    send.exceptionally(error -> {
                    plugin.getNeoLogger().error("Failed to send a Discord message", error);
                    return null;
                    });
                } catch (IllegalArgumentException ignored) { }
            }
        }
        if (sends.isEmpty()) return false;
        if (timeoutSeconds <= 0) return true;
        try {
            CompletableFuture.allOf(sends.toArray(new CompletableFuture<?>[0]))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            return true;
        } catch (Exception error) {
            plugin.getNeoLogger().warn("Timed out while sending a Discord message: " + error.getMessage());
            return false;
        }
    }

    @HostAccess.Export
    public void editMessage(long channelId, long messageId, String message) {
        for (DiscordClient client : plugin.getBotProvider().getDiscordClients()) {
            if (client.isConnected()) {
                client.editMessage(channelId, messageId, message).exceptionally(error -> {
                    plugin.getNeoLogger().error("Failed to edit a Discord message", error);
                    return null;
                });
            }
        }
    }

    @HostAccess.Export
    public void editMessage(String channelId, String messageId, String message) {
        editMessage(dev.neovoxel.nbapi.discord.DiscordSnowflake.parse(channelId),
                dev.neovoxel.nbapi.discord.DiscordSnowflake.parse(messageId), message);
    }

    @HostAccess.Export
    public void deleteMessage(long channelId, long messageId) {
        for (DiscordClient client : plugin.getBotProvider().getDiscordClients()) {
            if (client.isConnected()) {
                client.deleteMessage(channelId, messageId).exceptionally(error -> {
                    plugin.getNeoLogger().error("Failed to delete a Discord message", error);
                    return null;
                });
            }
        }
    }

    @HostAccess.Export
    public void deleteMessage(String channelId, String messageId) {
        deleteMessage(dev.neovoxel.nbapi.discord.DiscordSnowflake.parse(channelId),
                dev.neovoxel.nbapi.discord.DiscordSnowflake.parse(messageId));
    }

    @HostAccess.Export
    public void register(String eventName, Value method) {
        if (method.canExecute()) handlers.put(method, eventName);
    }

    public void clearUuidContext(String uuid) {
        List<Value> toRemove = new ArrayList<>();
        for (Value value : handlers.keySet()) {
            if (value.getContext().getBindings("js").getMember("__uuid__").asString().equals(uuid)) {
                toRemove.add(value);
            }
        }
        toRemove.forEach(handlers::remove);
    }

    public void reset() {
        handlers.clear();
    }

    @NBotEventHandler
    private void onDiscordEvent(DiscordEvent event) {
        if (event instanceof DiscordMessageEvent && "MESSAGE_CREATE".equals(event.getEventName())
                && ((DiscordMessageEvent) event).getMessage().getAuthor() != null) {
            DiscordMessageEvent messageEvent = (DiscordMessageEvent) event;
            String channelId = Long.toString(messageEvent.getMessage().getChannelId());
            String content = messageEvent.getMessage().getContent() == null ? "" : messageEvent.getMessage().getContent();
            String userId = Long.toString(messageEvent.getMessage().getAuthor().getId());
            if (plugin.getDiscordService().isServerMessagesChannel(channelId)) {
                if (!isEcho(messageEvent.getMessage(), messageEvent.getSelfId(), channelId,
                        plugin.getDiscordService().ignoresSelfMessages(), plugin.getDiscordService().ignoresBotMessages())) {
                    String eventName; Object context;
                    if (plugin.getDiscordService().looksLikeRemoteCommand(content)) {
                        eventName = "RemoteCommandEvent";
                        context = new dev.neovoxel.neobot.script.InboundCommandContext(content, userId, "discord:" + channelId, "discord");
                    } else {
                        eventName = "DiscordMessageEvent";
                        context = messageEvent;
                    }
                    dispatchAndExecute(eventName, context);
                }
                fireEvent("DiscordEvent", event); fireEvent(scriptEventName(event), event); return;
            } else if (plugin.getDiscordService().isBindChannel(channelId)) {
                String bindEvent = bindChannelEventName(content);
                Object context = new dev.neovoxel.neobot.script.InboundCommandContext(content, userId, "discord:" + channelId, "discord");
                dispatchAndExecute(bindEvent, context);
                fireEvent("DiscordEvent", event); fireEvent(scriptEventName(event), event); return;
            }
        }
        fireEvent("DiscordEvent", event);
        fireEvent(scriptEventName(event), event);
    }

    /** True when this message is the bot echoing its own forward back into the channel it came from,
     *  which would otherwise loop it straight back into Minecraft. */
    static boolean isEcho(dev.neovoxel.nbapi.discord.data.DiscordMessage message, long selfId, String channelId,
                          boolean ignoreSelf, boolean ignoreBots) {
        return !dev.neovoxel.neobot.discord.DiscordForwardingPolicy.accepts(
                message, selfId, java.util.Collections.singleton(channelId), ignoreSelf, ignoreBots);
    }

    /** Same bind/unbind detection semantics as the QQ side: "bind"/"unbind" tokens, slash optional. */
    static String bindChannelEventName(String content) {
        String action = dev.neovoxel.neobot.bot.BotListener.bindOrUnbindAction(content);
        return "unbind".equals(action) ? "UnbindEvent" : "BindEvent";
    }

    private void dispatchAndExecute(String eventName, Object context) {
        dev.neovoxel.neobot.script.ScriptDispatchResult result = plugin.getScriptProvider() == null
                ? dev.neovoxel.neobot.script.ScriptDispatchResult.rejected()
                : plugin.getScriptProvider().dispatchBusiness(eventName, context);
        if (plugin.getScriptProvider() == null) {
            plugin.getNeoLogger().warn(eventName + " rejected: script system is not loaded");
            return;
        }
        plugin.getScriptProvider().executeBusinessActions(result);
    }

    static String scriptEventName(DiscordEvent event) {
        if (event instanceof DiscordReadyEvent) return "DiscordReadyEvent";
        if (event instanceof DiscordMessageDeleteEvent) return "DiscordMessageDeleteEvent";
        if (event instanceof DiscordMessageEvent) {
            return "MESSAGE_UPDATE".equals(event.getEventName())
                    ? "DiscordMessageUpdateEvent" : "DiscordMessageCreateEvent";
        }
        return "DiscordRawEvent";
    }

    private void fireEvent(String eventName, DiscordEvent event) {
        try {
            for (Map.Entry<Value, String> entry : handlers.entrySet()) {
                if (entry.getValue().equals(eventName)) entry.getKey().execute(event);
            }
        } catch (Exception error) {
            plugin.getNeoLogger().error("Failed to fire Discord event", error);
        }
    }
}
