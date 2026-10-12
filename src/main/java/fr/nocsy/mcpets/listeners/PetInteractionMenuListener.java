package fr.nocsy.mcpets.listeners;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickCallback;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.inventory.InventoryClickEvent;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.data.Items;
import fr.nocsy.mcpets.PPermission;
import fr.nocsy.mcpets.utils.Utils;
import fr.nocsy.mcpets.utils.PDCTag;
import fr.nocsy.mcpets.data.PetSkin;
import fr.nocsy.mcpets.data.PetAIMode;
import fr.nocsy.mcpets.data.PetLimits;
import fr.nocsy.mcpets.data.config.Language;
import fr.nocsy.mcpets.data.PetDespawnReason;
import fr.nocsy.mcpets.data.config.FormatArg;
import fr.nocsy.mcpets.data.inventories.PetMenu;
import fr.nocsy.mcpets.data.inventories.PetInventory;
import fr.nocsy.mcpets.data.inventories.PetInventoryHolder;

public class PetInteractionMenuListener implements Listener {

    private static final String RENAME_INPUT_KEY = "pet_name";

    /**
     * Open the rename dialog. Confirm with an empty box removes the nickname, cancel changes nothing.
     */
    public static void changeName(@NotNull final Player p) {
        final Pet pet = Optional.ofNullable(Pet.getFromLastInteractedWith(p)).orElse(Pet.getFromLastOpInteractedWith(p));
        if (pet == null) {
            Language.REVOKED_BEFORE_CHANGES.sendMessage(p);
            return;
        }

        final ActionButton confirm = ActionButton.builder(Component.text("Confirm"))
                .action(DialogAction.customClick((response, audience) -> {
                    final String input = response.getText(RENAME_INPUT_KEY);
                    applyName(p, pet, input == null ? "" : input);
                }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()))
                .build();
        // No action: the dialog just closes and the name is left alone
        final ActionButton cancel = ActionButton.builder(Component.text("Cancel")).build();

        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Rename your pet"))
                        .canCloseWithEscape(true)
                        .inputs(List.of(DialogInput.text(RENAME_INPUT_KEY, Utils.toComponent("<green>Type your pet's new name:"))
                                .width(300)
                                .maxLength(256)
                                .build()))
                        .build())
                .type(DialogType.confirmation(confirm, cancel)));

        // Shown a tick later, the menu click closes the inventory right after this call
        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) p.showDialog(dialog);
            }
        }.runTaskLater(MCPets.getInstance(), 1L);
    }

    private static void applyName(final Player p, final Pet pet, String name) {
        name = name.replace("'", "");
        name = name.replace(";;", ";").replace(";;;", ";").trim();

        if (!pet.isStillHere()) {
            Language.REVOKED_BEFORE_CHANGES.sendMessage(p);
            return;
        }

        final boolean stripColor = !p.hasPermission(PPermission.COLOR.getPermission());

        // Blank box removes the nickname
        if (name.isEmpty()) {
            pet.setDisplayName(Language.TAG_TO_REMOVE_NAME.getMessage(), true, stripColor);
            Language.NICKNAME_CHANGED_SUCCESSFULY.sendMessage(p);
            return;
        }

        final String blackListedWord = Utils.isInBlackList(name);
        if (blackListedWord != null) {
            Language.BLACKLISTED_WORD.sendMessageFormatted(p, new FormatArg("%word%", blackListedWord));
            return;
        }

        pet.setDisplayName(name, true, stripColor);
        Language.NICKNAME_CHANGED_SUCCESSFULY.sendMessage(p);
    }

    public static void mount(@NotNull final Player p, final Pet pet) {
        if (p.isInsideVehicle()) {
            Language.ALREADY_INSIDE_VEHICULE.sendMessage(p);
        } else if (!pet.setMount(p)) {
            Language.NOT_MOUNTABLE.sendMessage(p);
        }
    }

    public static void inventory(final Player p, final Pet pet) {
        final PetInventory inventory = PetInventory.get(pet);
        if (inventory != null) inventory.open(p);
    }

    public static void skins(final Player p, final Pet pet) {
        new BukkitRunnable() {
            @Override
            public void run() {
                PetSkin.openInventory(p, pet);
            }
        }.runTaskLater(MCPets.getInstance(), 2L);
    }

    public static void summon(final Player p, @NotNull final Pet pet) {
        // Already following, the button is greyed out
        if (pet.getAiMode() == PetAIMode.FOLLOW)
            return;
        if (pet.isModeLimitReached(PetAIMode.FOLLOW)) {
            Language.MAX_FOLLOWING_PETS_REACHED.sendMessageFormatted(p,
                    new FormatArg("%max%", String.valueOf(PetLimits.getMaxFollowingPets(pet.getOwner()))));
            return;
        }
        if (pet.summonToOwner() && !Language.PET_SUMMONED.getMessage().isEmpty()) {
            p.sendMessage(Language.PET_SUMMONED.getComponentWithPrefix().replaceText(TextReplacementConfig.builder()
                    .matchLiteral("%pet%")
                    .replacement(pet.getNameForMessages())
                    .build()));
        }
        p.closeInventory();
    }

    public static void revoke(final Player p, @NotNull final Pet pet) {
        pet.despawn(PetDespawnReason.REVOKE);
        Language.REVOKED.sendMessage(p);
    }

    @EventHandler
    public void click(final InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof final PetInventoryHolder holder)) {
            return;
        }

        final boolean isPetMenu = holder.getType() == PetInventoryHolder.Type.PET_INTERACTION_MENU;
        final boolean isMountMenu = holder.getType() == PetInventoryHolder.Type.MOUNT_INTERACTION_MENU;

        if (isPetMenu || isMountMenu) {
            e.setCancelled(true);

            if (!(e.getWhoClicked() instanceof final Player p)) {
                return;
            }

            if (e.getClickedInventory() == null) {
                return;
            }

            final Pet pet = Optional.ofNullable(Pet.getFromLastInteractedWith(p)).orElse(Pet.getFromLastOpInteractedWith(p));

            // Checked before isStillHere(), the pet to summon may be in an unloaded chunk
            final ItemStack clicked = e.getCurrentItem();
            if (pet != null && !pet.isRemoved() && clicked != null && clicked.hasItemMeta()
                    && Items.SUMMON_TAG.equals(PDCTag.get(clicked.getItemMeta()))) {
                summon(p, pet);
                return;
            }

            // Revoking also works on a pet whose chunk is unloaded
            if (e.getSlot() == 4 && pet != null && !pet.isRemoved()) {
                revoke(p, pet);
                p.closeInventory();
                return;
            }

            if (pet == null || !pet.isStillHere()) {
                p.closeInventory();
                Language.REVOKED_BEFORE_CHANGES.sendMessage(p);
                return;
            }

            final ItemStack it = e.getCurrentItem();
            if (it != null && it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {

                final String localizedName = PDCTag.get(it.getItemMeta());
                if (localizedName == null) return;

                if (localizedName.equals(Items.PETMENU.getLocalizedName())) {
                    openBackPetMenu(p);
                    return;
                }

                if (localizedName.equals(Items.MOUNTMENU.getLocalizedName())) {
                    openBackMountMenu(p);
                    return;
                }

                if (localizedName.equals(Items.MOUNT.getLocalizedName())) {
                    mount(p, pet);
                } else if (localizedName.equals(Items.RENAME.getLocalizedName())) {
                    changeName(p);
                } else if (localizedName.equals(Items.INVENTORY.getLocalizedName())) {
                    inventory(p, pet);
                } else if (pet.getSignalStick() != null && it.isSimilar(pet.getSignalStick())) {
                    pet.giveStickSignals(p);
                } else if (localizedName.equals(Items.SKINS.getLocalizedName())) {
                    skins(p, pet);
                }
                p.closeInventory();
            }
        }
    }

    private void openBackPetMenu(final Player p) {
        final PetMenu menu = new PetMenu(p, 0);
        menu.open(p);
    }

    private void openBackMountMenu(final Player p) {
        final fr.nocsy.mcpets.data.inventories.MountMenu menu = new fr.nocsy.mcpets.data.inventories.MountMenu(p, 0);
        menu.open(p);
    }
}
