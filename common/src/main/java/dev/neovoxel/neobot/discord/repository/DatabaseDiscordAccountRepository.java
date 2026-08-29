package dev.neovoxel.neobot.discord.repository;

import dev.neovoxel.neobot.discord.model.DiscordAccountBinding;
import dev.neovoxel.nsapi.DatabaseStorage;
import dev.neovoxel.nsapi.entity.Row;
import dev.neovoxel.nsapi.table.DatabaseTable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseDiscordAccountRepository implements DiscordAccountRepository {
    public static final String TABLE = "neobot_discord_accounts";
    private final DatabaseStorage storage;

    public DatabaseDiscordAccountRepository(DatabaseStorage storage) { this.storage = storage; }
    private DatabaseTable table() { return storage.table(TABLE); }

    @Override
    public void initialize() {
        table().create().column("minecraft_uuid", "VARCHAR(36)", "PRIMARY KEY")
                .column("minecraft_name", "VARCHAR(64)")
                .column("discord_user_id", "VARCHAR(20)")
                .column("qq_user_id", "VARCHAR(20)").execute();
    }

    private DiscordAccountBinding map(Row row) {
        DiscordAccountBinding binding = new DiscordAccountBinding(UUID.fromString(row.getString("minecraft_uuid")),
                row.getString("minecraft_name"), row.getString("discord_user_id"));
        try { binding.setQqUserId(row.getString("qq_user_id")); } catch (RuntimeException ignored) { }
        return binding;
    }

    @Override
    public DiscordAccountBinding findByMinecraftUuid(UUID uuid) {
        List<Row> rows = table().select("minecraft_uuid", "minecraft_name", "discord_user_id", "qq_user_id")
                .where("minecraft_uuid", uuid.toString()).execute().map();
        return rows.isEmpty() ? null : map(rows.get(0));
    }

    @Override
    public List<DiscordAccountBinding> findByDiscordUserId(String userId) {
        List<DiscordAccountBinding> result = new ArrayList<>();
        for (Row row : table().select("minecraft_uuid", "minecraft_name", "discord_user_id")
                .where("discord_user_id", userId).execute().map()) result.add(map(row));
        return result;
    }

    @Override
    public List<DiscordAccountBinding> findAll() {
        List<DiscordAccountBinding> result = new ArrayList<>();
        for (Row row : table().select("minecraft_uuid", "minecraft_name", "discord_user_id").execute().map()) result.add(map(row));
        return result;
    }

    @Override
    public synchronized boolean add(DiscordAccountBinding binding, int maximumBindingsPerUser) {
        if (findByMinecraftUuid(binding.getMinecraftUuid()) != null
                || findByDiscordUserId(binding.getDiscordUserId()).size() >= maximumBindingsPerUser) return false;
        table().insert().column("minecraft_uuid", binding.getMinecraftUuid().toString())
                .column("minecraft_name", binding.getMinecraftName())
                .column("discord_user_id", binding.getDiscordUserId()).execute();
        return true;
    }

    @Override
    public synchronized boolean removeByMinecraftUuid(UUID uuid) {
        if (findByMinecraftUuid(uuid) == null) return false;
        table().delete().where("minecraft_uuid", uuid.toString()).execute();
        return true;
    }

    @Override
    public synchronized DiscordAccountBinding findByQqUserId(String userId) {
        List<Row> rows = table().select("minecraft_uuid", "minecraft_name", "discord_user_id", "qq_user_id")
                .where("qq_user_id", userId).execute().map();
        if (rows.isEmpty()) return null;
        DiscordAccountBinding binding = map(rows.get(0));
        binding.setQqUserId(userId);
        return binding;
    }

    @Override
    public synchronized boolean bindQqUser(UUID uuid, String qqUserId) {
        if (qqUserId == null || !qqUserId.matches("[0-9]{1,20}") || findByQqUserId(qqUserId) != null) return false;
        DiscordAccountBinding binding = findByMinecraftUuid(uuid);
        if (binding == null) {
            table().insert().column("minecraft_uuid", uuid.toString()).column("minecraft_name", "")
                    .column("discord_user_id", "").column("qq_user_id", qqUserId).execute();
        } else if (binding.getQqUserId() != null && !binding.getQqUserId().isEmpty()) return false;
        else {
            table().delete().where("minecraft_uuid", uuid.toString()).execute();
            table().insert().column("minecraft_uuid", uuid.toString()).column("minecraft_name", binding.getMinecraftName())
                    .column("discord_user_id", binding.getDiscordUserId()).column("qq_user_id", qqUserId).execute();
        }
        return true;
    }

    @Override
    public synchronized boolean unbindQqUser(UUID uuid, String qqUserId) {
        DiscordAccountBinding binding = findByMinecraftUuid(uuid);
        if (binding == null || !qqUserId.equals(binding.getQqUserId())) return false;
        if (binding.getDiscordUserId() == null || binding.getDiscordUserId().isEmpty())
            return removeByMinecraftUuid(uuid);
        table().delete().where("minecraft_uuid", uuid.toString()).execute();
        table().insert().column("minecraft_uuid", uuid.toString()).column("minecraft_name", binding.getMinecraftName())
                .column("discord_user_id", binding.getDiscordUserId()).column("qq_user_id", "").execute();
        return true;
    }
}
