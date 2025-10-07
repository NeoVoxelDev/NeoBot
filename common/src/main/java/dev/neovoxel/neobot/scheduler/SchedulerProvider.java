package dev.neovoxel.neobot.scheduler;

public interface SchedulerProvider {
    ScheduledTask submit(Runnable task);

    ScheduledTask submitAsync(Runnable task);

    ScheduledTask submit(Runnable task, long delay);

    ScheduledTask submitAsync(Runnable task, long delay);

    ScheduledTask submit(Runnable task, long delay, long period);

    ScheduledTask submitAsync(Runnable task, long delay, long period);

    void shutdown();
}
