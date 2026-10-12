package fr.nocsy.mcpets.mythicmobs.mechanics;

import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.data.PetAIMode;
import fr.nocsy.mcpets.data.config.FormatArg;
import fr.nocsy.mcpets.data.PetLimits;
import fr.nocsy.mcpets.data.config.Language;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;

/**
 * Tells the pet's owner how many pets they can have in the given mode (language.yml), e.g. when the whistle hits a limit
 */
@MythicMechanic(
        name = "petLimitMessage"
)
public class PetLimitMessageMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final PetAIMode mode;

    public PetLimitMessageMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getContainer().getFile());

        MythicLineConfig config = event.getConfig();

        mode = PetAIMode.valueOf(config.getString(new String[]{"mode", "m"}, "follow").toUpperCase());
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        Pet pet = Pet.getFromEntity(BukkitAdapter.adapt(target));
        if (pet == null || pet.getOwner() == null) {
            return SkillResult.CONDITION_FAILED;
        }

        Player owner = Bukkit.getPlayer(pet.getOwner());
        if (owner == null) {
            return SkillResult.CONDITION_FAILED;
        }

        if (mode == PetAIMode.FOLLOW) {
            Language.MAX_FOLLOWING_PETS_REACHED.sendMessageFormatted(owner,
                    new FormatArg("%max%", String.valueOf(PetLimits.getMaxFollowingPets(owner.getUniqueId()))));
        } else {
            Language.MAX_NON_FOLLOWING_PETS_REACHED.sendMessageFormatted(owner,
                    new FormatArg("%max%", String.valueOf(PetLimits.getMaxNonFollowingPets(owner.getUniqueId()))));
        }
        return SkillResult.SUCCESS;
    }

}
