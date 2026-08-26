package dev.neovoxel.neobot.adapter.executor;

import org.apache.logging.log4j.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

/**
 * Verifies the LogCapture fallback added to BukkitConsoleSender against real Log4j2 classes (a
 * test-only dependency; production code only ever reaches Log4j2 via reflection, since the bukkit
 * module compiles against 1.8.8, which predates it). Each fake dispatchCommand here never calls
 * sendMessage on the sender it's handed, only writes to the root logger directly - reproducing how
 * VanillaCommandWrapper routes vanilla/Brigadier commands straight to the DedicatedServer console
 * logger instead of the passed-in CommandSender.
 */
public class BukkitConsoleSenderLogCaptureTest {
    @After
    public void tearDown() {
        setServer(null);
    }

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
    public void recoversVanillaCommandOutputFromTheRootLoggerWhenSendMessageIsNeverCalled() {
        Server serverProxy = (Server) proxyFor(Server.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getConsoleSender": return proxyFor(ConsoleCommandSender.class, noOpHandler());
                case "isPrimaryThread": return true;
                case "dispatchCommand":
                    LogManager.getRootLogger().error("There are 0 of a max of 30 players online: ");
                    return true;
                default: return primitiveDefault(method.getReturnType());
            }
        });
        setServer(serverProxy);

        BukkitConsoleSender sender = new BukkitConsoleSender((Plugin) proxyFor(Plugin.class, noOpHandler()));
        sender.execute("list");

        assertEquals("There are 0 of a max of 30 players online: ", sender.getResult());
    }

    @Test
    public void ignoresLogRecordsFromOtherThreadsWhileDispatching() {
        Server serverProxy = (Server) proxyFor(Server.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getConsoleSender": return proxyFor(ConsoleCommandSender.class, noOpHandler());
                case "isPrimaryThread": return true;
                case "dispatchCommand": {
                    Thread other = new Thread(() -> LogManager.getRootLogger().error("noise from an unrelated thread"));
                    other.start();
                    other.join();
                    return true;
                }
                default: return primitiveDefault(method.getReturnType());
            }
        });
        setServer(serverProxy);

        BukkitConsoleSender sender = new BukkitConsoleSender((Plugin) proxyFor(Plugin.class, noOpHandler()));
        sender.execute("list");

        assertEquals("", sender.getResult());
    }

    @Test
    public void prefersDirectSendMessageCaptureOverTheLogFallbackWhenBothOccur() {
        Server serverProxy = (Server) proxyFor(Server.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getConsoleSender": return proxyFor(ConsoleCommandSender.class, noOpHandler());
                case "isPrimaryThread": return true;
                case "dispatchCommand": {
                    ConsoleCommandSender dispatchedSender = (ConsoleCommandSender) args[0];
                    dispatchedSender.sendMessage("direct message");
                    LogManager.getRootLogger().error("log fallback message");
                    return true;
                }
                default: return primitiveDefault(method.getReturnType());
            }
        });
        setServer(serverProxy);

        BukkitConsoleSender sender = new BukkitConsoleSender((Plugin) proxyFor(Plugin.class, noOpHandler()));
        sender.execute("list");

        assertEquals("direct message", sender.getResult());
    }

    @Test
    public void reportsUnknownCommandWhenDispatchRecognizesNoMatchingCommandNode() {
        // Mirrors CraftServer#dispatchCommand: when Brigadier's parse matches zero context nodes (the root
        // literal isn't a known command at all), it returns false without calling performCommand, so
        // neither sendMessage nor the root logger ever see anything.
        Server serverProxy = (Server) proxyFor(Server.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getConsoleSender": return proxyFor(ConsoleCommandSender.class, noOpHandler());
                case "isPrimaryThread": return true;
                case "dispatchCommand": return false;
                default: return primitiveDefault(method.getReturnType());
            }
        });
        setServer(serverProxy);

        BukkitConsoleSender sender = new BukkitConsoleSender((Plugin) proxyFor(Plugin.class, noOpHandler()));
        sender.execute("sc ty missqwq 0 0 0");

        assertEquals("error:unknown-command", sender.getResult());
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
}
