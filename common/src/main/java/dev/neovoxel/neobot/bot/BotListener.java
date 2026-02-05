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
import dev.neovoxel.neobot.misc.EventListener;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BotListener extends EventListener implements NBotListener {
    
    public BotListener(NeoBot plugin) {
        super(plugin);
    }

    @HostAccess.Export
    public void sendGroupMessage(long groupId, String message) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SendGroupMessage(groupId, new JSONArray(message)));
            }
        });
    }

    @HostAccess.Export
    public void sendPrivateMessage(long userId, String message) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SendPrivateMessage(userId, new JSONArray(message)));
            }
        });
    }

    @HostAccess.Export
    public void renameGroupMember(long groupId, long userId, String name) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupCard(groupId, userId, name));
            }
        });
    }

    @HostAccess.Export
    public void muteGroupMember(long groupId, long userId, int duration) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupBan(groupId, userId, duration));
            }
        });
    }

    @HostAccess.Export
    public void muteAllGroupMember(long groupId) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupWholeBan(groupId, true));
            }
        });
    }

    @HostAccess.Export
    public void unMuteAllGroupMember(long groupId) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupWholeBan(groupId, false));
            }
        });
    }

    @HostAccess.Export
    public void kickGroupMember(long groupId, long userId) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupKick(groupId, userId));
            }
        });
    }

    @HostAccess.Export
    public void approveGroupRequest(String flag, String type) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupAddRequest(flag, GroupRequestType.from(type)));
            }
        });
    }

    @HostAccess.Export
    public void rejectGroupRequest(String flag, String type) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupAddRequest(flag, GroupRequestType.from(type), false));
            }
        });
    }

    @HostAccess.Export
    public void approveFriendRequest(String flag) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetFriendAddRequest(flag));
            }
        });
    }

    @HostAccess.Export
    public void rejectFriendRequest(String flag) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetFriendAddRequest(flag, false));
            }
        });
    }

    @HostAccess.Export
    public void setGroupSpecialTitle(long groupId, long userId, String title, long duration) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupSpecialTitle(groupId, userId, title, duration));
            }
        });
    }

    @HostAccess.Export
    public void setGroupWholeBan(long groupId, boolean enable) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new SetGroupWholeBan(groupId, enable));
            }
        });
    }

    @HostAccess.Export
    public void recallMessage(long messageId) {
        getPlugin().getBotProvider().getBot().forEach(client -> {
            if (client.isConnected()) {
                client.action(new DeleteMessage(messageId));
            }
        });
    }

    @HostAccess.Export
    public void getGroupMemberInfo(long groupId, long userId, Value method) {
        if (method.canExecute()) {
            getPlugin().getBotProvider().getBot().forEach(client -> {
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
            getPlugin().getBotProvider().getBot().forEach(client -> {
                if (client.isConnected()) {
                    client.action(new GetGroupMemberList(groupId), method::execute);
                } else method.execute(new ArrayList<>());
            });
        }
    }

    @HostAccess.Export
    public void getFriendList(Value method) {
        if (method.canExecute()) {
            getPlugin().getBotProvider().getBot().forEach(client -> {
                if (client.isConnected()) {
                    client.action(new GetFriendList(), method::execute);
                } else method.execute(new ArrayList<>());
            });
        }
    }

    @HostAccess.Export
    public void getGroupList(Value method) {
        if (method.canExecute()) {
            getPlugin().getBotProvider().getBot().forEach(client -> {
                if (client.isConnected()) {
                    client.action(new GetGroupList(), method::execute);
                } else method.execute(new ArrayList<>());
            });
        }
    }

    @HostAccess.Export
    public void getGroupInfo(long groupId, Value method) {
        if (method.canExecute()) {
            getPlugin().getBotProvider().getBot().forEach(client -> {
                if (client.isConnected()) {
                    client.action(new GetGroupInfo(groupId), method::execute);
                } else method.execute(new ArrayList<>());
            });
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
