package fr.nocsy.mcpets.commands.mcpets;

import fr.nocsy.mcpets.PPermission;
import fr.nocsy.mcpets.commands.AArgument;
import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.data.PetAIMode;
import fr.nocsy.mcpets.data.PetDespawnReason;
import fr.nocsy.mcpets.data.PlacedPets;
import fr.nocsy.mcpets.data.config.FormatArg;
import fr.nocsy.mcpets.data.config.Language;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /mcpets revokeAll <world>
 * Revokes every sitting and wandering pet in the given world, e.g. before or after a resource world is reset.
 * Following pets aren't touched, they come back with their owner.
 */
public class ArgumentRevokeAll extends AArgument {

    public ArgumentRevokeAll(CommandSender sender, String[] args) {
        super("revokeAll", new int[]{2}, sender, args, "/mcpets revokeAll <world>");
    }

    @Override
    public void commandEffect() {
        final String worldName = args[1];
        int count = 0;

        final List<Pet> pets = new ArrayList<>();
        for (final List<Pet> ownerPets : Pet.getActivePets().values())
            pets.addAll(ownerPets);

        for (final Pet pet : pets) {
            if (pet.getAiMode() != PetAIMode.SIT && pet.getAiMode() != PetAIMode.WANDER) continue;
            if (!worldName.equals(getWorldName(pet))) continue;

            final UUID owner = pet.getOwner();
            final Component name = pet.getNameForMessages();
            pet.despawn(PetDespawnReason.DIMENSION_RESET);
            notifyOwner(owner, name);
            count++;
        }

        // Pets MCPets doesn't have in memory, e.g. of owners who haven't joined since a restart
        for (final Map.Entry<UUID, String> forgotten : PlacedPets.forgetAllIn(worldName)) {
            final Pet template = Pet.getFromId(forgotten.getValue());
            notifyOwner(forgotten.getKey(), template != null ? template.getNameForMessages() : Component.text(forgotten.getValue()));
            count++;
        }

        Language.REVOKE_ALL_DONE.sendMessageFormatted(sender,
                new FormatArg("%count%", count),
                new FormatArg("%world%", worldName));
    }

    /**
     * The world the pet is in, or the one it was saved in if its world isn't loaded anymore
     */
    private static String getWorldName(final Pet pet) {
        try {
            final Location loc = pet.getLocation();
            if (loc != null && loc.getWorld() != null)
                return loc.getWorld().getName();
        } catch (final IllegalArgumentException ignored) {
            // The world was unloaded, e.g. it's being reset
        }
        return pet.getOwner() != null ? PlacedPets.getWorldName(pet.getOwner(), pet.getId()) : null;
    }

    private static void notifyOwner(final UUID owner, final Component name) {
        if (owner == null || Language.PET_REVOKED_DIMENSION_RESET.getMessage().isEmpty()) return;
        final Player player = Bukkit.getPlayer(owner);
        if (player == null) return;
        player.sendMessage(Language.PET_REVOKED_DIMENSION_RESET.getComponentWithPrefix().replaceText(TextReplacementConfig.builder()
                .matchLiteral("%pet%")
                .replacement(name)
                .build()));
    }

    @Override
    protected boolean additionalConditions() {
        return sender.hasPermission(PPermission.ADMIN.getPermission());
    }
}
