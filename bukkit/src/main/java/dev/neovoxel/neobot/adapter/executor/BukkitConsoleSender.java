package dev.neovoxel.neobot.adapter.executor;

import dev.neovoxel.neobot.adapter.RemoteExecutor;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.graalvm.polyglot.HostAccess;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A version-neutral console sender that captures command output.
 *
 * CommandSender gained methods several times after Minecraft 1.8. A dynamic
 * proxy implements the interface exposed by the running server, avoiding a
 * separate sender implementation for every Bukkit generation.
 */
public class BukkitConsoleSender implements RemoteExecutor {
    /** ScriptDispatcher gives the whole RemoteCommandEvent handler only 2000ms (ScriptProvider's
     *  businessDispatcher timeout) before treating the call as rejected, so this must stay comfortably
     *  under that or a slow main thread would surface as an unexplained dispatcher rejection instead of
     *  this class's own clearer timeout failure. */
    private static final long MAIN_THREAD_TIMEOUT_MILLIS = 1500L;

    private final List<String> messageList = new ArrayList<>();
    private ConsoleCommandSender commandSender;
    private final Plugin plugin;

    public BukkitConsoleSender() {
        this(null);
    }

    /** Package-visible so tests can inject a fake Plugin instead of relying on
     *  JavaPlugin.getProvidingPlugin(), which needs a real PluginClassLoader and throws in a plain
     *  unit-test JVM. Production callers all use the no-arg constructor and resolve lazily instead. */
    BukkitConsoleSender(Plugin plugin) {
        this.plugin = plugin;
    }

