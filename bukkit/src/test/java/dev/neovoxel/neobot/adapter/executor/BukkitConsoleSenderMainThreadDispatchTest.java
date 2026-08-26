package dev.neovoxel.neobot.adapter.executor;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Reproduces the reported bug: a remote command triggered from QQ/Discord ran
 * Bukkit.dispatchCommand() straight from ScriptDispatcher's worker thread, which Paper's AsyncCatcher
 * rejects (the command is silently dropped). These tests fake out the static Bukkit/Server singleton with
 * dynamic proxies so the off-main-thread rescheduling in BukkitConsoleSender.execute() can be verified
 * without a live server.
 */
public class BukkitConsoleSenderMainThreadDispatchTest {
    private ExecutorService fakeMainThreadExecutor;

    @After
    public void tearDown() {
        if (fakeMainThreadExecutor != null) fakeMainThreadExecutor.shutdownNow();
        setServer(null);
    }

    /** Bukkit.setServer(Server) throws UnsupportedOperationException once the singleton has already been
     *  set, which happens after the first test in this JVM. Writing the private static field directly via
     *  reflection lets every test (and @After) swap the fake in and out freely. */
    private static void setServer(Server server) {
        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    @Test
    public void dispatchesOnTheSchedulerThreadNotTheCallingThreadWhenCalledOffMain() throws Exception {
        Thread callingThread = Thread.currentThread();
        AtomicReference<Thread> dispatchThread = new AtomicReference<>();
        FakeServer server = FakeServer.recordingDispatchOnto(dispatchThread);
        setServer(server.proxy);
        fakeMainThreadExecutor = server.mainThreadExecutor;

        BukkitConsoleSender sender = new BukkitConsoleSender((Plugin) proxyFor(Plugin.class, noOpHandler()));
        sender.execute("list");

        assertTrue("dispatchCommand must have run", dispatchThread.get() != null);
        assertFalse("dispatch must not run on the calling thread", dispatchThread.get() == callingThread);
    }

    @Test
    public void blocksTheCallingThreadUntilTheMainThreadDispatchCompletes() throws Exception {
        AtomicReference<Thread> dispatchThread = new AtomicReference<>();
        FakeServer server = FakeServer.recordingDispatchOnto(dispatchThread);
        setServer(server.proxy);
        fakeMainThreadExecutor = server.mainThreadExecutor;

        BukkitConsoleSender sender = new BukkitConsoleSender((Plugin) proxyFor(Plugin.class, noOpHandler()));
        sender.execute("say hi");

        // If execute() returned without waiting for the scheduled task, this would be flaky; it isn't,
        // because completion.get() only unblocks after the fake main thread actually ran dispatchCommand.
        assertTrue(dispatchThread.get() != null);
    }

    @Test
    public void dispatchesDirectlyWhenAlreadyOnTheMainThread() {
        AtomicReference<Thread> dispatchThread = new AtomicReference<>();
        FakeServer server = FakeServer.withCallingThreadAsMain(dispatchThread);
        setServer(server.proxy);

        BukkitConsoleSender sender = new BukkitConsoleSender((Plugin) proxyFor(Plugin.class, noOpHandler()));
        sender.execute("list");

        assertEquals(Thread.currentThread(), dispatchThread.get());
    }

    @Test
    public void surfacesATimeoutAsARuntimeExceptionInsteadOfHangingForever() {
        FakeServer server = FakeServer.thatNeverRunsScheduledTasks();
        setServer(server.proxy);

        BukkitConsoleSender sender = new BukkitConsoleSender((Plugin) proxyFor(Plugin.class, noOpHandler()));
        try {
            sender.execute("list");
            fail("expected a timeout to surface as a RuntimeException");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("Timed out"));
        }
    }

