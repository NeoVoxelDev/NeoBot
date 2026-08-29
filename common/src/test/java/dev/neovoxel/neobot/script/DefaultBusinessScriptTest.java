package dev.neovoxel.neobot.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loads the actual bundled business script (common/src/main/resources/default-script/main.js)
 * into a real GraalJS context and drives its registered handlers directly, the same way
 * ScriptProvider does via scriptManager.registerBusinessScript. This exercises the shipped
 * forwarding/binding/remote-command policy end to end, without any Java fallback involved.
 */
class DefaultBusinessScriptTest {
    private Engine engine;
    private Context context;
    private Map<String, Value> handlers;
    private Map<String, Value> gameEventHandlers;
    private FakeBusiness business;

    @BeforeEach
    void loadScript() throws Exception {
        engine = GraalScriptRuntime.createEngine();
        context = GraalScriptRuntime.buildContext(Context.newBuilder("js").engine(engine).allowAllAccess(true));
        handlers = new HashMap<>();
        gameEventHandlers = new HashMap<>();
        business = new FakeBusiness();
        context.getBindings("js").putMember("scriptManager", new FakeScriptManager(handlers));
        context.getBindings("js").putMember("gameEvent", new FakeGameEvent(gameEventHandlers));
        context.getBindings("js").putMember("business", business);
        context.eval("js", readMainJs());
    }

    @AfterEach
    void closeContext() {
        context.close();
        engine.close();
    }

