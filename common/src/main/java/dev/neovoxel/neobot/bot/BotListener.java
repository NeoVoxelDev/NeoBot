package dev.neovoxel.neobot.bot;

import dev.neovoxel.nbapi.action.get.GetGroupMemberInfo;
import dev.neovoxel.nbapi.action.get.GetGroupMemberList;
import dev.neovoxel.nbapi.action.set.*;
import dev.neovoxel.nbapi.event.NEvent;
import dev.neovoxel.nbapi.event.message.GroupMessageEvent;
import dev.neovoxel.nbapi.event.message.PrivateMessageEvent;
import dev.neovoxel.nbapi.event.notice.*;
import dev.neovoxel.nbapi.event.request.FriendRequestEvent;
import dev.neovoxel.nbapi.event.request.GroupRequestEvent;
import dev.neovoxel.nbapi.event.request.GroupRequestType;
import dev.neovoxel.nbapi.listener.NBotEventHandler;
import dev.neovoxel.nbapi.listener.NBotListener;
import dev.neovoxel.neobot.NeoBot;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.json.JSONArray;

import java.util.IdentityHashMap;
import java.util.Map;

public class BotListener implements NBotListener {
    private final NeoBot plugin;

    private final Map<Value, String> map = new IdentityHashMap<>();
    
    public BotListener(NeoBot plugin) {
        this.plugin = plugin;
    }

    @HostAccess.Export
    public void sendGroupMessage(long groupId, String message) {
        plugin.getBot().action(new SendGroupMessage(groupId, new JSONArray(message)));
    }

    @HostAccess.Export
    public void sendPrivateMessage(long userId, String message) {
        plugin.getBot().action(new SendPrivateMessage(userId, new JSONArray(message)));
    }

    @HostAccess.Export
    public void renameGroupMember(long groupId, long userId, String name) {
        plugin.getBot().action(new SetGroupCard(groupId, userId, name));
    }

    @HostAccess.Export
    public void muteGroupMember(long groupId, long userId, int duration) {
        plugin.getBot().action(new SetGroupBan(groupId, userId, duration));
    }

    @HostAccess.Export
    public void muteAllGroupMember(long groupId) {
        plugin.getBot().action(new SetGroupWholeBan(groupId, true));
    }

    @HostAccess.Export
    public void unMuteAllGroupMember(long groupId) {
        plugin.getBot().action(new SetGroupWholeBan(groupId, false));
    }

    @HostAccess.Export
    public void kickGroupMember(long groupId, long userId) {
        plugin.getBot().action(new SetGroupKick(groupId, userId));
    }

    @HostAccess.Export
    public void approveGroupRequest(String flag, String type) {
        plugin.getBot().action(new SetGroupAddRequest(flag, GroupRequestType.from(type)));
    }

    @HostAccess.Export
    public void rejectGroupRequest(String flag, String type) {
        plugin.getBot().action(new SetGroupAddRequest(flag, GroupRequestType.from(type), false));
    }

    @HostAccess.Export
    public void approveFriendRequest(String flag) {
        plugin.getBot().action(new SetFriendAddRequest(flag));
    }

    @HostAccess.Export
    public void rejectFriendRequest(String flag) {
        plugin.getBot().action(new SetFriendAddRequest(flag, false));
    }

    @HostAccess.Export
    public void getGroupMemberInfo(long groupId, long userId, Value method) {
        if (method.canExecute()) {
            plugin.getBot().action(new GetGroupMemberInfo(groupId, userId), method::execute);
        }
    }

    @HostAccess.Export
    public void getGroupMemberList(long groupId, Value method) {
        if (method.canExecute()) {
            plugin.getBot().action(new GetGroupMemberList(groupId), method::execute);
        }
    }

    public void register(String eventName, Value method) {
        if (method.canExecute()) map.put(method, eventName);
    }

    private void fireEvent(String eventName, NEvent event) {
        for (Map.Entry<Value, String> entry : map.entrySet()) {
            if (entry.getValue().equals(eventName)) {
                entry.getKey().execute(event);
            }
        }
    }
    
    @NBotEventHandler
    private void onGroupMessage(GroupMessageEvent event) {
        fireEvent("GroupMessageEvent", event);
    }

    @NBotEventHandler
    private void onPrivateMessage(PrivateMessageEvent event) {
        fireEvent("PrivateMessageEvent", event);
    }

    @NBotEventHandler
    private void onFriendAdd(FriendAddEvent event) {
        fireEvent("FriendAddEvent", event);
    }

    @NBotEventHandler
    private void onGroupDecrease(GroupDecreaseEvent event) {
        fireEvent("GroupDecreaseEvent", event);
    }

    @NBotEventHandler
    private void onGroupIncrease(GroupIncreaseEvent event) {
        fireEvent("GroupIncreaseEvent", event);
    }

    @NBotEventHandler
    private void onPoke(PokeEvent event) {
        fireEvent("PokeEvent", event);
    }

    @NBotEventHandler
    private void onFriendRequest(FriendRequestEvent event) {
        fireEvent("FriendRequestEvent", event);
    }

    @NBotEventHandler
    private void onGroupRequest(GroupRequestEvent event) {
        fireEvent("GroupRequestEvent", event);
    }
}
