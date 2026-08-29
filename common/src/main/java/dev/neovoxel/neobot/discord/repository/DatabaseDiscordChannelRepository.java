package dev.neovoxel.neobot.discord.repository;

import dev.neovoxel.neobot.discord.model.DiscordChannelBinding;
import dev.neovoxel.nsapi.DatabaseStorage;
import dev.neovoxel.nsapi.entity.Row;
import dev.neovoxel.nsapi.table.DatabaseTable;

import java.util.ArrayList;
import java.util.List;

public class DatabaseDiscordChannelRepository implements DiscordChannelRepository {
    public static final String TABLE = "neobot_discord_channels";
    private final DatabaseStorage storage;

    public DatabaseDiscordChannelRepository(DatabaseStorage storage) {
        this.storage = storage;
    }

    private DatabaseTable table() { return storage.table(TABLE); }

    @Override
    public void initialize() {
        table().create().column("channel_id", "VARCHAR(20)", "PRIMARY KEY")
                .column("guild_id", "VARCHAR(20)").execute();
    }

    @Override
    public synchronized boolean add(DiscordChannelBinding binding) {
        if (!table().select("channel_id").where("channel_id", binding.getChannelId()).execute().map().isEmpty()) return false;
        table().insert().column("channel_id", binding.getChannelId()).column("guild_id", binding.getGuildId()).execute();
        return true;
    }

    @Override
    public synchronized boolean remove(String guildId, String channelId) {
        if (table().select("channel_id").where("channel_id", channelId).where("guild_id", guildId).execute().map().isEmpty()) return false;
        table().delete().where("channel_id", channelId).where("guild_id", guildId).execute();
        return true;
    }

    @Override
    public List<DiscordChannelBinding> findAll() {
        List<DiscordChannelBinding> result = new ArrayList<>();
        for (Row row : table().select("guild_id", "channel_id").execute().map()) {
            result.add(new DiscordChannelBinding(row.getString("guild_id"), row.getString("channel_id")));
        }
        return result;
    }
}
