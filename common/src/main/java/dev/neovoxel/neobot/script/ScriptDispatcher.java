package dev.neovoxel.neobot.script;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Thread-safe synchronous dispatcher. A timeout or exception is a hard rejection. */
public final class ScriptDispatcher {
    public interface Handler { ScriptDispatchResult handle(String event, Object context) throws Exception; }
    private final CopyOnWriteArrayList<Handler> handlers = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final long timeoutMillis;
    public ScriptDispatcher(long timeoutMillis) { this.timeoutMillis = Math.max(1L, timeoutMillis); }
    public void register(Handler handler) { if (handler != null) handlers.addIfAbsent(handler); }
    public void unregister(Handler handler) { handlers.remove(handler); }
    /** Drops every registered handler without touching the executor, so the dispatcher can be reused
     *  across a script reload instead of leaving handlers bound to an already-closed script context. */
    public void clearHandlers() { handlers.clear(); }
    public ScriptDispatchResult dispatch(String event, Object context) {
        if (handlers.isEmpty()) return ScriptDispatchResult.rejected();
        for (Handler handler : handlers) {
            Future<ScriptDispatchResult> future = executor.submit(() -> handler.handle(event, context));
            try {
                ScriptDispatchResult result = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
                if (result != null && result.isHandled()) return result;
            } catch (Exception error) {
                future.cancel(true);
                return ScriptDispatchResult.rejected();
            }
        }
        return ScriptDispatchResult.unhandled();
    }
    public void close() { executor.shutdownNow(); handlers.clear(); }
}
