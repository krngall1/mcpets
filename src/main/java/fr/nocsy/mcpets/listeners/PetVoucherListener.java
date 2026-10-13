package fr.nocsy.mcpets.listeners;

import fr.nocsy.mcpets.data.PetVoucher;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class PetVoucherListener implements Listener {

    // Right clicking with a pet voucher redeems it (see PetVoucher)
    @EventHandler(priority = EventPriority.HIGH)
    public void redeem(final PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (e.getHand() == null || !PetVoucher.isVoucher(e.getItem()))
            return;

        // The voucher is never placed or used as its base item
        e.setCancelled(true);
        PetVoucher.redeem(e.getPlayer(), e.getHand());
    }
}
