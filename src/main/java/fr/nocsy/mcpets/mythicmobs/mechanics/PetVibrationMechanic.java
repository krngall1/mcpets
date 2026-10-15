package fr.nocsy.mcpets.mythicmobs.mechanics;

import fr.nocsy.mcpets.MCPets;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Vibration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends sculk vibration particles from the entity that triggered the skill to points on the target,
 * e.g. from the clicking player to a warden pet's tendrils: petVibration{y=1.3;side=0.5;ticks=15} @self
 * MythicMobs' own particle mechanic can't give a vibration a destination. The particle flies to an empty
 * block display placed at each point (no size, so it lands exactly there), removed once the particle arrives.
 * The particle leaves a tick after the skill runs.
 */
@MythicMechanic(
        name = "petVibration"
)
public class PetVibrationMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final int arrivalTicks;
    private final double yOffset;
    private final double sideOffset;
    private final double forwardOffset;
    private final double fromYOffset;

    public PetVibrationMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getContainer().getFile());

        MythicLineConfig config = event.getConfig();

        arrivalTicks = Math.max(1, config.getInteger(new String[]{"ticks", "t"}, 15));
        yOffset = config.getDouble(new String[]{"yoffset", "y"}, 1.0);
        // One particle to each side at this distance, or a single one in the middle when 0
        sideOffset = config.getDouble(new String[]{"sideoffset", "side", "s"}, 0);
        forwardOffset = config.getDouble(new String[]{"forwardoffset", "forward", "f"}, 0);
        fromYOffset = config.getDouble(new String[]{"fromyoffset", "fromy", "fy"}, 1.0);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (data.getTrigger() == null) {
            return SkillResult.CONDITION_FAILED;
        }

        Entity source = BukkitAdapter.adapt(data.getTrigger());
        Entity destination = BukkitAdapter.adapt(target);
        if (source == null || destination == null || !source.getWorld().equals(destination.getWorld())) {
            return SkillResult.CONDITION_FAILED;
        }

        Location from = source.getLocation().add(0, fromYOffset, 0);
        Location base = destination.getLocation();
        float yaw = base.getYaw();
        Vector forward = new Vector(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location centre = base.clone().add(0, yOffset, 0).add(forward.clone().multiply(forwardOffset));
        centre.setYaw(0);
        centre.setPitch(0);

        List<Location> points = new ArrayList<>();
        if (sideOffset == 0) {
            points.add(centre);
        } else {
            points.add(centre.clone().add(right.clone().multiply(sideOffset)));
            points.add(centre.clone().add(right.clone().multiply(-sideOffset)));
        }

        List<Entity> markers = new ArrayList<>();
        for (Location point : points) {
            markers.add(point.getWorld().spawn(point, BlockDisplay.class, display -> {
                display.setPersistent(false);
                display.setSilent(true);
            }));
        }

        // The particle is sent a tick later, once players have the markers: a vibration whose destination
        // entity the client doesn't know yet disappears straight away
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Entity marker : markers) {
                    if (!marker.isValid()) continue;
                    from.getWorld().spawnParticle(Particle.VIBRATION, from, 1,
                            new Vibration(from, new Vibration.Destination.EntityDestination(marker), arrivalTicks));
                }
            }
        }.runTaskLater(MCPets.getInstance(), 1L);

        new BukkitRunnable() {
            @Override
            public void run() {
                markers.forEach(Entity::remove);
            }
        }.runTaskLater(MCPets.getInstance(), arrivalTicks + 4L);

        return SkillResult.SUCCESS;
    }

}
