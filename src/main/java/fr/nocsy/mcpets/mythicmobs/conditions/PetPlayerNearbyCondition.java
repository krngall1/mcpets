package fr.nocsy.mcpets.mythicmobs.conditions;

import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.core.utils.annotations.MythicField;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;
import io.lumine.mythic.core.utils.annotations.MythicCondition;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@MythicCondition(
        author = "Kieran",
        name = "petPlayerNearby",
        description = "True if any player, in any game mode, is within the radius. MythicMobs' playerwithin skips creative players"
)
public class PetPlayerNearbyCondition extends SkillCondition implements IEntityCondition {

    @MythicField(
            name = "radius",
            aliases = {"r", "distance", "d"},
            description = "How close a player has to be, in blocks"
    )
    private final double radius;

    public PetPlayerNearbyCondition(MythicConditionLoadEvent event) {
        super(event.getConfig().getLine());

        MythicLineConfig mlc = event.getConfig();

        radius = mlc.getDouble(new String[]{"radius", "r", "distance", "d"}, 32);
    }

    @Override
    public boolean check(AbstractEntity target) {
        final Entity entity = target.getBukkitEntity();
        if (entity == null) return false;
        final Location loc = entity.getLocation();
        final double radiusSquared = radius * radius;
        for (final Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= radiusSquared)
                return true;
        }
        return false;
    }

}
