package fr.nocsy.mcpets.modeler.bone;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.data.Pet;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a pet's name only to the players looking at it, like vanilla named mobs.
 * Replaces BetterModel's own nametag, whose look check is measured from the player's feet
 * instead of their eyes and so triggers about a block above the pet.
 *
 * Each player gets one ray trace per check, against all loaded pets at once, so a player sees
 * at most one name (the pet under their crosshair, as in vanilla). A pet's text display only
 * exists while someone is looking at it.
 */
public final class LookAtNameDisplay {

    private static final double LOOK_RANGE = 5;
    private static final double RAY_SIZE = 0.2;
    private static final double SEARCH_RANGE_SQUARED = 8 * 8;
    private static final double MOVED_SQUARED = 0.01 * 0.01;
    private static final long PERIOD_TICKS = 2;

    private static final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private static BukkitTask task;

    private LookAtNameDisplay() {
    }

    private static final class Entry {
        private Component text;
        private TextDisplay display;
        private Location displayLoc;
        private final Set<UUID> shownTo = new HashSet<>();
    }

    /**
     * Show the given name above the pet to players looking at it.
     */
    public static void set(UUID petUUID, Component text) {
        Entry entry = entries.computeIfAbsent(petUUID, k -> new Entry());
        entry.text = text;
        if (entry.display != null && entry.display.isValid())
            entry.display.text(text);
        startTask();
    }

    /**
     * Stop showing a name for this pet.
     */
    public static void remove(UUID petUUID) {
        Entry entry = entries.remove(petUUID);
        if (entry != null)
            removeDisplay(entry);
    }

    public static void removeAll() {
        for (Entry entry : entries.values())
            removeDisplay(entry);
        entries.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static void startTask() {
        if (task != null)
            return;
        task = Bukkit.getScheduler().runTaskTimer(MCPets.getInstance(), LookAtNameDisplay::tick, 1L, PERIOD_TICKS);
    }

    private static void tick() {
        // The pets loaded right now
        Map<UUID, Entity> loadedPets = new HashMap<>();
        Iterator<Map.Entry<UUID, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry> e = it.next();
            UUID petUUID = e.getKey();

            Entity pet = Bukkit.getEntity(petUUID);
            if (pet == null || !pet.isValid()) {
                removeDisplay(e.getValue());
                // Keep the name while the pet is only unloaded, so it comes back with its chunk
                if (!isActivePet(petUUID))
                    it.remove();
                continue;
            }

            hideBetterModelTag(petUUID);
            loadedPets.put(petUUID, pet);
        }

        // Which players look at which pet: one ray trace per player near a pet
        Map<UUID, Set<UUID>> viewers = new HashMap<>();
        if (!loadedPets.isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!hasPetNearby(player, loadedPets.values()))
                    continue;
                Entity target = lookedAtPet(player, loadedPets.keySet());
                if (target != null)
                    viewers.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>()).add(player.getUniqueId());
            }
        }

        for (Map.Entry<UUID, Entity> e : loadedPets.entrySet()) {
            Entry entry = entries.get(e.getKey());
            if (entry != null)
                updateDisplay(entry, e.getValue(), viewers.getOrDefault(e.getKey(), Collections.emptySet()));
        }

        if (entries.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private static boolean hasPetNearby(Player player, Iterable<Entity> pets) {
        Location loc = player.getLocation();
        for (Entity pet : pets) {
            if (pet.getWorld() == loc.getWorld() && pet.getLocation().distanceSquared(loc) <= SEARCH_RANGE_SQUARED)
                return true;
        }
        return false;
    }

    /**
     * Same test as vanilla: the pet under the player's crosshair, within range, with no block in the way.
     */
    private static Entity lookedAtPet(Player player, Set<UUID> petUUIDs) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTrace(eye, eye.getDirection(), LOOK_RANGE,
                FluidCollisionMode.NEVER, true, RAY_SIZE, ent -> petUUIDs.contains(ent.getUniqueId()));
        return result == null ? null : result.getHitEntity();
    }

    /**
     * Show the name to the players looking at the pet, hide it from the others.
     * Nobody looking removes the display, so idle pets cost nothing here.
     */
    private static void updateDisplay(Entry entry, Entity pet, Set<UUID> lookingNow) {
        if (lookingNow.isEmpty()) {
            removeDisplay(entry);
            return;
        }

        Location loc = nameLocation(pet);
        if (entry.display == null || !entry.display.isValid() || entry.display.getWorld() != loc.getWorld()) {
            removeDisplay(entry);
            entry.display = spawnDisplay(loc, entry.text);
            entry.displayLoc = loc;
        } else if (entry.displayLoc == null || entry.displayLoc.distanceSquared(loc) > MOVED_SQUARED) {
            // Only moved when the pet (or its name bone) moved
            entry.display.teleport(loc);
            entry.displayLoc = loc;
        }

        for (UUID uuid : lookingNow) {
            if (entry.shownTo.add(uuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.showEntity(MCPets.getInstance(), entry.display);
            }
        }
        entry.shownTo.removeIf(uuid -> {
            if (lookingNow.contains(uuid)) return false;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.hideEntity(MCPets.getInstance(), entry.display);
            return true;
        });
    }

    /**
     * Where BetterModel would draw the tag: the model's name bone, or just above the pet's hitbox.
     */
    private static Location nameLocation(Entity pet) {
        Location base = pet.getLocation();
        EntityTrackerRegistry registry = BetterModel.registryOrNull(pet.getUniqueId());
        if (registry != null) {
            for (EntityTracker tracker : registry.trackers()) {
                RenderedBone bone = tracker.bone("name");
                if (bone != null) {
                    Vector3f pos = bone.worldPosition();
                    return new Location(base.getWorld(), base.getX() + pos.x, base.getY() + pos.y, base.getZ() + pos.z);
                }
            }
        }
        return base.add(0, pet.getHeight() + 0.3, 0);
    }

    private static void hideBetterModelTag(UUID petUUID) {
        EntityTrackerRegistry registry = BetterModel.registryOrNull(petUUID);
        if (registry == null)
            return;
        for (EntityTracker tracker : registry.trackers()) {
            RenderedBone bone = tracker.bone("name");
            if (bone != null && bone.getNametag() != null)
                bone.getNametag().component(null);
        }
    }

    private static TextDisplay spawnDisplay(Location loc, Component text) {
        return loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setTeleportDuration((int) PERIOD_TICKS);
            d.text(text);
        });
    }

    private static void removeDisplay(Entry entry) {
        if (entry.display != null && entry.display.isValid())
            entry.display.remove();
        entry.display = null;
        entry.displayLoc = null;
        entry.shownTo.clear();
    }

    private static boolean isActivePet(UUID petUUID) {
        for (List<Pet> pets : Pet.getActivePets().values()) {
            for (Pet pet : pets) {
                if (pet.getActiveMob() != null && pet.getActiveMob().getEntity() != null
                        && petUUID.equals(pet.getActiveMob().getEntity().getUniqueId()))
                    return true;
            }
        }
        return false;
    }
}
