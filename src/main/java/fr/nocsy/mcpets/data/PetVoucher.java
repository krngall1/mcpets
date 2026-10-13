package fr.nocsy.mcpets.data;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.PPermission;
import fr.nocsy.mcpets.data.config.FormatArg;
import fr.nocsy.mcpets.data.config.Language;
import fr.nocsy.mcpets.utils.CraftEngineCompat;
import fr.nocsy.mcpets.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Pets that are Withdrawable can be traded in for a voucher item: the owner loses the pet's permission
 * and gets the item, which can be traded. Right clicking with the voucher gives the permission back
 * to whoever holds it, if they don't already own the pet.
 * The pet id is stored on the voucher, the permission is read from the pet config when it's redeemed.
 */
public final class PetVoucher {

    private static final NamespacedKey VOUCHER_KEY = new NamespacedKey("mcpets", "pet_voucher");

    // Players with a withdraw or redeem waiting on LuckPerms, so a second click can't run it twice
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();

    public enum WithdrawResult {
        WITHDRAWN,
        NOT_WITHDRAWABLE,
        NOT_OWNED,
        FROM_RANK,
        BUSY,
        FAILED
    }

    private PetVoucher() {}

    /**
     * The voucher item for this pet, tagged with the pet id
     * null if the pet has no WithdrawItem or it can't be found
     */
    @Nullable
    public static ItemStack buildVoucher(final Pet pet, @Nullable final Player player) {
        final ItemStack item = resolveItem(pet.getWithdrawItem(), player);
        if (item == null)
            return null;
        final ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;
        meta.getPersistentDataContainer().set(VOUCHER_KEY, PersistentDataType.STRING, pet.getId());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Item from an id: "minecraft:paper" (or just "paper") is a vanilla item,
     * any other namespace (e.g. "pet_vouchers:blaze") is looked up in CraftEngine
     */
    @Nullable
    public static ItemStack resolveItem(@Nullable final String id, @Nullable final Player player) {
        if (id == null || id.isBlank())
            return null;
        final String trimmed = id.trim();
        final int colon = trimmed.indexOf(':');
        if (colon < 0 || trimmed.substring(0, colon).equalsIgnoreCase("minecraft")) {
            final Material material = Material.matchMaterial(trimmed);
            return material != null && material.isItem() ? new ItemStack(material) : null;
        }
        return CraftEngineCompat.buildItem(trimmed, player);
    }

    /**
     * Id of the pet this item is a voucher for, null if it isn't a voucher
     */
    @Nullable
    public static String getVoucherPetId(@Nullable final ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return null;
        return item.getItemMeta().getPersistentDataContainer().get(VOUCHER_KEY, PersistentDataType.STRING);
    }

    public static boolean isVoucher(@Nullable final ItemStack item) {
        return getVoucherPetId(item) != null;
    }

    /**
     * Can this pet be withdrawn at all (config wise)
     */
    public static boolean canWithdraw(@Nullable final Pet pet) {
        return pet != null
                && pet.isWithdrawable()
                && pet.getWithdrawItem() != null && !pet.getWithdrawItem().isBlank()
                && pet.getPermission() != null
                // Taking the shared permission away would lock the player out of MCPets
                && !pet.getPermission().equals(PPermission.USE.getPermission());
    }

    /**
     * Take the pet away from the player and give them its voucher.
     * The pet is revoked if it's out, the permission is removed, then the voucher is given.
     * The player is told the outcome.
     */
    public static WithdrawResult withdraw(final Player player, final String petId) {
        final WithdrawResult result = tryWithdraw(player, petId);
        switch (result) {
            case NOT_WITHDRAWABLE -> Language.PET_WITHDRAW_NOT_ALLOWED.sendMessage(player);
            case NOT_OWNED -> Language.PET_WITHDRAW_NOT_OWNED.sendMessage(player);
            case FROM_RANK -> Language.PET_WITHDRAW_FROM_RANK.sendMessage(player);
            case FAILED -> Language.PET_WITHDRAW_FAILED.sendMessage(player);
            // WITHDRAWN is announced once the voucher is given, BUSY is a double click
            default -> {}
        }
        return result;
    }

    private static WithdrawResult tryWithdraw(final Player player, final String petId) {
        final Pet pet = Pet.getFromId(petId);
        if (!canWithdraw(pet))
            return WithdrawResult.NOT_WITHDRAWABLE;
        if (!Utils.hasPermission(player.getUniqueId(), pet.getPermission()))
            return WithdrawResult.NOT_OWNED;
        // Without LuckPerms the permission can't be removed for good
        if (MCPets.getLuckPerms() == null) {
            MCPets.getLog().warning("Pet " + petId + " can't be withdrawn: LuckPerms is needed to remove its permission.");
            return WithdrawResult.FAILED;
        }

        // A pet that comes with a rank (group or wildcard permission) can't be taken away by removing the player's node
        if (!Utils.hasOwnPermissionNode(player.getUniqueId(), pet.getPermission()))
            return WithdrawResult.FROM_RANK;

        final ItemStack voucher = buildVoucher(pet, player);
        if (voucher == null) {
            MCPets.getLog().warning("Pet " + petId + " can't be withdrawn: WithdrawItem " + pet.getWithdrawItem() + " could not be found.");
            return WithdrawResult.FAILED;
        }

        final String key = pendingKey(player, petId);
        if (!pending.add(key))
            return WithdrawResult.BUSY;

        final Pet active = Pet.getActivePet(player.getUniqueId(), petId);
        if (active != null)
            active.despawn(PetDespawnReason.REVOKE);

        Utils.removePermissionAsync(player.getUniqueId(), pet.getPermission()).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(MCPets.getInstance(), () -> {
                    pending.remove(key);
                    if (error != null) {
                        MCPets.getLog().log(Level.WARNING, "Could not remove " + pet.getPermission() + " from " + player.getName() + " to withdraw " + petId, error);
                        if (player.isOnline())
                            Language.PET_WITHDRAW_FAILED.sendMessage(player);
                        return;
                    }
                    if (player.isOnline()) {
                        giveOrDrop(player, voucher);
                        Language.PET_WITHDRAWN.sendMessageFormatted(player, new FormatArg("%pet%", petName(pet)));
                    } else {
                        // The player left before LuckPerms answered, the pet is given back rather than lost
                        Utils.givePermissionAsync(player.getUniqueId(), pet.getPermission());
                    }
                }));
        return WithdrawResult.WITHDRAWN;
    }

