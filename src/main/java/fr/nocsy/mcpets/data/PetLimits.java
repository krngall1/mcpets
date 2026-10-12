package fr.nocsy.mcpets.data;

import fr.nocsy.mcpets.data.config.GlobalConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Each player's limits for following and for sitting/wandering pets.
 * The base comes from config.yml (MaxFollowingPets, MaxNonFollowingPets) and ranks raise it with permissions:
 * mcpets.follow_limit.<1-16> and mcpets.non_follow_limit.<1-16> add their number (only the highest counts),
 * mcpets.follow_limit.* and mcpets.non_follow_limit.* remove the limit.
 * 0 means no limit.
 */
public class PetLimits {

    public static final String FOLLOW_NODE = "mcpets.follow_limit.";
    public static final String NON_FOLLOW_NODE = "mcpets.non_follow_limit.";
    private static final int MAX_BONUS = 16;

    public static int getMaxFollowingPets(final UUID owner) {
        return getLimit(owner, GlobalConfig.getInstance().getMaxFollowingPets(), FOLLOW_NODE);
    }

    public static int getMaxNonFollowingPets(final UUID owner) {
        return getLimit(owner, GlobalConfig.getInstance().getMaxNonFollowingPets(), NON_FOLLOW_NODE);
    }

    private static int getLimit(final UUID owner, final int base, final String node) {
        if (base <= 0)
            return 0;
        final Player player = owner == null ? null : Bukkit.getPlayer(owner);
        // Pets of offline owners don't change mode, so the base is enough
        if (player == null)
            return base;
        if (has(player, node + "*"))
            return 0;
        for (int bonus = MAX_BONUS; bonus > 0; bonus--) {
            if (has(player, node + bonus))
                return base + bonus;
        }
        return base;
    }

    // Only nodes that are actually given count, so ops don't get every bonus by default
    private static boolean has(final Player player, final String permission) {
        return player.isPermissionSet(permission) && player.hasPermission(permission);
    }
}
