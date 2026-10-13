package fr.nocsy.mcpets.utils;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * CraftEngine custom items.
 * CraftEngine is a soft dependency: check isLoaded() before calling anything else here,
 * so its classes are never touched on a server without it.
 */
public final class CraftEngineCompat {

    private CraftEngineCompat() {}

    public static boolean isLoaded() {
        return Bukkit.getPluginManager().isPluginEnabled("CraftEngine");
    }

    /**
     * Build the CraftEngine item with this id (e.g. "pet_vouchers:blaze")
     * null if CraftEngine isn't loaded or doesn't know the id
     */
    @Nullable
    public static ItemStack buildItem(final String id, @Nullable final Player player) {
        if (!isLoaded())
            return null;
        return Hook.buildItem(id, player);
    }

    // Kept apart so the CraftEngine classes are only loaded when CraftEngine is there
    private static final class Hook {
        private static ItemStack buildItem(final String id, @Nullable final Player player) {
            final BukkitItemDefinition item = CraftEngineItems.byId(id);
            if (item == null)
                return null;
            return player != null ? item.buildBukkitItem(player) : item.buildBukkitItem();
        }
    }
}