    @HostAccess.Export
    @Override
    public boolean init() {
        final ConsoleCommandSender serverConsole = Bukkit.getConsoleSender();
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if (name.equals("sendMessage") || name.equals("sendRawMessage")
                        || name.equals("sendRichMessage") || name.equals("acceptConversationInput")) {
                    capture(args);
                    return null;
                }
                if (name.equals("equals")) return proxy == (args == null ? null : args[0]);
                if (name.equals("hashCode")) return System.identityHashCode(proxy);
                if (name.equals("toString")) return "NeoBotCapturingConsoleSender";
                return method.invoke(serverConsole, args);
            }
        };
        commandSender = (ConsoleCommandSender) Proxy.newProxyInstance(
                ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[]{ConsoleCommandSender.class}, handler);
        return true;
    }

    @HostAccess.Export
    @Override
    public void execute(String command) {
        if (commandSender == null) {
            init();
        }
        messageList.clear();
        if (Bukkit.isPrimaryThread()) {
            dispatchWithLogCapture(command);
            return;
        }
        // Called from ScriptDispatcher's own worker pool, never the main thread. Bukkit.dispatchCommand
        // must run on the main thread (Paper's AsyncCatcher rejects it otherwise and the command is
        // silently dropped), but the caller here is synchronous and immediately reads getResult(), so the
        // dispatch has to be rescheduled onto the main thread and this thread blocked until it finishes.
        Plugin owningPlugin = plugin != null ? plugin : JavaPlugin.getProvidingPlugin(BukkitConsoleSender.class);
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(owningPlugin, () -> {
            try {
                dispatchWithLogCapture(command);
                completion.complete(null);
            } catch (Throwable error) {
                completion.completeExceptionally(error);
            }
        });
        try {
            completion.get(MAIN_THREAD_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for command to run on the main thread: " + command, error);
        } catch (ExecutionException error) {
            throw new RuntimeException("Command failed on the main thread: " + command, error.getCause());
        } catch (TimeoutException error) {
            throw new RuntimeException("Timed out waiting for command to run on the main thread: " + command, error);
        }
    }

    @HostAccess.Export
    @Override
    public String getResult() {
        return String.join("\n", messageList);
    }

    /** Vanilla/Brigadier commands (list, whitelist, seed, ...) never reach this class's proxy at all: Paper's
     *  VanillaCommandWrapper.getListener() special-cases anything `instanceof ConsoleCommandSender` by
     *  ignoring the sender object entirely and building a fresh CommandSourceStack from the real
     *  DedicatedServer singleton, whose sendSystemMessage() writes straight to the server logger. Only
     *  Bukkit-plugin commands that call sendMessage on the sender they were handed actually hit
     *  capture() below. Log4j2's root logger sees every log record regardless of which path produced it, so
     *  a temporary appender scoped to this thread recovers vanilla command output without needing to know
     *  which of the two paths a given command takes.
     *
     *  Neither path fires at all, though, when the command's root literal doesn't match any node in the
     *  merged Bukkit/Brigadier command tree: CraftServer#dispatchCommand parses first and returns false
     *  immediately once it sees the parse matched zero context nodes, before ever calling
     *  Commands#performCommand - so nothing is sent to the sender and nothing is logged. dispatchCommand's
     *  return value is the only signal this ever happened; without checking it, an unrecognized command
     *  looks identical to a recognized command that produced no output, and callers upstream fall back to
     *  reporting it as a bare "success".
     */
    private void dispatchWithLogCapture(String command) {
        LogCapture capture = LogCapture.attach();
        boolean recognized;
        try {
            recognized = Bukkit.dispatchCommand(commandSender, command);
        } finally {
            if (capture != null) {
                capture.detach();
                if (messageList.isEmpty()) {
                    messageList.addAll(capture.lines());
                }
            }
        }
        if (messageList.isEmpty() && !recognized) {
            messageList.add("error:unknown-command");
        }
    }

    void capture(Object[] args) {
        if (args == null) return;
        for (Object arg : args) {
            if (arg instanceof String) {
                messageList.add((String) arg);
            } else if (arg instanceof String[]) {
                for (String line : (String[]) arg) messageList.add(line);
            } else if (arg != null && !arg.getClass().getName().equals("java.util.UUID")) {
                messageList.add(String.valueOf(arg));
            }
        }
    }

    /** Reflection-only (like the rest of this class) since the bukkit module compiles against 1.8.8, which
     *  predates Paper's move to Log4j2/Adventure. Absent entirely on very old servers; attach() then
     *  returns null and callers fall back to whatever capture() already collected (unchanged behavior). */
    private static final class LogCapture {
        private final Object rootLogger;
        private final Object appenderProxy;
        private final List<String> lines;

        private LogCapture(Object rootLogger, Object appenderProxy, List<String> lines) {
            this.rootLogger = rootLogger;
            this.appenderProxy = appenderProxy;
            this.lines = lines;
        }

        List<String> lines() {
            return lines;
        }

        static LogCapture attach() {
            try {
                Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager");
                Object candidateRootLogger = logManagerClass.getMethod("getRootLogger").invoke(null);
                Class<?> coreLoggerClass = Class.forName("org.apache.logging.log4j.core.Logger");
                if (!coreLoggerClass.isInstance(candidateRootLogger)) return null;

                Class<?> appenderClass = Class.forName("org.apache.logging.log4j.core.Appender");
                Class<?> logEventClass = Class.forName("org.apache.logging.log4j.core.LogEvent");
                Class<?> lifeCycleStateClass = Class.forName("org.apache.logging.log4j.core.LifeCycle$State");
                Object startedState = startedState(lifeCycleStateClass);
                Method getThreadName = logEventClass.getMethod("getThreadName");
                Method getMessage = logEventClass.getMethod("getMessage");

                List<String> lines = Collections.synchronizedList(new ArrayList<>());
                String capturingThreadName = Thread.currentThread().getName();

                InvocationHandler handler = (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "append": {
                            Object event = args[0];
                            if (capturingThreadName.equals(getThreadName.invoke(event))) {
                                Object message = getMessage.invoke(event);
                                if (message != null) {
                                    Object formatted = message.getClass().getMethod("getFormattedMessage").invoke(message);
                                    if (formatted != null) lines.add(stripAnsi(formatted.toString()));
                                }
                            }
                            return null;
                        }
                        case "getName": return "NeoBotCommandOutputCapture";
                        case "getLayout": return null;
                        case "ignoreExceptions": return true;
                        case "getHandler": return null;
                        case "setHandler": return null;
                        case "getState": return startedState;
                        case "isStarted": return true;
                        case "isStopped": return false;
                        case "initialize": return null;
                        case "start": return null;
                        case "stop": return method.getReturnType() == boolean.class ? Boolean.TRUE : null;
                        case "equals": return proxy == (args == null ? null : args[0]);
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "NeoBotCommandOutputCapture";
                        default: return null;
                    }
                };

                Object appenderProxy = Proxy.newProxyInstance(
                        appenderClass.getClassLoader(), new Class<?>[]{appenderClass}, handler);
                coreLoggerClass.getMethod("addAppender", appenderClass).invoke(candidateRootLogger, appenderProxy);
                return new LogCapture(candidateRootLogger, appenderProxy, lines);
            } catch (Throwable error) {
                return null;
            }
        }

        private static Object startedState(Class<?> lifeCycleStateClass) {
            for (Object constant : lifeCycleStateClass.getEnumConstants()) {
                if (constant.toString().equals("STARTED")) return constant;
            }
            return null;
        }

        void detach() {
            try {
                Class<?> appenderClass = Class.forName("org.apache.logging.log4j.core.Appender");
                rootLogger.getClass().getMethod("removeAppender", appenderClass).invoke(rootLogger, appenderProxy);
            } catch (Throwable ignored) {
            }
        }
    }

    private static String stripAnsi(String text) {
        return text.replaceAll("\\[[;\\d]*m", "");
    }
}
