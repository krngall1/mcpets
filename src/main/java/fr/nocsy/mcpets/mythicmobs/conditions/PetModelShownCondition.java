package fr.nocsy.mcpets.mythicmobs.conditions;

import fr.nocsy.mcpets.MCPets;

import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;
import io.lumine.mythic.core.utils.annotations.MythicCondition;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;

@MythicCondition(
        author = "Kieran",
        name = "petModelShown",
        description = "True once the pet's model is being shown to a player. BetterModel plays its idle and walk animations "
                + "the first time a player sees a model, on top of any state played before, so one-shot poses like sit wait for this"
)
public class PetModelShownCondition extends SkillCondition implements IEntityCondition {

    public PetModelShownCondition(MythicConditionLoadEvent event) {
        super(event.getConfig().getLine());
    }

    @Override
    public boolean check(AbstractEntity target) {
        return target.getBukkitEntity() != null
                && MCPets.getModeler().isModelShown(target.getBukkitEntity().getUniqueId());
    }

}