    private static String readMainJs() throws Exception {
        try (InputStream in = DefaultBusinessScriptTest.class.getClassLoader()
                .getResourceAsStream("default-script/main.js")) {
            assertNotNull(in, "default-script/main.js must be on the test classpath");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private Result run(String event, Object context) {
        Value handler = handlers.get(event);
        assertNotNull(handler, "default script never registered a handler for " + event);
        return Result.from(handler.execute(context));
    }

    private FakeLoginEvent fireLogin(String name, java.util.UUID uuid) {
        Value handler = gameEventHandlers.get("LoginEvent");
        assertNotNull(handler, "default script never registered a gameEvent handler for LoginEvent");
        FakeLoginEvent event = new FakeLoginEvent(name, uuid);
        handler.execute(event);
        return event;
    }

    @Test
    void forwardsMinecraftChatToConfiguredQqGroupsAndDiscordChannel() {
        business.qqGroups = new String[] {"111", "222"};
        business.discordChannel = "333";

        Result result = run("MinecraftChatEvent", new FakePlayerChatEvent("Steve", "hello"));

        assertTrue(result.handled);
        assertEquals("[MC] Steve: hello", result.contentByAction.get("sendToQQ"));
        assertEquals("[MC] Steve: hello", result.contentByAction.get("sendToDiscord"));
        assertEquals(java.util.Arrays.asList("qq:111", "qq:222", "discord:333"), result.targets);
        assertEquals(java.util.Arrays.asList("sendToQQ", "sendToDiscord"), result.actions);
    }

    @Test
    void appliesDistinctFormatsPerPlatformWhenQqAndDiscordTemplatesDiverge() {
        business.qqGroups = new String[] {"111"};
        business.discordChannel = "333";
        business.chatForwardToQqFormat = "<QQ> ${player} 说: ${message}";
        business.minecraftToDiscordFormat = "**${player}**: ${message}";

        Result result = run("MinecraftChatEvent", new FakePlayerChatEvent("Steve", "hello"));

        assertEquals("<QQ> Steve 说: hello", result.contentByAction.get("sendToQQ"));
        assertEquals("**Steve**: hello", result.contentByAction.get("sendToDiscord"));
        assertNotEquals(result.contentByAction.get("sendToQQ"), result.contentByAction.get("sendToDiscord"));
    }

    @Test
    void forwardsQqGroupMessagesToMinecraftWithResolvedNicknameInPlaceOfRawSenderId() {
        Result result = run("QQGroupMessageEvent", new FakeQqGroupMessageEvent(111L, 999L, "hi from qq"));

        assertTrue(result.handled);
        assertEquals("[QQ群(111)] ResolvedNick: hi from qq", result.content);
        assertEquals(111L, business.lastResolveQqGroupId);
        assertEquals(999L, business.lastResolveQqUserId);
        assertEquals(java.util.Collections.singletonList("minecraft:default"), result.targets);
        assertEquals(java.util.Collections.singletonList("sendToMinecraft"), result.actions);
    }

    @Test
    void doesNotForwardMinecraftChatToQqWhenChatForwardToQqDisabled() {
        business.qqGroups = new String[] {"111", "222"};
        business.discordChannel = "333";
        business.chatForwardToQqEnabled = false;

        Result result = run("MinecraftChatEvent", new FakePlayerChatEvent("Steve", "hello"));

        assertEquals(java.util.Collections.singletonList("discord:333"), result.targets);
        assertEquals(java.util.Collections.singletonList("sendToDiscord"), result.actions);
    }

    @Test
    void doesNotForwardQqMessagesToMinecraftWhenChatForwardToGameDisabled() {
        business.chatForwardToGameEnabled = false;

        Result result = run("QQGroupMessageEvent", new FakeRawMessageEvent("hi from qq"));

        assertTrue(result.handled);
        assertTrue(result.targets.isEmpty());
        assertTrue(result.actions.isEmpty());
    }

    @Test
    void forwardsDiscordMessagesToMinecraftWithResolvedChannelNameInPlaceOfRawChannelId() {
        Result result = run("DiscordMessageEvent", new FakeDiscordMessageEvent(444L, "Alice", "hi from discord"));

        assertTrue(result.handled);
        assertEquals("[Discord(resolved-channel)] Alice: hi from discord", result.content);
        assertEquals("444", business.lastResolveDiscordChannelId);
        assertEquals(java.util.Collections.singletonList("minecraft"), result.targets);
        assertEquals(java.util.Collections.singletonList("sendToMinecraft"), result.actions);
    }

    @Test
    void announcesServerStartAndStopToConfiguredDestinations() {
        business.qqGroups = new String[] {"111"};
        business.discordChannel = "333";

        Result start = run("ServerStartEvent", new Object());
        assertEquals("[TestServer] 服务器已启动!", start.content);
        assertEquals(java.util.Arrays.asList("qq:111", "discord:333"), start.targets);

        Result stop = run("ServerStopEvent", new Object());
        assertEquals("[TestServer] 服务器已关闭!", stop.content);
        assertEquals(java.util.Arrays.asList("qq:111", "discord:333"), stop.targets);
    }

    @Test
    void suppressesQqAnnouncementsWhenQqServerStatusDisabled() {
        business.qqGroups = new String[] {"111"};
        business.discordChannel = "333";
        business.qqServerStatusEnabled = false;

        Result start = run("ServerStartEvent", new Object());
        assertEquals(java.util.Collections.singletonList("discord:333"), start.targets);
        assertEquals(java.util.Collections.singletonList("sendToDiscord"), start.actions);

        Result stop = run("ServerStopEvent", new Object());
        assertEquals(java.util.Collections.singletonList("discord:333"), stop.targets);
        assertEquals(java.util.Collections.singletonList("sendToDiscord"), stop.actions);
    }

    @Test
    void suppressesDiscordAnnouncementsWhenDiscordServerStatusDisabled() {
        business.qqGroups = new String[] {"111"};
        business.discordChannel = "333";
        business.discordServerStatusEnabled = false;

        Result start = run("ServerStartEvent", new Object());
        assertEquals(java.util.Collections.singletonList("qq:111"), start.targets);
        assertEquals(java.util.Collections.singletonList("sendToQQ"), start.actions);

        Result stop = run("ServerStopEvent", new Object());
        assertEquals(java.util.Collections.singletonList("qq:111"), stop.targets);
        assertEquals(java.util.Collections.singletonList("sendToQQ"), stop.actions);
    }

    @Test
    void suppressesAllAnnouncementsWhenBothServerStatusSwitchesDisabled() {
        business.qqGroups = new String[] {"111"};
        business.discordChannel = "333";
        business.qqServerStatusEnabled = false;
        business.discordServerStatusEnabled = false;

        Result start = run("ServerStartEvent", new Object());
        assertTrue(start.targets.isEmpty());
        assertTrue(start.actions.isEmpty());

        Result stop = run("ServerStopEvent", new Object());
        assertTrue(stop.targets.isEmpty());
        assertTrue(stop.actions.isEmpty());
    }

    @Test
    void announcesPlayerJoinAndQuitToConfiguredQqDiscordDestinationsOnlyNotBackIntoGame() {
        business.qqGroups = new String[] {"111"};
        business.discordChannel = "333";

        // Vanilla already prints its own join/quit line in-game, so NeoBot must not broadcast a second one
        // (regression: it previously did, producing a duplicate "[Minecraft] X 进入了服务器!" line).
        Result join = run("PlayerJoinEvent", new FakePlayerChatEvent("Steve", ""));
        assertEquals("[TestServer] Steve 进入了服务器!", join.content);
        assertEquals(java.util.Arrays.asList("qq:111", "discord:333"), join.targets);
        assertEquals(java.util.Arrays.asList("sendToQQ", "sendToDiscord"), join.actions);

        Result quit = run("PlayerQuitEvent", new FakePlayerChatEvent("Steve", ""));
        assertEquals("[TestServer] Steve 离开了服务器!", quit.content);
        assertEquals(java.util.Arrays.asList("qq:111", "discord:333"), quit.targets);
        assertEquals(java.util.Arrays.asList("sendToQQ", "sendToDiscord"), quit.actions);
    }

    @Test
    void suppressesPlayerJoinEntirelyWhenQqAndDiscordPlayerStatusDisabled() {
        business.qqGroups = new String[] {"111"};
        business.discordChannel = "333";
        business.qqPlayerStatusEnabled = false;
        business.discordPlayerStatusEnabled = false;

        Result join = run("PlayerJoinEvent", new FakePlayerChatEvent("Steve", ""));

        assertTrue(join.targets.isEmpty());
        assertTrue(join.actions.isEmpty());
    }

    @Test
    void announcesPlayerDeathToConfiguredQqDiscordDestinationsOnlyNotBackIntoGame() {
        business.qqGroups = new String[] {"111"};
        business.discordChannel = "333";

        // Vanilla already prints its own death message in-game, so NeoBot must not broadcast a second one
        // (regression: it previously did, producing a duplicate "[Minecraft] X 逝世了!" line).
        Result death = run("PlayerDeathEvent", new FakePlayerChatEvent("Steve", ""));
        assertEquals("[TestServer] Steve 逝世了!", death.content);
        assertEquals(java.util.Arrays.asList("qq:111", "discord:333"), death.targets);
        assertEquals(java.util.Arrays.asList("sendToQQ", "sendToDiscord"), death.actions);
    }

    @Test
    void suppressesPlayerDeathEntirelyWhenQqAndDiscordPlayerStatusDisabled() {
        business.qqGroups = new String[] {"111"};
        business.discordChannel = "333";
        business.qqPlayerStatusEnabled = false;
        business.discordPlayerStatusEnabled = false;

        Result death = run("PlayerDeathEvent", new FakePlayerChatEvent("Steve", ""));

        assertTrue(death.targets.isEmpty());
        assertTrue(death.actions.isEmpty());
    }

    @Test
    void bindThreadsThePlatformAsTheSharedIdentityKindAndRepliesToTheRequester() {
        Result qqBind = run("BindEvent", new InboundCommandContext("/bind Steve", "qq-user-1", "qq:555", "qq"));
        assertEquals("qq", business.lastBindKind);
        assertEquals("Steve", business.lastBindPlayer);
        assertEquals("qq-user-1", business.lastBindIdentity);
        assertEquals(java.util.Collections.singletonList("qq:555"), qqBind.targets);
        assertEquals(java.util.Collections.singletonList("sendToQQ"), qqBind.actions);

        Result discordBind = run("BindEvent", new InboundCommandContext("/bind Steve", "discord-user-1", "discord:777", "discord"));
        assertEquals("discord", business.lastBindKind);
        assertEquals(java.util.Collections.singletonList("discord:777"), discordBind.targets);
        assertEquals(java.util.Collections.singletonList("sendToDiscord"), discordBind.actions);
    }

    @Test
    void unbindThreadsThePlatformAsTheSharedIdentityKind() {
        run("UnbindEvent", new InboundCommandContext("/unbind Steve", "qq-user-1", "qq:555", "qq"));
        assertEquals("qq", business.lastUnbindKind);
        assertEquals("Steve", business.lastUnbindPlayer);
    }

    @Test
    void bindAndUnbindSuccessMessagesAreDistinctOnBothPlatforms() {
        Result qqBind = run("BindEvent", new InboundCommandContext("/bind Steve", "qq-user-1", "qq:555", "qq"));
        Result qqUnbind = run("UnbindEvent", new InboundCommandContext("/unbind Steve", "qq-user-1", "qq:555", "qq"));
        assertEquals("绑定成功!", qqBind.content);
        assertEquals("解绑成功!", qqUnbind.content);
        assertNotEquals(qqBind.content, qqUnbind.content);

        Result discordBind = run("BindEvent", new InboundCommandContext("/bind Steve", "discord-user-1", "discord:777", "discord"));
        Result discordUnbind = run("UnbindEvent", new InboundCommandContext("/unbind Steve", "discord-user-1", "discord:777", "discord"));
        assertEquals("绑定成功!", discordBind.content);
        assertEquals("解绑成功!", discordUnbind.content);
        assertNotEquals(discordBind.content, discordUnbind.content);
    }

    @Test
    void malformedBindContentIsCancelledWithoutCallingBusiness() {
        Result result = run("BindEvent", new InboundCommandContext("/bind", "qq-user-1", "qq:555", "qq"));
        assertTrue(result.cancelled);
        assertNull(business.lastBindKind, "business.bind must not be invoked for a malformed command");
    }

    @Test
    void ownerOrAdminCanExecuteAConfiguredSubServerCommand() {
        business.qqOwnerOrAdmin = true;
        business.executeResult = "done";

        Result result = run("RemoteCommandEvent", new InboundCommandContext("login say hi", "owner-1", "qq:555", "qq"));

        assertEquals(java.util.Collections.singletonList("login say hi"), business.executeCalls);
        assertEquals("[NeoBot] 命令执行结果: \ndone", result.content);
        assertEquals(java.util.Collections.singletonList("qq:555"), result.targets);
        assertEquals(java.util.Collections.singletonList("sendToQQ"), result.actions);
    }

    @Test
    void remoteCommandResultIsWrappedUsingConfiguredTemplate() {
        business.qqOwnerOrAdmin = true;
        business.executeResult = "done";
        business.remoteCommandResultFormat = "RESULT=${result}";

        Result result = run("RemoteCommandEvent", new InboundCommandContext("login say hi", "owner-1", "qq:555", "qq"));

        assertEquals("RESULT=done", result.content);
    }

    @Test
    void ordinaryUserIsDeniedAndNoCommandIsExecuted() {
        business.qqOwnerOrAdmin = false;

        Result result = run("RemoteCommandEvent", new InboundCommandContext("login say hi", "random-user", "qq:555", "qq"));

        assertTrue(business.executeCalls.isEmpty(), "a denied user must never reach executeMinecraftCommand");
        assertTrue(result.handled);
        assertEquals(java.util.Collections.singletonList("qq:555"), result.targets);
    }

    @Test
    void discordRemoteCommandPermissionIsIndependentFromQq() {
        business.qqOwnerOrAdmin = false;
        business.ownerOrAdmin = true;
        business.executeResult = "done-discord";

        Result qqResult = run("RemoteCommandEvent", new InboundCommandContext("login say hi", "same-user", "qq:555", "qq"));
        assertTrue(business.executeCalls.isEmpty(), "QQ user must be denied when only the Discord whitelist authorizes them");
        assertNotEquals("done-discord", qqResult.content);

        Result discordResult = run("RemoteCommandEvent", new InboundCommandContext("login say hi", "same-user", "discord:555", "discord"));
        assertEquals("[NeoBot] 命令执行结果: \ndone-discord", discordResult.content);
        assertEquals(java.util.Collections.singletonList("login say hi"), business.executeCalls);
    }

    @Test
    void allowsLoginWhenNoBindingIsRequired() {
        FakeLoginEvent event = fireLogin("Steve", java.util.UUID.randomUUID());
        assertFalse(event.disallowed);
    }

    @Test
    void disallowsLoginWhenBindingRequiredAndNeitherPlatformIsBound() {
        business.accountRequireBinding = true;

        FakeLoginEvent event = fireLogin("Steve", java.util.UUID.randomUUID());

        assertTrue(event.disallowed);
        assertEquals("Account binding required for Steve", event.disallowReason);
    }

    @Test
    void allowsLoginWhenBindingRequiredAndOnlyQqIsBound() {
        business.accountRequireBinding = true;
        java.util.UUID uuid = java.util.UUID.randomUUID();
        business.qqBoundUuids.add(uuid);

        FakeLoginEvent event = fireLogin("Steve", uuid);

        assertFalse(event.disallowed);
    }

    @Test
    void allowsLoginWhenBindingRequiredAndOnlyDiscordIsBound() {
        business.accountRequireBinding = true;
        java.util.UUID uuid = java.util.UUID.randomUUID();
        business.discordBoundUuids.add(uuid);

        FakeLoginEvent event = fireLogin("Steve", uuid);

        assertFalse(event.disallowed);
    }

    @Test
    void allowsLoginWhenBindingRequiredAndBothPlatformsAreBound() {
        business.accountRequireBinding = true;
        java.util.UUID uuid = java.util.UUID.randomUUID();
        business.qqBoundUuids.add(uuid);
        business.discordBoundUuids.add(uuid);

        FakeLoginEvent event = fireLogin("Steve", uuid);

        assertFalse(event.disallowed);
    }

    /** Minimal stand-in for ScriptProvider.registerBusinessScript's storage side, used only to capture handlers. */
    public static final class FakeScriptManager {
        private final Map<String, Value> handlers;
        public FakeScriptManager(Map<String, Value> handlers) { this.handlers = handlers; }
        public void registerBusinessScript(String event, Value callback) { handlers.put(event, callback); }
    }

    /** Minimal stand-in for GameEventListener's storage side, used only to capture handlers. */
    public static final class FakeGameEvent {
        private final Map<String, Value> handlers;
        public FakeGameEvent(Map<String, Value> handlers) { this.handlers = handlers; }
        public void register(String event, Value callback) { handlers.put(event, callback); }
    }

    public static final class FakeLoginEvent {
        private final String name;
        private final java.util.UUID uuid;
        boolean disallowed;
        String disallowReason;
        public FakeLoginEvent(String name, java.util.UUID uuid) { this.name = name; this.uuid = uuid; }
        public String getName() { return name; }
        public java.util.UUID getUuid() { return uuid; }
        public void disallow(String reason) { disallowed = true; disallowReason = reason; }
    }

    public static final class FakePlayerChatEvent {
        private final FakePlayer player;
        private final String message;
        public FakePlayerChatEvent(String playerName, String message) { this.player = new FakePlayer(playerName); this.message = message; }
        public FakePlayer getPlayer() { return player; }
        public String getMessage() { return message; }
    }
    public static final class FakePlayer {
        private final String name;
        public FakePlayer(String name) { this.name = name; }
        public String getName() { return name; }
    }
    public static final class FakeRawMessageEvent {
        private final String raw;
        public FakeRawMessageEvent(String raw) { this.raw = raw; }
        public String getRawMessage() { return raw; }
    }
    public static final class FakeQqGroupMessageEvent {
        private final long groupId;
        private final long senderId;
        private final String raw;
        public FakeQqGroupMessageEvent(long groupId, long senderId, String raw) { this.groupId = groupId; this.senderId = senderId; this.raw = raw; }
        public long getGroupId() { return groupId; }
        public long getSenderId() { return senderId; }
        public String getRawMessage() { return raw; }
    }
    public static final class FakeDiscordMessageEvent {
        private final FakeDiscordMessage message;
        public FakeDiscordMessageEvent(long channelId, String username, String content) { this.message = new FakeDiscordMessage(channelId, username, content); }
        public FakeDiscordMessage getMessage() { return message; }
    }
    public static final class FakeDiscordMessage {
        private final long channelId;
        private final FakeDiscordAuthor author;
        private final String content;
        public FakeDiscordMessage(long channelId, String username, String content) { this.channelId = channelId; this.author = new FakeDiscordAuthor(username); this.content = content; }
        public long getChannelId() { return channelId; }
        public FakeDiscordAuthor getAuthor() { return author; }
        public String getContent() { return content; }
    }
    public static final class FakeDiscordAuthor {
        private final String username;
        public FakeDiscordAuthor(String username) { this.username = username; }
        public String getUsername() { return username; }
    }

    public static final class FakeBusiness {
        String[] qqGroups = new String[0];
        String discordChannel = "";
        boolean ownerOrAdmin = false;
        boolean qqOwnerOrAdmin = false;
        String bindResult = "success";
        String unbindResult = "success";
        String executeResult = "ok";
        String remoteCommandResultFormat = "[NeoBot] 命令执行结果: \n${result}";
        String lastBindKind;
        String lastBindPlayer;
        String lastBindIdentity;
        String lastUnbindKind;
        String lastUnbindPlayer;
        final List<String> executeCalls = new ArrayList<>();

        String serverName = "TestServer";
        String startMessage = "[${server}] 服务器已启动!";
        String stopMessage = "[${server}] 服务器已关闭!";
        String discordToMinecraftFormat = "[Discord(${channel})] ${user}: ${message}";
        String minecraftToDiscordFormat = "[MC] ${player}: ${message}";
        String chatForwardToQqFormat = "[MC] ${player}: ${message}";
        String chatForwardToGameFormat = "[QQ群(${group})] ${user}: ${message}";
        boolean chatForwardToQqEnabled = true;
        boolean chatForwardToGameEnabled = true;
        boolean qqServerStatusEnabled = true;
        boolean discordServerStatusEnabled = true;
        boolean qqPlayerStatusEnabled = true;
        boolean discordPlayerStatusEnabled = true;
        String joinMessage = "[${server}] ${player} 进入了服务器!";
        String quitMessage = "[${server}] ${player} 离开了服务器!";
        String deathMessage = "[${server}] ${player} 逝世了!";
        boolean accountRequireBinding = false;
        String accountRequireBindingMessage = "Account binding required for ${player}";
        final java.util.Set<java.util.UUID> qqBoundUuids = new java.util.HashSet<>();
        final java.util.Set<java.util.UUID> discordBoundUuids = new java.util.HashSet<>();

        String qqNicknameToResolve = "ResolvedNick";
        long lastResolveQqGroupId;
        long lastResolveQqUserId;
        String discordChannelNameToResolve = "resolved-channel";
        String lastResolveDiscordChannelId;

        public String[] configuredQqGroups() { return qqGroups; }
        public String configuredDiscordChannel() { return discordChannel; }
        public String configuredServerName() { return serverName; }
        public String configuredStartMessage() { return startMessage; }
        public String configuredStopMessage() { return stopMessage; }
        public String configuredDiscordToMinecraftFormat() { return discordToMinecraftFormat; }
        public String configuredMinecraftToDiscordFormat() { return minecraftToDiscordFormat; }
        public String configuredChatForwardToQqFormat() { return chatForwardToQqFormat; }
        public String configuredChatForwardToGameFormat() { return chatForwardToGameFormat; }
        public boolean configuredChatForwardToQqEnabled() { return chatForwardToQqEnabled; }
        public boolean configuredChatForwardToGameEnabled() { return chatForwardToGameEnabled; }
        public boolean configuredQqServerStatusEnabled() { return qqServerStatusEnabled; }
        public boolean configuredDiscordServerStatusEnabled() { return discordServerStatusEnabled; }
        public boolean configuredQqPlayerStatusEnabled() { return qqPlayerStatusEnabled; }
        public boolean configuredDiscordPlayerStatusEnabled() { return discordPlayerStatusEnabled; }
        public String configuredJoinMessage() { return joinMessage; }
        public String configuredQuitMessage() { return quitMessage; }
        public String configuredDeathMessage() { return deathMessage; }
        public boolean configuredAccountRequireBinding() { return accountRequireBinding; }
        public String configuredAccountRequireBindingMessage() { return accountRequireBindingMessage; }
        public boolean hasQqBinding(java.util.UUID uuid) { return qqBoundUuids.contains(uuid); }
        public boolean hasDiscordBinding(java.util.UUID uuid) { return discordBoundUuids.contains(uuid); }
        public String bind(String playerName, String identity, String kind) {
            lastBindPlayer = playerName; lastBindIdentity = identity; lastBindKind = kind; return bindResult;
        }
        public String unbind(String playerName, String identity, String kind) {
            lastUnbindKind = kind; lastUnbindPlayer = playerName; return unbindResult;
        }
        public boolean isOwnerOrAdmin(String userId) { return ownerOrAdmin; }
        public boolean isQqOwnerOrAdmin(String userId) { return qqOwnerOrAdmin; }
        public String resolveQqNickname(long groupId, long userId) {
            lastResolveQqGroupId = groupId; lastResolveQqUserId = userId; return qqNicknameToResolve;
        }
        public String resolveDiscordChannelName(String channelId) {
            lastResolveDiscordChannelId = channelId; return discordChannelNameToResolve;
        }
        public String executeMinecraftCommand(String server, String command) {
            executeCalls.add(server + " " + command);
            return executeResult;
        }
        public String configuredRemoteCommandResultFormat() { return remoteCommandResultFormat; }
    }

    private static final class Result {
        final boolean handled;
        final boolean cancelled;
        final String content;
        final List<String> targets;
        final List<String> actions;
        final Map<String, String> contentByAction;

        private Result(boolean handled, boolean cancelled, String content, List<String> targets, List<String> actions,
                       Map<String, String> contentByAction) {
            this.handled = handled; this.cancelled = cancelled; this.content = content;
            this.targets = targets; this.actions = actions; this.contentByAction = contentByAction;
        }

        static Result from(Value value) {
            if (value == null || value.isNull()) return new Result(false, false, null, java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyMap());
            boolean handled = value.hasMember("handled") && value.getMember("handled").asBoolean();
            boolean cancelled = value.hasMember("cancelled") && value.getMember("cancelled").asBoolean();
            String content = value.hasMember("content") && !value.getMember("content").isNull() ? value.getMember("content").asString() : null;
            List<String> targets = new ArrayList<>();
            List<String> actions = new ArrayList<>();
            Map<String, String> contentByAction = new HashMap<>();
            if (value.hasMember("targets") && value.getMember("targets").hasArrayElements()) {
                for (long i = 0; i < value.getMember("targets").getArraySize(); i++) targets.add(value.getMember("targets").getArrayElement(i).asString());
            }
            if (value.hasMember("actions") && value.getMember("actions").hasArrayElements()) {
                for (long i = 0; i < value.getMember("actions").getArraySize(); i++) actions.add(value.getMember("actions").getArrayElement(i).asString());
            }
            if (value.hasMember("contentByAction") && value.getMember("contentByAction").hasMembers()) {
                Value byAction = value.getMember("contentByAction");
                for (String key : byAction.getMemberKeys()) {
                    Value entry = byAction.getMember(key);
                    if (entry != null && !entry.isNull()) contentByAction.put(key, entry.asString());
                }
            }
            return new Result(handled, cancelled, content, targets, actions, contentByAction);
        }
    }
}
