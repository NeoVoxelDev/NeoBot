package dev.neovoxel.neobot.discord.repository;

import dev.neovoxel.neobot.discord.model.DiscordAccountBinding;

import java.util.List;
import java.util.UUID;

public interface DiscordAccountRepository {
    void initialize();
    DiscordAccountBinding findByMinecraftUuid(UUID uuid);
    List<DiscordAccountBinding> findByDiscordUserId(String userId);
    List<DiscordAccountBinding> findAll();
    boolean add(DiscordAccountBinding binding, int maximumBindingsPerUser);
    boolean removeByMinecraftUuid(UUID uuid);
    default DiscordAccountBinding findByQqUserId(String userId) { return null; }
    default boolean bindQqUser(UUID uuid, String qqUserId) { return false; }
    default boolean unbindQqUser(UUID uuid, String qqUserId) { return false; }
}
