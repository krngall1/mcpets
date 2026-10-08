package fr.nocsy.mcpets.mythicmobs.mechanics;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.data.PetAIMode;
import fr.nocsy.mcpets.data.config.Language;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.placeholders.PlaceholderString;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

@MythicMechanic(
        name = "petReload"
)
public class PetReloadMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final PlaceholderString placeholderOwnerUUID;

    public PetReloadMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getContainer().getFile());

        MythicLineConfig config = event.getConfig();

        placeholderOwnerUUID = config.getPlaceholderString(new String[]{"owner", "uuid"}, "");
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {

        String ownerUUID = placeholderOwnerUUID.get(data.getCaster());

        Entity entity = BukkitAdapter.adapt(target);

        Pet pet = Pet.getFromEntity(entity);
        if (pet != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    pet.setNameTag(pet.getCurrentName(), true);
                }
            }.runTaskLater(MCPets.getInstance(), 10L);
            return SkillResult.SUCCESS;
        }
        System.out.println("Pet was null");

        // The owner stored on the entity by MCPets wins, else the uuid given to the mechanic (e.g. a saved variable)
        final UUID ownerId = resolveOwner(entity, ownerUUID);
        if (ownerId == null) {
            System.out.println("Couldn't find the owner of the pet (" + ownerUUID + ").");
            return SkillResult.CONDITION_FAILED;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        System.out.println(owner.getName());
        if (!owner.hasPlayedBefore() || !owner.isOnline()) {
            System.out.println("Couldn't find online player (" + ownerId + ").");
            return SkillResult.CONDITION_FAILED;
        }

        NamespacedKey key = new NamespacedKey("mcpets", "pet_id");
        String entityPetID = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (entityPetID == null ) {
            System.out.println("Entity pet ID was null");
            return SkillResult.CONDITION_FAILED;
        }

        Set<String> tags = entity.getScoreboardTags();
        PetAIMode aiMode = PetAIMode.FOLLOW;
        if (tags.contains("sit")) aiMode = PetAIMode.SIT;
        else if (tags.contains("wander")) aiMode = PetAIMode.WANDER;

        Optional<ActiveMob> activeMob = MCPets.getMythicMobs().getMobManager().getActiveMob(entity.getUniqueId());
        if (activeMob.isEmpty()) return SkillResult.CONDITION_FAILED;

        // The pet MCPets still has in memory (e.g. after a MythicMobs reload or a chunk reload)
        Pet ownerPet = Pet.getActivePetsForOwner(ownerId).stream()
                .filter(activePet -> activePet.getId().equals(entityPetID))
                .findFirst()
                .orElse(null);

        // After a full restart MCPets has forgotten sitting and wandering pets that stayed in the world,
        // so they're rebuilt from their pet id. Following pets are respawned on join instead.
        if (ownerPet == null) {
            if (aiMode == PetAIMode.FOLLOW) return SkillResult.CONDITION_FAILED;
            ownerPet = Pet.getFromId(entityPetID);
            if (ownerPet == null) return SkillResult.CONDITION_FAILED;
        }

        final Pet relinkedPet = ownerPet;
        relinkedPet.relink(activeMob.get(), ownerId, aiMode);
        new BukkitRunnable() {
            @Override
            public void run() {
                relinkedPet.setNameTag(relinkedPet.getCurrentName(), true);
            }
        }.runTaskLater(MCPets.getInstance(), 10L);

        return SkillResult.SUCCESS;
    }

    private static UUID resolveOwner(final Entity entity, final String fallback) {
        final String stored = entity.getPersistentDataContainer().get(Pet.OWNER_KEY, PersistentDataType.STRING);
        for (final String candidate : new String[]{stored, fallback}) {
            if (candidate == null || candidate.isEmpty()) continue;
            try {
                return UUID.fromString(candidate);
            } catch (final IllegalArgumentException ignored) {
                // e.g. "null" when the variable was saved before the pet was linked
            }
        }
        return null;
    }

}
