package dev.neovoxel.neobot.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded-time source/message idempotency guard. */
public final class SourceDeduplicator {
    private final Map<String, Long> seen = new ConcurrentHashMap<>();
    private final long ttlMillis;
    public SourceDeduplicator(long ttlMillis) { this.ttlMillis = Math.max(1000L, ttlMillis); }
    public boolean first(String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) return true;
        long now = System.currentTimeMillis();
        Long previous = seen.putIfAbsent(sourceId, now);
        if (previous == null) return true;
        if (now - previous > ttlMillis) { seen.put(sourceId, now); return true; }
        return false;
    }
    public void clear() { seen.clear(); }
}
