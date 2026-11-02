package fr.nocsy.mcpets.mythicmobs.mechanics;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.data.PetAIMode;
import fr.nocsy.mcpets.data.RevokedPets;
import fr.nocsy.mcpets.data.config.Language;
import fr.nocsy.mcpets.utils.debug.Debugger;
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

    // Unlinked pet entities whose owner was offline, by owner. They're relinked when the owner joins
    // (relinkWaiting), so the reload skill doesn't have to keep retrying while the owner is away
    private static final Map<UUID, Set<UUID>> waitingForOwner = new HashMap<>();

    public PetReloadMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getContainer().getFile());

        MythicLineConfig config = event.getConfig();

        placeholderOwnerUUID = config.getPlaceholderString(new String[]{"owner", "uuid"}, "");
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        // MythicMobs can run this off the main thread (e.g. after a delay in a skill). Saving pet data and
        // touching entities there isn't safe: several pets saving at once throws a ConcurrentModificationException
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(MCPets.getInstance(), () -> castOnMainThread(data, target));
            return SkillResult.SUCCESS;
        }
        return castOnMainThread(data, target);
    }

    private SkillResult castOnMainThread(SkillMetadata data, AbstractEntity target) {

        String ownerUUID = placeholderOwnerUUID.get(data.getCaster());

        Entity entity = BukkitAdapter.adapt(target);

        // The pet was revoked while its chunk wasn't loaded (see RevokedPets), so it isn't brought back
        if (RevokedPets.consume(entity.getUniqueId())) {
            MCPets.getModeler().removeModel(entity.getUniqueId());
            MCPets.getMythicMobs().getMobManager().getActiveMob(entity.getUniqueId()).ifPresent(ActiveMob::remove);
            entity.remove();
            return SkillResult.CONDITION_FAILED;
        }

        Pet pet = Pet.getFromEntity(entity);
        if (pet != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    refreshNameTag(pet);
                }
            }.runTaskLater(MCPets.getInstance(), 10L);
            return SkillResult.SUCCESS;
        }

        // A following pet's entity that survived a restart: MCPets respawns following pets near their owner,
        // so relinking this one would leave two copies
        if (Pet.isLeftoverFollowEntity(entity, null)) {
            Pet.removeUntrackedEntity(entity);
            return SkillResult.CONDITION_FAILED;
        }

        // The owner stored on the entity by MCPets wins, else the uuid given to the mechanic (e.g. a saved variable)
        final UUID ownerId = resolveOwner(entity, ownerUUID);
        if (ownerId == null) {
            MCPets.getLog().warning("Pet entity " + entity.getUniqueId() + " at " + entity.getLocation().toVector() + " has no owner stored, it can't be relinked");
            return SkillResult.CONDITION_FAILED;
        }

        // Owner offline: the entity waits for them to join instead of being retried.
        // (Bukkit.getPlayer is a map lookup, OfflinePlayer.hasPlayedBefore read the player's data file from disk)
        if (Bukkit.getPlayer(ownerId) == null) {
            waitingForOwner.computeIfAbsent(ownerId, k -> new HashSet<>()).add(entity.getUniqueId());
            Debugger.send("§7Pet entity " + entity.getUniqueId() + " is waiting for its owner " + ownerId + " to join");
            return SkillResult.CONDITION_FAILED;
        }

        return relink(entity, ownerId) ? SkillResult.SUCCESS : SkillResult.CONDITION_FAILED;
    }

    /**
     * Relink the owner's pet entities that loaded while they were offline (see waitingForOwner).
     * Runs on join, after PlacedPets.restore, so entities it already linked are skipped
     */
    public static void relinkWaiting(final Player player) {
        final Set<UUID> waiting = waitingForOwner.remove(player.getUniqueId());
        if (waiting == null) return;
        for (final UUID entityId : waiting) {
            final Entity entity = Bukkit.getEntity(entityId);
            // Unloaded since: its reload skill runs again when its chunk loads
            if (entity == null || !entity.isValid()) continue;
            if (Pet.getFromEntity(entity) != null) continue;
            if (relink(entity, player.getUniqueId()))
                Debugger.send("§7Relinked pet entity " + entityId + " of " + player.getName() + " on join");
        }
    }

    private static boolean relink(final Entity entity, final UUID ownerId) {
        NamespacedKey key = new NamespacedKey("mcpets", "pet_id");
        String entityPetID = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (entityPetID == null) {
            MCPets.getLog().warning("Pet entity " + entity.getUniqueId() + " has no pet id, it can't be relinked");
            return false;
        }

        Set<String> tags = entity.getScoreboardTags();
        PetAIMode aiMode = PetAIMode.FOLLOW;
        if (tags.contains("sit")) aiMode = PetAIMode.SIT;
        else if (tags.contains("wander")) aiMode = PetAIMode.WANDER;

        Optional<ActiveMob> activeMob = MCPets.getMythicMobs().getMobManager().getActiveMob(entity.getUniqueId());
        if (activeMob.isEmpty()) return false;

        // The pet MCPets still has in memory (e.g. after a MythicMobs reload or a chunk reload)
        Pet ownerPet = Pet.getActivePetsForOwner(ownerId).stream()
                .filter(activePet -> activePet.getId().equals(entityPetID))
                .findFirst()
                .orElse(null);

        // After a full restart MCPets has forgotten sitting and wandering pets that stayed in the world,
        // so they're rebuilt from their pet id. Following pets are respawned on join instead.
        if (ownerPet == null) {
            if (aiMode == PetAIMode.FOLLOW) return false;
            ownerPet = Pet.getFromId(entityPetID);
            if (ownerPet == null) return false;
        }

        final Pet relinkedPet = ownerPet;
        relinkedPet.relink(activeMob.get(), ownerId, aiMode);
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshNameTag(relinkedPet);
            }
        }.runTaskLater(MCPets.getInstance(), 10L);

        return true;
    }

    /**
     * Re-apply the pet's name tag, hidden when the pet has no custom name
     * ("None" is MCPets' placeholder for "no name", the same check setDisplayName does)
     */
    private static void refreshNameTag(final Pet pet) {
        final String name = pet.getCurrentName();
        pet.setNameTag(name, name != null && !name.equalsIgnoreCase(Language.TAG_TO_REMOVE_NAME.getMessage()));
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
