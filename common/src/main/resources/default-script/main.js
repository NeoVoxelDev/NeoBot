// Default business policy. Platform adapters publish events and execute only the actions this script returns.
// There is no Java fallback; concrete deployments should replace this script with their own policy.
gameEvent.register("LoginEvent", function (event) {
  var uuid = event.getUuid();
  if (business.configuredAccountRequireBinding() && !business.hasQqBinding(uuid) && !business.hasDiscordBinding(uuid)) {
    event.disallow(business.configuredAccountRequireBindingMessage().split("${player}").join(event.getName()));
  }
});
scriptManager.registerBusinessScript("MinecraftChatEvent", function (event) {
  var targets = [];
  var actions = [];
  var contentByAction = {};
  var player = event.getPlayer().getName();
  var message = event.getMessage();
  if (business.configuredChatForwardToQqEnabled()) {
    var groups = business.configuredQqGroups();
    for (var i = 0; i < groups.length; i++) targets.push("qq:" + groups[i]);
    if (groups.length > 0) {
      actions.push("sendToQQ");
      contentByAction.sendToQQ = business.configuredChatForwardToQqFormat().split("${player}").join(player).split("${message}").join(message);
    }
  }
  var channel = business.configuredDiscordChannel();
  if (channel) {
    targets.push("discord:" + channel);
    actions.push("sendToDiscord");
    contentByAction.sendToDiscord = business.configuredMinecraftToDiscordFormat().split("${player}").join(player).split("${message}").join(message);
  }
  return { handled: true, contentByAction: contentByAction, targets: targets, actions: actions };
});
scriptManager.registerBusinessScript("QQGroupMessageEvent", function (event) {
  if (!business.configuredChatForwardToGameEnabled()) return { handled: true, targets: [], actions: [] };
  var content = business.configuredChatForwardToGameFormat()
    .split("${group}").join(String(event.getGroupId()))
    .split("${user}").join(business.resolveQqNickname(event.getGroupId(), event.getSenderId()))
    .split("${message}").join(event.getRawMessage());
  return { handled: true, content: content, targets: ["minecraft:default"], actions: ["sendToMinecraft"] };
});
scriptManager.registerBusinessScript("DiscordMessageEvent", function (event) {
  var user = event.getMessage().getAuthor().getUsername();
  var channel = event.getMessage().getChannelId();
  var content = business.configuredDiscordToMinecraftFormat()
    .split("${channel}").join(business.resolveDiscordChannelName(String(channel)))
    .split("${user}").join(user)
    .split("${message}").join(event.getMessage().getContent());
  return { handled: true, content: content, targets: ["minecraft"], actions: ["sendToMinecraft"] };
});
function serverStatusTargets() {
  var targets = []; var actions = [];
  if (business.configuredQqServerStatusEnabled()) {
    var groups = business.configuredQqGroups();
    for (var i = 0; i < groups.length; i++) targets.push("qq:" + groups[i]);
    if (groups.length > 0) actions.push("sendToQQ");
  }
  if (business.configuredDiscordServerStatusEnabled()) {
    var channel = business.configuredDiscordChannel();
    if (channel) { targets.push("discord:" + channel); actions.push("sendToDiscord"); }
  }
  return { targets: targets, actions: actions };
}
scriptManager.registerBusinessScript("ServerStartEvent", function (event) {
  var destinations = serverStatusTargets();
  var content = business.configuredStartMessage().split("${server}").join(business.configuredServerName());
  return { handled: true, content: content, targets: destinations.targets, actions: destinations.actions };
});
scriptManager.registerBusinessScript("ServerStopEvent", function (event) {
  var destinations = serverStatusTargets();
  var content = business.configuredStopMessage().split("${server}").join(business.configuredServerName());
  return { handled: true, content: content, targets: destinations.targets, actions: destinations.actions };
});
function playerStatusTargets() {
  // Vanilla already broadcasts its own join/quit/death message in-game; only forward to QQ/Discord here.
  var targets = []; var actions = [];
  if (business.configuredQqPlayerStatusEnabled()) {
    var groups = business.configuredQqGroups();
    for (var i = 0; i < groups.length; i++) targets.push("qq:" + groups[i]);
    if (groups.length > 0) actions.push("sendToQQ");
  }
  if (business.configuredDiscordPlayerStatusEnabled()) {
    var channel = business.configuredDiscordChannel();
    if (channel) { targets.push("discord:" + channel); actions.push("sendToDiscord"); }
  }
  return { targets: targets, actions: actions };
}
scriptManager.registerBusinessScript("PlayerJoinEvent", function (event) {
  var destinations = playerStatusTargets();
  var content = business.configuredJoinMessage().split("${server}").join(business.configuredServerName()).split("${player}").join(event.getPlayer().getName());
  return { handled: true, content: content, targets: destinations.targets, actions: destinations.actions };
});
scriptManager.registerBusinessScript("PlayerQuitEvent", function (event) {
  var destinations = playerStatusTargets();
  var content = business.configuredQuitMessage().split("${server}").join(business.configuredServerName()).split("${player}").join(event.getPlayer().getName());
  return { handled: true, content: content, targets: destinations.targets, actions: destinations.actions };
});
scriptManager.registerBusinessScript("PlayerDeathEvent", function (event) {
  var destinations = playerStatusTargets();
  var content = business.configuredDeathMessage().split("${server}").join(business.configuredServerName()).split("${player}").join(event.getPlayer().getName());
  return { handled: true, content: content, targets: destinations.targets, actions: destinations.actions };
});
function bindResultText(result, isBind) {
  switch (result) {
    case "success": case "SUCCESS": return isBind ? "绑定成功!" : "解绑成功!";
    case "error:unknown-player": case "INVALID_PLAYER": return "未找到该 Minecraft 玩家。";
    case "error:already-bound-or-conflict": case "PLAYER_ALREADY_BOUND": return "该账号已被绑定。";
    case "error:not-owner-or-unbound": case "NOT_BOUND": return "该账号未绑定。";
    case "NOT_OWNER": return "只能解绑自己绑定的账号。";
    case "USER_MAX_BINDINGS": return "已达到绑定数量上限。";
    default: return "操作失败：" + result;
  }
}
scriptManager.registerBusinessScript("BindEvent", function (event) {
  var parts = String(event.getContent() || "").trim().split(/\s+/);
  if (parts.length !== 2) return { handled: true, cancelled: true };
  var platform = event.getPlatform();
  var result = business.bind(parts[1], event.getUserId(), platform);
  var action = platform === "qq" ? "sendToQQ" : "sendToDiscord";
  return { handled: true, content: bindResultText(result, true), targets: [event.getReplyTarget()], actions: [action] };
});
scriptManager.registerBusinessScript("UnbindEvent", function (event) {
  var parts = String(event.getContent() || "").trim().split(/\s+/);
  if (parts.length !== 2) return { handled: true, cancelled: true };
  var platform = event.getPlatform();
  var result = business.unbind(parts[1], event.getUserId(), platform);
  var action = platform === "qq" ? "sendToQQ" : "sendToDiscord";
  return { handled: true, content: bindResultText(result, false), targets: [event.getReplyTarget()], actions: [action] };
});
scriptManager.registerBusinessScript("RemoteCommandEvent", function (event) {
  var parts = String(event.getContent() || "").trim().split(/\s+/);
  var platform = event.getPlatform();
  var action = platform === "qq" ? "sendToQQ" : "sendToDiscord";
  var targets = [event.getReplyTarget()];
  var authorized = platform === "qq"
    ? business.isQqOwnerOrAdmin(String(event.getUserId() || ""))
    : business.isOwnerOrAdmin(String(event.getUserId() || ""));
  if (parts.length < 2 || !authorized) {
    return { handled: true, content: "权限不足：仅 Owner/Admin 可执行远程命令。", targets: targets, actions: [action] };
  }
  var result = business.executeMinecraftCommand(parts[0], parts.slice(1).join(" "));
  var content = business.configuredRemoteCommandResultFormat().split("${result}").join(result);
  return { handled: true, content: content, targets: targets, actions: [action] };
});
