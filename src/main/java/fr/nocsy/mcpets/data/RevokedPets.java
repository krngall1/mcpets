package fr.nocsy.mcpets.data;

import fr.nocsy.mcpets.MCPets;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Pets that were revoked while their chunk wasn't loaded.
 * Their entity is saved with the chunk and comes back when it loads, so it's removed then.
 * Kept in revoked-pets.yml so it still works after a restart.
 */
public class RevokedPets {

    // A pet that never comes back (e.g. its chunk got deleted) is forgotten after a while
    private static final long KEEP_FOR = TimeUnit.DAYS.toMillis(30);

    private static Map<UUID, Long> revoked;

    private static File getFile() {
        return new File(MCPets.getInstance().getDataFolder(), "revoked-pets.yml");
    }

    private static Map<UUID, Long> get() {
        if (revoked != null)
            return revoked;

        revoked = new HashMap<>();
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(getFile());
        final long now = System.currentTimeMillis();
        for (final String key : config.getKeys(false)) {
            final long revokedAt = config.getLong(key);
            if (now - revokedAt > KEEP_FOR) continue;
            try {
                revoked.put(UUID.fromString(key), revokedAt);
            } catch (final IllegalArgumentException ignored) {
                // Not an entity uuid, skipped
            }
        }
        return revoked;
    }

    /**
     * Remember that the entity with this uuid must be removed when it's loaded again
     */
    public static void add(final UUID entityId) {
        get().put(entityId, System.currentTimeMillis());
        save();
    }

    /**
     * Say whether the entity with this uuid was revoked
     */
    public static boolean isRevoked(final UUID entityId) {
        return get().containsKey(entityId);
    }

    /**
     * Say whether the entity with this uuid was revoked, and forget it
     */
    public static boolean consume(final UUID entityId) {
        if (get().remove(entityId) == null)
            return false;
        save();
        return true;
    }

    private static void save() {
        final YamlConfiguration config = new YamlConfiguration();
        revoked.forEach((uuid, revokedAt) -> config.set(uuid.toString(), revokedAt));
        try {
            config.save(getFile());
        } catch (final IOException ex) {
            MCPets.getLog().log(Level.WARNING, "Could not save revoked-pets.yml", ex);
        }
    }
}
