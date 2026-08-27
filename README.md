# NeoBot
A bot plugin that connects Minecraft with QQ, Kook, Discord, etc.

QQ chat forwarding uses the existing `bot.options.enable-groups` numeric array (for example `[114514]`). The generated config now explicitly contains `chat-forward.to-qq` and `chat-forward.to-game` switches (both default to `false` so the example group is never contacted accidentally). Set them to `true` deliberately: Minecraft chat is then sent to every configured group, and ordinary messages from those groups are broadcast to Minecraft. `/bind` and `/unbind` (and legacy forms without `/`) are handled first and are never forwarded. An empty group list disables QQ forwarding and is reported in the log.

All forwarding decisions are made entirely by the business script (`common/src/main/resources/default-script/main.js` by default). Java only raises standard events — `MinecraftChatEvent`, `QQGroupMessageEvent`, `DiscordMessageEvent`, `BindEvent`, `UnbindEvent`, `RemoteCommandEvent`, `ServerStartEvent`, `ServerStopEvent` — and executes the low-level send/command actions a script returns. There is no Java fallback: if no script is loaded, or a script throws or times out, the event is rejected and logged, and nothing is sent. `/bind` and `/unbind` (and legacy forms without `/`) are recognized by Java and dispatched as `BindEvent`/`UnbindEvent` before ordinary forwarding, but the bind/unbind logic itself runs in the script via the `business` API.

## Discord

Add `"discord"` to `bot.type`, set `enabled` to `true`, and provide the bot token in the server's generated `config.json`. The token is never logged.

```json
{
  "bot": {
    "type": ["onebot11-ws", "discord"],
    "discord": {
      "enabled": true,
      "token": "YOUR_DISCORD_BOT_TOKEN",
      "intents": 37377,
      "proxy": {
        "mode": "official",
        "url": ""
      }
    }
  }
}
```

Use `official`, `proxy_http`, `proxy_https`, or `cf_worker` for `proxy.mode`. For every proxy mode, `proxy.url` is the only endpoint setting. The plugin derives `<url>/api/v10` for REST and `<url>/gateway?v=10&encoding=json` for Gateway, removes an existing `/api/v10` or `/gateway` suffix, and rejects query strings/fragments. `proxy_http` derives `http` and `ws`; `proxy_https` and `cf_worker` derive `https` and `wss`. A Worker must proxy both normal HTTP requests and WebSocket Upgrade traffic.

A ready-to-deploy `cf_worker` implementation (single-file script, `wrangler.toml`, and full deployment walkthrough including custom domains) lives in [cf-worker-discord-proxy/](cf-worker-discord-proxy/README.md). One Worker handles both the Discord proxy and its optional web admin panel -- nothing else to deploy.

Discord is exposed to scripts as `discord`, separately from the existing `qq` OneBot bridge:

```javascript
discord.register("DiscordMessageCreateEvent", event => {
    discord.sendMessage(String(event.message.channelId), "Received: " + event.message.content);
});
```

Other event names are `DiscordReadyEvent`, `DiscordMessageUpdateEvent`, `DiscordMessageDeleteEvent`, `DiscordRawEvent`, and `DiscordEvent`. Scripts can call `discord.sendMessage(channelId, text)`, `discord.editMessage(channelId, messageId, text)`, `discord.deleteMessage(channelId, messageId)`, and `discord.getClients()`. Prefer the String overloads for snowflakes so JavaScript never rounds IDs above `2^53`. Discord clients and channel IDs are never converted to OneBot clients or group IDs.

### Discord chat and bindings

Set `server-messages-channel-id` and, optionally, the separate bind channel. All Discord IDs must be JSON strings, preserving snowflake precision.

```json
"channels": {
  "bind-channel-id": "223456789012345678",
  "server-messages-channel-id": "323456789012345678"
},
"account": {
  "maximum-bindings-per-user": 1
}
```

Built-in server lifecycle notifications use the server messages channel and do not require a script:

```json
"server-status": {
  "enabled": true,
  "server-name": "Minecraft",
  "start-message": "[${server}] 服务器已启动!",
  "stop-message": "[${server}] 服务器已关闭!"
}
```

The start message waits for Discord READY before sending. The stop message is sent before the Discord client is shut down and waits at most five seconds for the REST request. `/neobot reload` does not emit either lifecycle message.

`server-messages-channel-id` is the unified Minecraft chat, announcement, and server-command channel. Ordinary messages are forwarded to Minecraft; recognized server-prefix commands are executed and are not forwarded. `bind-channel-id` is reserved for `/bind <minecraft-player-name>` and `/unbind <minecraft-player-name>` (legacy `!bind`/`!unbind` remain accepted). A Discord user can unlink only their own binding. Bot/self messages are ignored by default. Existing legacy channel rows remain available for advanced administrator management.

Server management is processed only in `server-messages-channel-id`. Configure one or more server objects; a minimal setup needs only one enabled object such as `{ "server-name": "login", "prefix": "login", "executor": "bukkit", "enabled": true }`. A matching prefix routes the complete command text to that executor and requires an Owner/Admin Discord ID. Ordinary multi-word text that does not match a configured prefix is chat and is forwarded, without an Unknown reply. `bind-channel-id` is reserved exclusively for `/bind` and `/unbind` (legacy `!bind`/`!unbind` remain accepted).

```
/neobot discord channel bind <guild-id> <channel-id>
/neobot discord channel unbind <guild-id> <channel-id>
/neobot discord channel list
/neobot discord account bind <player> <discord-user-id>
/neobot discord account unlink <player>
/neobot discord account status <player>
```

The game-side account commands are administrator actions and can manage any account. Discord self-service binding uses only the bind channel. Both bridges use `/bind <player>` and `/unbind <player>`; QQ also accepts the legacy `bind`/`unbind` spelling. `neobot_discord_accounts` is the shared UUID-keyed binding record: it may contain both `discord_user_id` and `qq_user_id`, so either bridge sees the same binding and enforces one identity per player. Conflicts and non-owner unbinds are rejected. Existing Discord rows are preserved; the QQ column is added for new installations/migrations without overwriting existing identities.

GitHub status and remote-script downloads use direct GitHub by default. For networks that require a proxy, enable `repository.use-github-proxy` and set `repository.github-proxy-url` to a verified base URL such as `http://127.0.0.1:7897`. The original GitHub URL is appended, requests have bounded timeouts, and proxy failures fall back to direct access. No public proxy hostname is hard-coded.
