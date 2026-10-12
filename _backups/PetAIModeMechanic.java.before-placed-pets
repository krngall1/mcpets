package fr.nocsy.mcpets.mythicmobs.mechanics;

import fr.nocsy.mcpets.data.PetAIMode;
import fr.nocsy.mcpets.data.config.GlobalConfig;
import org.bukkit.entity.Entity;

import fr.nocsy.mcpets.data.Pet;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;

@MythicMechanic(
        name = "petAIMode"
)
public class PetAIModeMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final PetAIMode mode;
    // Wander radius, falls back on the global WanderRange when not given
    private final double range;

    public PetAIModeMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getContainer().getFile());

        MythicLineConfig config = event.getConfig();

        mode = PetAIMode.valueOf(config.getString(new String[]{"mode"}, "follow").toUpperCase());
        range = config.getDouble(new String[]{"range", "r"}, -1);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        Entity entity = BukkitAdapter.adapt(target);

        Pet pet = Pet.getFromEntity(entity);
        if (pet == null) {
            return SkillResult.CONDITION_FAILED;
        }

        // Re-applying wander to a pet that's already wandering (e.g. a refresh skill) keeps its home
        final boolean keepHome = mode == PetAIMode.WANDER && pet.getAiMode() == PetAIMode.WANDER && pet.getWanderCenter() != null;
        pet.setAiMode(mode);

        if (mode == PetAIMode.WANDER && !keepHome) {
            pet.setWanderHome(entity.getLocation(), range > 0 ? range : GlobalConfig.getInstance().getWanderRange());
        }

        return SkillResult.SUCCESS;
    }

}
