package fr.nocsy.mcpets.mythicmobs.conditions;

import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.data.PetAIMode;

import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.core.utils.annotations.MythicField;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;
import io.lumine.mythic.core.utils.annotations.MythicCondition;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;

@MythicCondition(
        author = "Kieran",
        name = "petModeLimitReached",
        description = "True if the pet can't switch to the given mode because the owner is at MaxFollowingPets or MaxNonFollowingPets"
)
public class PetModeLimitCondition extends SkillCondition implements IEntityCondition {

    @MythicField(
            name = "mode",
            aliases = {"m"},
            description = "The mode the pet would switch to (FOLLOW, SIT or WANDER)"
    )
    private final PetAIMode mode;

    public PetModeLimitCondition(MythicConditionLoadEvent event) {
        super(event.getConfig().getLine());

        MythicLineConfig mlc = event.getConfig();

        mode = PetAIMode.valueOf(mlc.getString(new String[]{"mode", "m"}, "follow").toUpperCase());
    }

    @Override
    public boolean check(AbstractEntity target) {
        Pet pet = Pet.getFromEntity(target.getBukkitEntity());

        return pet != null && pet.isModeLimitReached(mode);
    }

}
