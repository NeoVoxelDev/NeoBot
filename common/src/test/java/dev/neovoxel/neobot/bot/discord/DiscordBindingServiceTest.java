package dev.neovoxel.neobot.bot.discord;

import dev.neovoxel.neobot.discord.DiscordService;
import dev.neovoxel.neobot.discord.model.DiscordAccountBinding;
import dev.neovoxel.neobot.discord.model.DiscordChannelBinding;
import dev.neovoxel.neobot.discord.repository.DiscordAccountRepository;
import dev.neovoxel.neobot.discord.repository.DiscordChannelRepository;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DiscordBindingServiceTest {
    @Test
    void keepsSnowflakesAsStringsAndRejectsDuplicates() {
        MemoryChannels channels = new MemoryChannels();
        DiscordService service = new DiscordService(null, channels, new MemoryAccounts());
        assertTrue(service.bindChannel("9223372036854775807", "123456789012345678"));
        assertFalse(service.bindChannel("9223372036854775807", "123456789012345678"));
        assertFalse(service.bindChannel("not-a-snowflake", "123456789012345678"));
        assertEquals("123456789012345678", service.channels().get(0).getChannelId());
        assertTrue(service.unbindChannel("9223372036854775807", "123456789012345678"));
        assertFalse(service.unbindChannel("9223372036854775807", "123456789012345678"));
    }

    @Test
    void enforcesOnePlayerAndOneDiscordUserBindingBoundary() {
        MemoryAccounts accounts = new MemoryAccounts();
        DiscordService service = new DiscordService(null, new MemoryChannels(), accounts);
        UUID first = UUID.randomUUID();
        assertTrue(service.adminBind("first", first, "123456789012345678"));
        assertFalse(service.adminBind("first", first, "223456789012345678"));
        assertFalse(service.adminBind("second", UUID.randomUUID(), "123456789012345678"));
        assertTrue(service.unbindAccount(first));
        assertFalse(service.unbindAccount(first));
    }

    @Test
    void directBindingRejectsDuplicatesAndOnlyTheOwnerCanUnbind() {
        MemoryAccounts accounts = new MemoryAccounts();
        DiscordService service = new DiscordService(null, new MemoryChannels(), accounts);
        UUID playerId = UUID.randomUUID();
        TestOfflinePlayer player = new TestOfflinePlayer("Steve", playerId);

        assertEquals(DiscordService.AccountResult.SUCCESS,
                service.bindFromDiscord("Steve", player, "123456789012345678"));
        assertEquals(DiscordService.AccountResult.PLAYER_ALREADY_BOUND,
                service.bindFromDiscord("Steve", player, "223456789012345678"));
        assertEquals(DiscordService.AccountResult.NOT_OWNER,
                service.unbindFromDiscord("Steve", player, "223456789012345678"));
        assertEquals(DiscordService.AccountResult.SUCCESS,
                service.unbindFromDiscord("Steve", player, "123456789012345678"));
        assertEquals(DiscordService.AccountResult.INVALID_PLAYER,
                service.bindFromDiscord("not a player", player, "123456789012345678"));
    }

    private static class TestOfflinePlayer extends dev.neovoxel.neobot.adapter.OfflinePlayer {
        private TestOfflinePlayer(String name, UUID uuid) { super(name, uuid); }
        public boolean isOnline() { return false; }
    }

    private static class MemoryChannels implements DiscordChannelRepository {
        private final List<DiscordChannelBinding> values = new ArrayList<>();
        public void initialize() { }
        public boolean add(DiscordChannelBinding binding) { if (findAll().stream().anyMatch(v -> v.getChannelId().equals(binding.getChannelId()))) return false; values.add(binding); return true; }
        public boolean remove(String guild, String channel) { return values.removeIf(v -> v.getGuildId().equals(guild) && v.getChannelId().equals(channel)); }
        public List<DiscordChannelBinding> findAll() { return new ArrayList<>(values); }
    }
    private static class MemoryAccounts implements DiscordAccountRepository {
        private final Map<UUID, DiscordAccountBinding> values = new HashMap<>();
        public void initialize() { }
        public DiscordAccountBinding findByMinecraftUuid(UUID id) { return values.get(id); }
        public List<DiscordAccountBinding> findByDiscordUserId(String id) { List<DiscordAccountBinding> result = new ArrayList<>(); for (DiscordAccountBinding v : values.values()) if (v.getDiscordUserId().equals(id)) result.add(v); return result; }
        public List<DiscordAccountBinding> findAll() { return new ArrayList<>(values.values()); }
        public boolean add(DiscordAccountBinding binding, int max) { if (values.containsKey(binding.getMinecraftUuid()) || findByDiscordUserId(binding.getDiscordUserId()).size() >= max) return false; values.put(binding.getMinecraftUuid(), binding); return true; }
        public boolean removeByMinecraftUuid(UUID id) { return values.remove(id) != null; }
    }
}
