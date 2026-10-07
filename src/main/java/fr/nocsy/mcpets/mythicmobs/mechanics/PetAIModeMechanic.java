package fr.nocsy.mcpets.mythicmobs.mechanics;

import fr.nocsy.mcpets.data.PetAIMode;
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

    public PetAIModeMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getContainer().getFile());

        MythicLineConfig config = event.getConfig();

        mode = PetAIMode.valueOf(config.getString(new String[]{"mode"}, "follow"));
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        Entity entity = BukkitAdapter.adapt(target);

        Pet pet = Pet.getFromEntity(entity);
        if (pet == null) {
            return SkillResult.CONDITION_FAILED;
        }

        pet.setAiMode(mode);

        if (mode == PetAIMode.WANDER) {
            pet.setWanderCenter(entity.getLocation());
            pet.setWanderRange(16);
        }

        return SkillResult.SUCCESS;
    }

}
