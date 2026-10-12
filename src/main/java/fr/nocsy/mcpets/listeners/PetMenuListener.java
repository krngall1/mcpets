package fr.nocsy.mcpets.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryClickEvent;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.commands.mcpets.ArgumentMenu;
import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.data.inventories.MountInteractionMenu;
import fr.nocsy.mcpets.data.inventories.PetInteractionMenu;
import org.bukkit.Bukkit;
import fr.nocsy.mcpets.data.inventories.PetMenu;
import fr.nocsy.mcpets.utils.MenuPaginationHelper;
import fr.nocsy.mcpets.data.inventories.PetInventoryHolder;

public class PetMenuListener implements Listener {

    @EventHandler
    public void click(final InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof final PetInventoryHolder holder)) {
            return;
        }

        if (holder.getType() != PetInventoryHolder.Type.PET_MENU) {
            return;
        }

        if (!(e.getWhoClicked() instanceof final Player p)) {
            return;
        }

        e.setCancelled(true);

        final ItemStack it = e.getCurrentItem();
        if (it == null || it.getType().isAir()) {
            return;
        }

        if (MenuPaginationHelper.handlePagination(it, p,
                "AlmPetPreviousPage;", "AlmPetNextPage;",
            (player, page) -> new PetMenu(player, page).open(player))) {
            return;
        }

        final Pet petObject = Pet.getFromIcon(it);
        if (petObject != null) {
            clickPetIcon(p, petObject);
        }
    }

    /**
     * A pet icon was clicked in a pets menu: an active pet opens its own menu, any other pet is summoned
     */
    public static void clickPetIcon(final Player p, final Pet petObject) {
        p.closeInventory();
        final Pet active = Pet.getActivePet(p.getUniqueId(), petObject.getId());
        if (active == null) {
            petObject.copy().spawnWithMessage(p);
            return;
        }

        // Opened on the next tick, not while the click on this menu is still being handled
        Bukkit.getScheduler().runTask(MCPets.getInstance(), () -> {
            final boolean loaded = active.isStillHere() && active.getActiveMob().getEntity().getBukkitEntity().isValid();
            // A pet in an unloaded chunk gets the simple menu, its other buttons need the pet in the world
            if (!loaded) {
                ArgumentMenu.openCommandMenu(p, active, active.isMount());
            } else if (active.isMount()) {
                active.setLastInteractedWith(p);
                new MountInteractionMenu(active, p.getUniqueId()).open(p);
            } else {
                active.setLastInteractedWith(p);
                new PetInteractionMenu(active, p.getUniqueId()).open(p);
            }
        });
    }

}
