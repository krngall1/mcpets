package fr.nocsy.mcpets.data;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.utils.debug.Debugger;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Sitting and wandering pets stay in the world when the server stops, but MCPets forgets them until their chunk loads.
 * Their mode, spot and entity are kept in placed-pets.yml, so they're active again as soon as their owner joins:
 * they show in the menu, count towards the limits and can be summoned or revoked even while their chunk is unloaded.
 * This file is per server on purpose, the entities only exist on this server.
 */
public class PlacedPets {

    private static YamlConfiguration config;

    private static File getFile() {
        return new File(MCPets.getInstance().getDataFolder(), "placed-pets.yml");
    }

    private static YamlConfiguration get() {
        if (config == null)
            config = YamlConfiguration.loadConfiguration(getFile());
        return config;
    }

    /**
     * Save the sitting and wandering pets of this owner
     */
    public static void save(final UUID owner) {
        if (owner == null) return;
        update(owner);
        write();
    }

    /**
     * Save the sitting and wandering pets of every owner, e.g. when the server stops
     */
    public static void saveAll() {
        for (final UUID owner : new ArrayList<>(Pet.getActivePets().keySet()))
            update(owner);
        write();
    }

    private static void update(final UUID owner) {
        final YamlConfiguration cfg = get();
        final Set<String> placed = new HashSet<>();

        for (final Pet pet : new ArrayList<>(Pet.getActivePetsForOwner(owner))) {
            if (pet.getAiMode() != PetAIMode.SIT && pet.getAiMode() != PetAIMode.WANDER)
                continue;
            placed.add(pet.getId());

            final String path = owner + "." + pet.getId();
            final UUID entityId = pet.getEntityId();
            final Location loc = pet.getLocation();
            // Missing data (shouldn't happen) keeps what was saved before
            if (entityId == null || loc == null || loc.getWorld() == null)
                continue;

            cfg.set(path, null);
            cfg.set(path + ".mode", pet.getAiMode().name());
            cfg.set(path + ".entity", entityId.toString());
            setLocation(cfg, path + ".location", loc);
            if (pet.getWanderCenter() != null && pet.getWanderCenter().getWorld() != null) {
                setLocation(cfg, path + ".center", pet.getWanderCenter());
                cfg.set(path + ".range", pet.getWanderRange());
            }
            final PetSkin skin = pet.getActiveSkin();
            if (skin != null)
                cfg.set(path + ".skin", skin.getPathId());
        }

        // Pets that follow again or were revoked are forgotten
        final ConfigurationSection section = cfg.getConfigurationSection(owner.toString());
        if (section != null) {
            for (final String petId : section.getKeys(false))
                if (!placed.contains(petId))
                    cfg.set(owner + "." + petId, null);
            if (section.getKeys(false).isEmpty())
                cfg.set(owner.toString(), null);
        }
    }

    /**
     * Make the owner's sitting and wandering pets active again, e.g. when they join after a restart.
     * A pet whose entity is loaded is linked to it right away, else it waits for its chunk (PetListener.petLoaded)
     */
    public static void restore(final Player player) {
        final UUID owner = player.getUniqueId();
        final ConfigurationSection section = get().getConfigurationSection(owner.toString());
        if (section == null) return;

        final List<Pet> restored = new ArrayList<>();
        for (final String petId : section.getKeys(false)) {
            // Still active, e.g. the owner rejoined without a restart
            if (Pet.getActivePetsForOwner(owner).stream().anyMatch(p -> p.getId().equals(petId)))
                continue;

            final Pet template = Pet.getFromId(petId);
            final ConfigurationSection data = section.getConfigurationSection(petId);
            if (template == null || data == null) continue;

            final UUID entityId;
            final PetAIMode mode;
            try {
                entityId = UUID.fromString(data.getString("entity", ""));
                mode = PetAIMode.valueOf(data.getString("mode", "SIT"));
            } catch (final IllegalArgumentException ex) {
                continue;
            }
            if (mode == PetAIMode.FOLLOW) continue;
            // Revoked while it couldn't be removed, it's gone for good
            if (RevokedPets.isRevoked(entityId)) continue;

            final Pet pet = template.copy();
            pet.setOwner(owner);
            final String skinId = data.getString("skin");
            if (skinId != null) {
                for (final PetSkin skin : PetSkin.getSkins(pet))
                    if (skinId.equals(skin.getPathId()))
                        pet.setActiveSkin(skin);
            }

            pet.restorePlaced(mode, entityId, getLocation(data, "location"),
                    getLocation(data, "center"), data.getDouble("range", pet.getWanderRange()));
            Pet.addActivePet(owner, pet);
            restored.add(pet);
        }

        // Linked once they're all active again, as relinking saves this file
        for (final Pet pet : restored) {
            final UUID entityId = pet.getEntityId();
            final Entity loaded = Bukkit.getEntity(entityId);
            final Optional<ActiveMob> mob = loaded != null && loaded.isValid()
                    ? MCPets.getMythicMobs().getMobManager().getActiveMob(entityId)
                    : Optional.empty();
            if (mob.isPresent()) {
                Debugger.send("§7Relinking placed pet " + pet.getId() + " of " + player.getName());
                pet.relink(mob.get(), owner, pet.getAiMode());
            } else {
                Debugger.send("§7Restored placed pet " + pet.getId() + " of " + player.getName() + ", waiting for its chunk to load");
            }
        }
    }

    private static void setLocation(final YamlConfiguration cfg, final String path, final Location loc) {
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
    }

    private static Location getLocation(final ConfigurationSection data, final String path) {
        final World world = Bukkit.getWorld(data.getString(path + ".world", ""));
        if (world == null) return null;
        return new Location(world, data.getDouble(path + ".x"), data.getDouble(path + ".y"), data.getDouble(path + ".z"));
    }

    private static void write() {
        try {
            get().save(getFile());
        } catch (final IOException ex) {
            MCPets.getLog().log(Level.WARNING, "Could not save placed-pets.yml", ex);
        }
    }
}
