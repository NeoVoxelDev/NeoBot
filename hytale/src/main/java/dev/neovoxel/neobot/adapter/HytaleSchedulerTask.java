package dev.neovoxel.neobot.adapter;
import dev.neovoxel.neobot.scheduler.ScheduledTask;
import org.graalvm.polyglot.HostAccess;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

public class HytaleSchedulerTask implements ScheduledTask {
    private ScheduledFuture<?> task;
    private static final Set<ScheduledFuture<?>> tasks = new HashSet<>();

    public HytaleSchedulerTask(ScheduledFuture<?> task) {
        this.task = task;
        tasks.add(task);
    }

    @HostAccess.Export
    @Override
    public void cancel() {
        task.cancel(false);
    }

    public static void cancelAllTasks() {
        tasks.forEach(t -> t.cancel(false));
        tasks.clear();
    }
}
