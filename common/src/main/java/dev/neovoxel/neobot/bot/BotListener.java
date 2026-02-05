package dev.neovoxel.neobot.bot;

import dev.neovoxel.nbapi.action.get.*;
import dev.neovoxel.nbapi.action.set.SendPrivateMessage;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BotListener implements NBotListener {
    private final NeoBot plugin;

    private final Map<Value, String> map = new LinkedHashMap<>();
    
    public BotListener(NeoBot plugin) {
        this.plugin = plugin;
    }

    @HostAccess.Export
    public void sendGroupMessage(long groupId, String message) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SendGroupMessage(groupId, new JSONArray(message)));
            }
        });
    }

    public void clearUuidContext(String uuid) {
        List<Value> toRemove = new ArrayList<>();
        for (Map.Entry<Value, String> entry : map.entrySet()) {
            if (entry.getKey().getContext().getBindings("js").getMember("__uuid__").asString().equals(uuid)) {
                toRemove.add(entry.getKey());
            }
        }
        for (Value value : toRemove) {
            map.remove(value);
        }
    }

    @HostAccess.Export
    public void sendPrivateMessage(long userId, String message) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SendPrivateMessage(userId, new JSONArray(message)));
            }
        });
    }

    @HostAccess.Export
    public void renameGroupMember(long groupId, long userId, String name) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupCard(groupId, userId, name));
            }
        });
    }

    @HostAccess.Export
    public void muteGroupMember(long groupId, long userId, int duration) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupBan(groupId, userId, duration));
            }
        });
    }

    @HostAccess.Export
    public void muteAllGroupMember(long groupId) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupWholeBan(groupId, true));
            }
        });
    }

    @HostAccess.Export
    public void unMuteAllGroupMember(long groupId) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupWholeBan(groupId, false));
            }
        });
    }

    @HostAccess.Export
    public void kickGroupMember(long groupId, long userId) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupKick(groupId, userId));
            }
        });
    }

    @HostAccess.Export
    public void approveGroupRequest(String flag, String type) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupAddRequest(flag, GroupRequestType.from(type)));
            }
        });
    }

    @HostAccess.Export
    public void rejectGroupRequest(String flag, String type) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupAddRequest(flag, GroupRequestType.from(type), false));
            }
        });
    }

    @HostAccess.Export
    public void approveFriendRequest(String flag) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetFriendAddRequest(flag));
            }
        });
    }

    @HostAccess.Export
    public void rejectFriendRequest(String flag) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetFriendAddRequest(flag, false));
            }
        });
    }

    @HostAccess.Export
    public void setGroupSpecialTitle(long groupId, long userId, String title, long duration) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupSpecialTitle(groupId, userId, title, duration));
            }
        });
    }

    @HostAccess.Export
    public void setGroupWholeBan(long groupId, boolean enable) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupWholeBan(groupId, enable));
            }
        });
    }

    @HostAccess.Export
    public void recallMessage(long messageId) {
        plugin.getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new DeleteMessage(messageId));
            }
        });
    }

    @HostAccess.Export
    public void getGroupMemberInfo(long groupId, long userId, Value method) {
        if (method.canExecute()) {
            plugin.getBotProvider().getBot().forEach(client -> {
                if (client.isConnected()) {
                    client.action(new GetGroupMemberInfo(groupId, userId), method::execute);
                } else {
                    method.execute(new Object());
                }
            });
        }
    }

    @HostAccess.Export
    public void getGroupMemberList(long groupId, Value method) {
        if (method.canExecute()) {
            plugin.getBotProvider().getBot().forEach(client -> {
                if (client.isConnected()) {
                    client.action(new GetGroupMemberList(groupId), method::execute);
                } else method.execute(new ArrayList<>());
            });
        }
    }

    @HostAccess.Export
    public void getFriendList(Value method) {
        if (method.canExecute()) {
            plugin.getBotProvider().getBot().forEach(client -> {
                if (client.isConnected()) {
                    client.action(new GetFriendList(), method::execute);
                } else method.execute(new ArrayList<>());
            });
        }
    }

    @HostAccess.Export
    public void getGroupList(Value method) {
        if (method.canExecute()) {
            plugin.getBotProvider().getBot().forEach(client -> {
                if (client.isConnected()) {
                    client.action(new GetGroupList(), method::execute);
                } else method.execute(new ArrayList<>());
            });
        }
    }

    @HostAccess.Export
    public void getGroupInfo(long groupId, Value method) {
        if (method.canExecute()) {
            plugin.getBotProvider().getBot().forEach(client -> {
                if (client.isConnected()) {
                    client.action(new GetGroupInfo(groupId), method::execute);
                } else method.execute(new ArrayList<>());
            });
        }
    }

    @HostAccess.Export
    public void register(String eventName, Value method) {
        if (method.canExecute()) map.put(method, eventName);
    }

    public void reset() {
        map.clear();
    }

    private void fireEvent(String eventName, NEvent event) {
        try {
            for (Map.Entry<Value, String> entry : map.entrySet()) {
                if (entry.getValue().equals(eventName)) {
                    entry.getKey().execute(event);
                }
            }
        } catch (Exception e) {
            plugin.getNeoLogger().error("Failed to fire event", e);
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