    /**
     * Redeem the voucher the player holds in this hand.
     * Returns false if the item isn't a voucher (nothing was done).
     */
    public static boolean redeem(final Player player, final EquipmentSlot hand) {
        final ItemStack item = player.getInventory().getItem(hand);
        final String petId = getVoucherPetId(item);
        if (petId == null)
            return false;

        final Pet pet = Pet.getFromId(petId);
        if (pet == null || pet.getPermission() == null) {
            Language.PET_VOUCHER_INVALID.sendMessage(player);
            return true;
        }
        final FormatArg petArg = new FormatArg("%pet%", petName(pet));
        if (Utils.hasPermission(player.getUniqueId(), pet.getPermission())) {
            Language.PET_VOUCHER_ALREADY_OWNED.sendMessageFormatted(player, petArg);
            return true;
        }
        // Without LuckPerms the permission would be lost on the next restart, the voucher is kept
        if (MCPets.getLuckPerms() == null) {
            MCPets.getLog().warning("Voucher for " + petId + " can't be redeemed: LuckPerms is needed to give its permission.");
            Language.PET_WITHDRAW_FAILED.sendMessage(player);
            return true;
        }

        final String key = pendingKey(player, petId);
        if (!pending.add(key))
            return true;

        // The voucher is used up straight away, and given back if the permission can't be given
        final ItemStack refund = item.asOne();
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItem(hand, item.getAmount() > 0 ? item : null);

        Utils.givePermissionAsync(player.getUniqueId(), pet.getPermission()).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(MCPets.getInstance(), () -> {
                    pending.remove(key);
                    if (error != null) {
                        MCPets.getLog().log(Level.WARNING, "Could not give " + pet.getPermission() + " to " + player.getName() + " to redeem " + petId, error);
                        if (player.isOnline()) {
                            giveOrDrop(player, refund);
                            Language.PET_WITHDRAW_FAILED.sendMessage(player);
                        }
                        return;
                    }
                    if (player.isOnline())
                        Language.PET_VOUCHER_REDEEMED.sendMessageFormatted(player, petArg);
                }));
        return true;
    }

    private static void giveOrDrop(final Player player, final ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private static String pendingKey(final Player player, final String petId) {
        return player.getUniqueId() + ";" + petId;
    }

    private static String petName(final Pet pet) {
        final ItemStack icon = pet.getIcon();
        if (icon != null && icon.hasItemMeta() && icon.getItemMeta().hasDisplayName())
            return icon.getItemMeta().getDisplayName();
        return pet.getId();
    }
}