    private static InvocationHandler noOpHandler() {
        return (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals": return proxy == (args == null ? null : args[0]);
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "FakeProxy";
                default: return primitiveDefault(method.getReturnType());
            }
        };
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == void.class) return null;
        return 0;
    }

    private static Object proxyFor(Class<?> iface, InvocationHandler handler) {
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler);
    }

    /** Fakes just enough of Server to drive BukkitConsoleSender.execute(): getConsoleSender(),
     *  isPrimaryThread(), getScheduler().runTask(...), and dispatchCommand(...). Each factory method below
     *  models a distinct scenario rather than sharing mutable thread-identity state across them, since an
     *  earlier version's shared "primary thread" holder got clobbered when a background executor thread
     *  was created after the calling thread had already been recorded as primary. */
    private static final class FakeServer {
        final Server proxy;
        final ExecutorService mainThreadExecutor;

        private FakeServer(Server proxy, ExecutorService mainThreadExecutor) {
            this.proxy = proxy;
            this.mainThreadExecutor = mainThreadExecutor;
        }

        /** isPrimaryThread() is true only for a dedicated background thread; scheduler.runTask(...) runs
         *  work there. Models the real "calling thread is a ScriptDispatcher worker, not Bukkit's main
         *  thread" scenario that execute() must reschedule out of. */
        static FakeServer recordingDispatchOnto(AtomicReference<Thread> dispatchThread) {
            AtomicReference<Thread> mainThread = new AtomicReference<>();
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "fake-bukkit-main");
                mainThread.set(thread);
                return thread;
            });
            try {
                executor.submit(() -> { }).get(2, TimeUnit.SECONDS);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }

            ConsoleCommandSender fakeConsole = (ConsoleCommandSender) proxyFor(ConsoleCommandSender.class, noOpHandler());
            BukkitScheduler fakeScheduler = (BukkitScheduler) proxyFor(BukkitScheduler.class, (p, method, args) -> {
                if ("runTask".equals(method.getName()) && args != null && args.length == 2 && args[1] instanceof Runnable) {
                    executor.submit((Runnable) args[1]);
                    return proxyFor(BukkitTask.class, noOpHandler());
                }
                return primitiveDefault(method.getReturnType());
            });
            Server serverProxy = (Server) proxyFor(Server.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getConsoleSender": return fakeConsole;
                    case "getScheduler": return fakeScheduler;
                    case "isPrimaryThread": return Thread.currentThread() == mainThread.get();
                    case "dispatchCommand":
                        dispatchThread.set(Thread.currentThread());
                        return true;
                    default: return primitiveDefault(method.getReturnType());
                }
            });
            return new FakeServer(serverProxy, executor);
        }

        /** isPrimaryThread() is always true, so execute()'s fast path dispatches directly without ever
         *  touching the scheduler. Models the "already on the main thread" case. */
        static FakeServer withCallingThreadAsMain(AtomicReference<Thread> dispatchThread) {
            ConsoleCommandSender fakeConsole = (ConsoleCommandSender) proxyFor(ConsoleCommandSender.class, noOpHandler());
            Server serverProxy = (Server) proxyFor(Server.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getConsoleSender": return fakeConsole;
                    case "isPrimaryThread": return true;
                    case "dispatchCommand":
                        dispatchThread.set(Thread.currentThread());
                        return true;
                    default: return primitiveDefault(method.getReturnType());
                }
            });
            return new FakeServer(serverProxy, null);
        }

        /** isPrimaryThread() is always false and the scheduler accepts tasks but never runs them, so the
         *  CompletableFuture in execute() never completes and its timeout must fire. */
        static FakeServer thatNeverRunsScheduledTasks() {
            ConsoleCommandSender fakeConsole = (ConsoleCommandSender) proxyFor(ConsoleCommandSender.class, noOpHandler());
            BukkitScheduler fakeScheduler = (BukkitScheduler) proxyFor(BukkitScheduler.class, (p, method, args) -> {
                if ("runTask".equals(method.getName())) return proxyFor(BukkitTask.class, noOpHandler());
                return primitiveDefault(method.getReturnType());
            });
            Server serverProxy = (Server) proxyFor(Server.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getConsoleSender": return fakeConsole;
                    case "getScheduler": return fakeScheduler;
                    case "isPrimaryThread": return false;
                    default: return primitiveDefault(method.getReturnType());
                }
            });
            return new FakeServer(serverProxy, null);
        }
    }
}
