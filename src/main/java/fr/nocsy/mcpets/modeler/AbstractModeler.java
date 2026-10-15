package fr.nocsy.mcpets.modeler;

import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.modeler.bone.AbstractNameTag;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Abstraction layer over ModelEngine / BetterModel APIs.
 * Allows the plugin to work with either modeler transparently.
 */
public interface AbstractModeler {

    /**
     * Mount an entity as driver on the pet's model.
     * @return true if mounting succeeded
     */
    boolean mountDriver(UUID petUUID, Entity rider, String mountType);

    /**
     * Check if the given entity is currently mounted as driver on the pet.
     */
    boolean hasMount(UUID petUUID, Entity rider);

    /**
     * Dismount a specific rider from the pet.
     */
    void dismountRider(UUID petUUID, Entity rider);

    /**
     * Dismount all riders from the pet.
     */
    void dismountAll(UUID petUUID);

    /**
     * Remove the modeled entity from the modeler's registry (cleanup on despawn).
     */
    void removeModel(UUID petUUID);

    /**
     * Get the nametag bone for the pet, or null if none exists.
     */
    AbstractNameTag getNameTag(UUID petUUID);

    /**
     * Check if the given mount type string is supported/registered.
     */
    boolean supportsMount(String mountType);

    /**
     * Handle vanilla dismount event: properly dismount the entity from the model.
     */
    void handleVanillaDismount(UUID petUUID, Entity rider);

    /**
     * Check if the pet is currently using a flying mount controller.
     */
    boolean isFlyingMount(Pet pet, UUID owner);

    /**
     * Has the pet's model been shown to a player yet? BetterModel starts a model's idle and walk animations
     * the first time a player sees it, on top of any state played before, so one-shot states wait for this
     */
    default boolean isModelShown(UUID petUUID) {
        return true;
    }

    /**
     * Register modeler-specific event listeners (ModelMountEvent, ModelDismountEvent, etc.)
     */
    void registerListeners(JavaPlugin plugin);

    /**
     * Unregister modeler-specific event listeners (cleanup on disable).
     */
    void unregisterListeners();
}
