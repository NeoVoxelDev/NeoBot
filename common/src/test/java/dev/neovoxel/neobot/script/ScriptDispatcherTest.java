package dev.neovoxel.neobot.script;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the dispatch->execute->return contract has no Java business fallback path. */
class ScriptDispatcherTest {

    private static class RecordingExecutor implements BusinessActionExecutor {
        final List<String> calls = new java.util.ArrayList<>();
        @Override public void execute(String action, String target, String content) {
            calls.add(action + ":" + target + ":" + content);
        }
    }

    @Test
    void noHandlerMeansNoForwarding() {
        ScriptDispatcher dispatcher = new ScriptDispatcher(200L);
        ScriptDispatchResult result = dispatcher.dispatch("MinecraftChatEvent", new Object());
        assertTrue(result.isCancelled());

        RecordingExecutor executor = new RecordingExecutor();
        ScriptProvider provider = new ScriptProvider(null);
        provider.setBusinessActionExecutor(executor);
        provider.executeBusinessActions(result);
        assertTrue(executor.calls.isEmpty(), "no script registered means no action must ever be executed");
    }

    @Test
    void scriptExceptionDoesNotFallBack() {
        ScriptDispatcher dispatcher = new ScriptDispatcher(200L);
        dispatcher.register((event, context) -> { throw new RuntimeException("boom"); });
        ScriptDispatchResult result = dispatcher.dispatch("BindEvent", new Object());
        assertTrue(result.isCancelled(), "an exception in the handler must be a hard rejection, not a fallback");

        RecordingExecutor executor = new RecordingExecutor();
        ScriptProvider provider = new ScriptProvider(null);
        provider.setBusinessActionExecutor(executor);
        provider.executeBusinessActions(result);
        assertTrue(executor.calls.isEmpty());
    }

    @Test
    void scriptTimeoutDoesNotFallBack() {
        ScriptDispatcher dispatcher = new ScriptDispatcher(50L);
        dispatcher.register((event, context) -> {
            Thread.sleep(2000L);
            return new ScriptDispatchResult(true, false, "late", Collections.singletonList("t"), Collections.singletonList("a"));
        });
        ScriptDispatchResult result = dispatcher.dispatch("RemoteCommandEvent", new Object());
        assertTrue(result.isCancelled(), "a timed-out handler must be a hard rejection, not a fallback");
    }

    @Test
    void eachEventIsProcessedExactlyOnce() {
        ScriptDispatcher dispatcher = new ScriptDispatcher(200L);
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        dispatcher.register((event, context) -> {
            firstCalls.incrementAndGet();
            return new ScriptDispatchResult(true, false, "handled-by-first",
                    Collections.singletonList("qq:1"), Collections.singletonList("sendToQQ"));
        });
        dispatcher.register((event, context) -> {
            secondCalls.incrementAndGet();
            return ScriptDispatchResult.unhandled();
        });

        ScriptDispatchResult result = dispatcher.dispatch("QQGroupMessageEvent", new Object());

        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get(), "a second handler must not also run once an earlier one claims the event");
        assertEquals("handled-by-first", result.getContent());
    }

    @Test
    void clearHandlersDropsStaleRegistrationsSoAReloadDoesNotBlockFutureDispatch() {
        ScriptDispatcher dispatcher = new ScriptDispatcher(200L);
        dispatcher.register((event, context) -> { throw new IllegalStateException("stale script context is closed"); });

        ScriptDispatchResult beforeClear = dispatcher.dispatch("MinecraftChatEvent", new Object());
        assertTrue(beforeClear.isCancelled(), "a stale handler left behind by an earlier script load must still hard-reject before clearHandlers()");

        dispatcher.clearHandlers();
        dispatcher.register((event, context) -> new ScriptDispatchResult(true, false, "hi",
                Collections.singletonList("discord:1"), Collections.singletonList("sendToDiscord")));

        ScriptDispatchResult afterClear = dispatcher.dispatch("MinecraftChatEvent", new Object());
        assertFalse(afterClear.isCancelled(), "clearHandlers() must drop the stale handler so a freshly reloaded script's handler can run");
        assertEquals("hi", afterClear.getContent());
    }

    @Test
    void executesEveryTargetActionPairExactlyOnceWhenHandled() {
        ScriptDispatcher dispatcher = new ScriptDispatcher(200L);
        dispatcher.register((event, context) -> new ScriptDispatchResult(true, false, "hi",
                Arrays.asList("qq:1", "discord:2"), Arrays.asList("sendToQQ", "sendToDiscord")));
        ScriptDispatchResult result = dispatcher.dispatch("MinecraftChatEvent", new Object());

        RecordingExecutor executor = new RecordingExecutor();
        ScriptProvider provider = new ScriptProvider(null);
        provider.setBusinessActionExecutor(executor);
        provider.executeBusinessActions(result);

        assertEquals(4, executor.calls.size());
        assertTrue(executor.calls.contains("sendToQQ:qq:1:hi"));
        assertTrue(executor.calls.contains("sendToDiscord:discord:2:hi"));
    }

    @Test
    void contentByActionOverridesTheSharedContentPerAction() {
        java.util.Map<String, String> contentByAction = new java.util.HashMap<>();
        contentByAction.put("sendToQQ", "qq-flavored");
        contentByAction.put("sendToDiscord", "discord-flavored");
        ScriptDispatcher dispatcher = new ScriptDispatcher(200L);
        dispatcher.register((event, context) -> new ScriptDispatchResult(true, false, "fallback",
                Arrays.asList("qq:1", "discord:2"), Arrays.asList("sendToQQ", "sendToDiscord"), contentByAction));
        ScriptDispatchResult result = dispatcher.dispatch("MinecraftChatEvent", new Object());

        RecordingExecutor executor = new RecordingExecutor();
        ScriptProvider provider = new ScriptProvider(null);
        provider.setBusinessActionExecutor(executor);
        provider.executeBusinessActions(result);

        // RecordingExecutor uses the interface's unfiltered default executeAll (actions x targets),
        // so each action still fires once per target; actionMatchesTarget-based filtering is covered
        // separately by NeoBotBusinessActionExecutorRoutingTest. This test only verifies that each
        // action's own content override reaches every call for that action, regardless of target.
        assertEquals(4, executor.calls.size());
        assertTrue(executor.calls.contains("sendToQQ:qq:1:qq-flavored"));
        assertTrue(executor.calls.contains("sendToDiscord:discord:2:discord-flavored"));
    }
}
