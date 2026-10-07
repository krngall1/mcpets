package fr.nocsy.mcpets.mythicmobs.mechanics;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.data.PetAIMode;
import fr.nocsy.mcpets.data.PetDespawnReason;
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

        OfflinePlayer owner = Bukkit.getOfflinePlayer(UUID.fromString(ownerUUID));
        System.out.println(owner.getName());
        if (!owner.hasPlayedBefore() || !owner.isOnline()) {
            System.out.println("Couldn't find online player (" + ownerUUID + ").");
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

        List<Pet> ownerPets =  Pet.getActivePetsForOwner(UUID.fromString(ownerUUID));
        for (Pet ownerPet : ownerPets) {
            if (ownerPet.getId().equals(entityPetID)) {
                Optional<ActiveMob> activeMob = MCPets.getMythicMobs().getMobManager().getActiveMob(entity.getUniqueId());
                if (activeMob.isEmpty()) return SkillResult.CONDITION_FAILED;;
                ownerPet.changeActiveMobTo(activeMob.get(), UUID.fromString(ownerUUID), aiMode, PetDespawnReason.REPLACED);
            }
        }

        return SkillResult.SUCCESS;
    }

}
