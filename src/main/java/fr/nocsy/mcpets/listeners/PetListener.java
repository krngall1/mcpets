package fr.nocsy.mcpets.listeners;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import fr.nocsy.mcpets.data.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.PPermission;
import fr.nocsy.mcpets.utils.Utils;
import fr.nocsy.mcpets.data.sql.Databases;
import fr.nocsy.mcpets.data.sql.PlayerData;
import fr.nocsy.mcpets.utils.debug.Debugger;
import fr.nocsy.mcpets.events.PetSpawnEvent;
import fr.nocsy.mcpets.data.config.Language;
import fr.nocsy.mcpets.data.flags.FlagsManager;
import fr.nocsy.mcpets.data.livingpets.PetFood;
import fr.nocsy.mcpets.data.config.GlobalConfig;
import fr.nocsy.mcpets.events.EntityMountPetEvent;
import fr.nocsy.mcpets.data.flags.DismountPetFlag;
import fr.nocsy.mcpets.data.inventories.PetInventory;
import fr.nocsy.mcpets.velocity.VelocitySyncManager;
import fr.nocsy.mcpets.events.PetOwnerInteractEvent;
import fr.nocsy.mcpets.data.inventories.PetInteractionMenu;
import fr.nocsy.mcpets.data.inventories.MountInteractionMenu;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobDespawnEvent;

public class PetListener implements Listener {

    private final Map<UUID, String> reconnectionPets = new HashMap<>();

    @EventHandler
    public void interact(PlayerInteractEntityEvent e) {
        if (!GlobalConfig.getInstance().isRightClickToOpen()) return;

        Player p = e.getPlayer();

        if (GlobalConfig.getInstance().isSneakMode() && !p.isSneaking()) return;

        // Do not open the menu if the player has a signal stick
        if (GlobalConfig.getInstance().isDisableInventoryWhileHoldingSignalStick()) {
            ItemStack it = p.getInventory().getItemInMainHand();
            if (Items.isSignalStick(it)) return;
        }

        //If it's pet food in the main hand then do not open the menu
        if (PetFood.getFromItem(p.getInventory().getItemInMainHand()) != null) {
            return;
        }

        Entity ent = e.getRightClicked();

        Pet pet = Pet.getFromEntity(ent);

        if (pet != null && pet.getOwner() != null &&
                pet.getOwner().equals(p.getUniqueId())) {
            PetOwnerInteractEvent event = new PetOwnerInteractEvent(pet);
            Utils.callEvent(event);
            if (event.isCancelled()) return;

            // Check if this is a mount and open the appropriate menu
            if (pet.isMount()) {
                MountInteractionMenu menu = new MountInteractionMenu(pet, p.getUniqueId());
                pet.setLastInteractedWith(p);
                menu.open(p);
            } else {
                PetInteractionMenu menu = new PetInteractionMenu(pet, p.getUniqueId());
                pet.setLastInteractedWith(p);
                menu.open(p);
            }
        }
        if (pet != null && p.isOp()) {
            // Check if this is a mount and open the appropriate menu
            if (pet.isMount()) {
                MountInteractionMenu menu = new MountInteractionMenu(pet, pet.getOwner());
                pet.setLastOpInteracted(p);
                menu.open(p);
            } else {
                PetInteractionMenu menu = new PetInteractionMenu(pet, pet.getOwner());
                pet.setLastOpInteracted(p);
                menu.open(p);
            }
        }
    }

    @EventHandler
    public void interact(EntityDamageByEntityEvent e) {
        if (!GlobalConfig.getInstance().isLeftClickToOpen()) return;

        if (!(e.getDamager() instanceof Player p)) return;

        if (GlobalConfig.getInstance().isSneakMode() && !p.isSneaking()) return;

        Entity ent = e.getEntity();

        Pet pet = Pet.getFromEntity(ent);

        if (pet != null && pet.getOwner() != null && pet.getOwner().equals(p.getUniqueId())) {
            if (pet.isMount()) {
                MountInteractionMenu menu = new MountInteractionMenu(pet, p.getUniqueId());
                pet.setLastInteractedWith(p);
                menu.open(p);
            } else {
                PetInteractionMenu menu = new PetInteractionMenu(pet, p.getUniqueId());
                pet.setLastInteractedWith(p);
                menu.open(p);
            }
            e.setCancelled(true);
            e.setDamage(0);
        }
        if (pet != null && p.isOp()) {
            // Check if this is a mount and open the appropriate menu
            if (pet.isMount()) {
                MountInteractionMenu menu = new MountInteractionMenu(pet, pet.getOwner());
                pet.setLastOpInteracted(p);
                menu.open(p);
            } else {
                PetInteractionMenu menu = new PetInteractionMenu(pet, pet.getOwner());
                pet.setLastOpInteracted(p);
                menu.open(p);
            }
            e.setCancelled(true);
            e.setDamage(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void disconnectPlayer(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // Where the sitting and wandering pets are, so they're active again after a restart
        PlacedPets.save(uuid);

        List<String> activePetIds = new ArrayList<>();
        Map<String, String> activeSkinIds = new HashMap<>();
        PlayerData.get(uuid).setLastActivePets(new ArrayList<>());
        PlayerData.get(uuid).save();
        // Create a copy to avoid ConcurrentModificationException when despawning modifies the list
        List<Pet> pets = Pet.getActivePetsForOwner(uuid);
        for (Pet pet : new ArrayList<>(pets)) {
            // Capture skin data before despawn clears it
            PetSkin activeSkin = pet.getActiveSkin();

            if (p.hasPermission(pet.getPermission())) {
                String encoded = PlayerData.encodeActivePet(pet.getId(), pet.getAiMode(),
                        activeSkin != null ? activeSkin.getPathId() : null);
                reconnectionPets.putIfAbsent(uuid, encoded);
                activePetIds.add(pet.getId());
                if (activeSkin != null) {
                    activeSkinIds.put(pet.getId(), activeSkin.getPathId());
                }
                if (GlobalConfig.getInstance().isSpawnPetAfterServerRestart()) {
                    PlayerData pd = PlayerData.get(uuid);
                    if (pd != null) {
                        pd.addLastActivePet(encoded);
                        pd.save();
                    }
                }
            }
            if (pet.getAiMode() == PetAIMode.FOLLOW) {
                pet.despawn(PetDespawnReason.DISCONNECTION);
            }
        }
        // Velocity: persist or clear active pet record so destination server restores correctly
        if (GlobalConfig.getInstance().isVelocityEnabled()
                && GlobalConfig.getInstance().isDatabaseSupport()) {
            if (!activePetIds.isEmpty()) {
                final List<String> petIdsToSave = new ArrayList<>(activePetIds);
                final Map<String, String> skinIdsToSave = new HashMap<>(activeSkinIds);
                Bukkit.getScheduler().runTaskAsynchronously(MCPets.getInstance(),
                        () -> Databases.saveActivePet(uuid, petIdsToSave, skinIdsToSave));
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(MCPets.getInstance(),
                        () -> Databases.clearActivePet(uuid));
            }
        }
        if (pets.isEmpty() && GlobalConfig.getInstance().isSpawnPetAfterServerRestart()) {
            PlayerData pd = PlayerData.get(uuid);
            if (pd != null) {
                pd.setLastActivePets(new ArrayList<>());
                pd.save();
            }
        }
        // Clean up player caches from memory to prevent memory leak
        PlayerData.remove(p.getUniqueId());
        PetInventory.removePlayer(p.getUniqueId());
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void reconnectionPlayer(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        Bukkit.getScheduler().runTaskLater(MCPets.getInstance(), () -> {
            if (GlobalConfig.getInstance().isDatabaseSupport()) {
                PlayerData.reloadAll(uuid);
            }

            // Sitting and wandering pets first, so they count towards the limits of the pets spawned below
            if (p.isOnline())
                PlacedPets.restore(p);

            if (GlobalConfig.getInstance().isVelocityEnabled()
                    && GlobalConfig.getInstance().isDatabaseSupport()) {

                // When Velocity is enabled the DB is the ONLY source of truth.
                boolean isLiveSwitch = VelocitySyncManager.isPlayerSwitching(uuid);
                VelocitySyncManager.clearSwitchingPlayer(uuid);
                reconnectionPets.remove(uuid); // discard — DB owns the state

                // Load from DB asynchronously to avoid blocking the main thread
                Bukkit.getScheduler().runTaskAsynchronously(MCPets.getInstance(), () -> {
                    Databases.ActivePetRecord record = Databases.loadActivePet(uuid);
                    if (record == null) return;
                    if (isLiveSwitch) {
                        Databases.clearActivePet(uuid);
                    }
                    // Return to main thread to spawn pets (skin restoration uses static maps)
                    Bukkit.getScheduler().runTask(MCPets.getInstance(), () -> {
                        if (!p.isOnline()) return;
                        for (String petId : record.getPetIds()) {
                            Pet template = Pet.getFromId(petId);
                            if (template == null) continue;
                            Pet velocityPet = template.copy();
                            velocityPet.setCheckPermission(false);
                            velocityPet.setOwner(uuid);
                            // Restore active skin if one was saved
                            restoreSkin(p, velocityPet, record.getSkinId(petId));
                            velocityPet.spawn(p.getLocation(), true);
                        }
                    });
                });
                return; // never fall through to reconnectionPets when Velocity is enabled
            }

            // Velocity disabled: use the local reconnection map (original behavior).
            if (reconnectionPets.containsKey(uuid)) {
                String stored = reconnectionPets.get(uuid);
                Pet pet = Pet.getFromId(PlayerData.decodeActivePetId(stored));
                if (pet == null) return;

                if (!p.hasPermission(pet.getPermission())) {
                    reconnectionPets.remove(uuid);
                    return;
                }
                if (pet.getAiMode() == PetAIMode.FOLLOW) {
                    pet = pet.copy();
                    pet.setCheckPermission(false);
                    pet.setOwner(uuid);
                    restoreSkin(p, pet, PlayerData.decodeActiveSkinId(stored));
                    pet.spawn(p.getLocation(), true);
                }
                reconnectionPets.remove(uuid);
            }
            else if (GlobalConfig.getInstance().isSpawnPetAfterServerRestart()) {
                PlayerData pd = PlayerData.get(uuid);
                List<String> storedPets = List.copyOf(pd.getLastActivePets());
                for (String stored : new ArrayList<>(storedPets)) {
                    String lastPetId = PlayerData.decodeActivePetId(stored);
                    if (lastPetId != null && !lastPetId.isEmpty()) {
                        Pet pet = Pet.getFromId(lastPetId);
                        if (pet != null) {
                            PetAIMode aiMode = PetAIMode.valueOf(PlayerData.decodeActiveAI(stored));
                            pet = pet.copy();
                            pet.setCheckPermission(false);
                            pet.setOwner(uuid);
                            restoreSkin(p, pet, PlayerData.decodeActiveSkinId(stored));
                            if (aiMode == PetAIMode.FOLLOW) pet.spawn(p.getLocation(), true);
                        }
                    }
                }
            }
        }, 20L);
    }

    private void restoreSkin(Player p, Pet pet, String skinPathId) {
        if (skinPathId == null || skinPathId.isEmpty()) return;
        for (PetSkin skin : PetSkin.getSkins(pet)) {
            if (skinPathId.equals(skin.getPathId())) {
                String perm = skin.getPermission();
                if (perm == null || perm.isEmpty() || p.hasPermission(perm)) {
                    pet.setActiveSkin(skin);
                }
                return;
            }
        }
    }

    @EventHandler
    public void teleport(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        for (Pet pet : new ArrayList<>(Pet.getActivePetsForOwner(p.getUniqueId()))) {
            if (pet.getTamingProgress() < 1 || pet.getAiMode() != PetAIMode.FOLLOW) continue;
            pet.despawn(PetDespawnReason.TELEPORT);
            new BukkitRunnable() {
                @Override
                public void run() {
                    pet.spawn(p, p.getLocation());
                }
            }.runTaskLater(MCPets.getInstance(), 20L);
        }
    }

    @EventHandler
    public void teleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        for (Pet pet : Pet.getActivePetsForOwner(p.getUniqueId())) {
            pet.dismount(p);
        }
    }

    @EventHandler
    public void riding(EntityDamageEvent e) {
        if (!GlobalConfig.getInstance().isDismountOnDamaged()) return;

        if (e.getEntity() instanceof Player p) {
            if (e instanceof EntityDamageByEntityEvent damageEvent) {
                if (damageEvent.getDamager() instanceof Player) {
                    return;
                }
            }
            
            if (GlobalConfig.getInstance().isDismountOnDamagedExcludePlayers()) return;

            Pet pet = Pet.fromOwner(p.getUniqueId());
            if (pet != null && pet.hasMount(p)) {
                pet.dismount(p);
            }
        }
    }

    @EventHandler
    public void damaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        Pet pet = Pet.getFromEntity(e.getEntity());
        // Cosmetic pets shouldn't be damageable
        if (pet != null && pet.getPetStats() == null) {
            e.setDamage(0);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void gamemode(PlayerGameModeChangeEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (e.getNewGameMode() != GameMode.SPECTATOR) return;
        for (Pet pet : new ArrayList<>(Pet.getActivePetsForOwner(uuid))) {
            if (pet.getAiMode() == PetAIMode.FOLLOW) {
                pet.despawn(PetDespawnReason.GAMEMODE);
            }
        }
    }

    /**
     * MythicMobs has no trigger for a mob being loaded again with its chunk,
     * so pets that stayed in the world run their LoadSkill (pet config) to restore their behaviour and animations
     */
    private final Set<UUID> loadingPets = new HashSet<>();
    @EventHandler
    public void petLoaded(EntityAddToWorldEvent e) {
        final Entity entity = e.getEntity();
        // A pet MCPets is spawning right now gets its pet id after this event, so it's skipped here
        if (!entity.getPersistentDataContainer().has(PET_ID_KEY, PersistentDataType.STRING)) return;
        // The event can fire several times for one load
        final UUID uuid = entity.getUniqueId();
        if (!loadingPets.add(uuid)) return;

        new BukkitRunnable() {
            private int attempts = 0;

            @Override
            public void run() {
                final Optional<ActiveMob> found = entity.isValid()
                        ? MCPets.getMythicMobs().getMobManager().getActiveMob(uuid)
                        : Optional.empty();

                // For a few ticks MythicMobs can still hand back the mob from before the chunk unloaded,
                // whose entity is no longer valid, so we wait for the reloaded one
                if (found.isEmpty() || found.get().getEntity() == null || !found.get().getEntity().getBukkitEntity().isValid()) {
                    if (++attempts < 20 && entity.isValid()) return;
                    Debugger.send("§cPet entity " + uuid + " loaded but MythicMobs never gave back its reloaded mob");
                    loadingPets.remove(uuid);
                    cancel();
                    return;
                }

                final ActiveMob activeMob = found.get();
                // A pet restored after a restart (see PlacedPets) is waiting for this entity
                Pet linkedPet = Pet.getFromEntity(entity);
                if (linkedPet == null)
                    linkedPet = Pet.getPlacedPet(uuid);
                final Pet pet = linkedPet;

                // The pet was revoked (e.g. replaced by a new pet) while its chunk wasn't loaded, so it's removed now
                // instead of coming back and taking the new pet's place
                if (RevokedPets.consume(uuid) || (pet != null && pet.isRemoved())) {
                    Debugger.send("§7Removing pet entity " + uuid + ", it was revoked while its chunk wasn't loaded");
                    MCPets.getModeler().removeModel(uuid);
                    activeMob.remove();
                    entity.remove();
                    loadingPets.remove(uuid);
                    cancel();
                    return;
                }

                // The pet usually is still linked (metadata is kept by uuid) but drives the old mob,
                // so it's pointed at the reloaded one
                if (pet != null && pet.getOwner() != null && pet.getActiveMob() != activeMob) {
                    Debugger.send("§7Relinking pet " + pet.getId() + " to its reloaded entity " + uuid);
                    pet.relink(activeMob, pet.getOwner(), pet.getAiMode());
                }

                // Run the pet's LoadSkill, read from the pet config when the entity isn't linked (e.g. owner offline after a restart)
                final Pet skillSource = pet != null ? pet
                        : Pet.getFromId(entity.getPersistentDataContainer().get(PET_ID_KEY, PersistentDataType.STRING));
                if (skillSource != null && skillSource.castLoadSkill(activeMob))
                    Debugger.send("§7Ran the load skill of pet entity " + uuid);
                else
                    Debugger.send("§7Pet entity " + uuid + " loaded, it has no LoadSkill to run");
                // The add event can fire again shortly after for the same load, so the uuid is held a bit longer
                Bukkit.getScheduler().runTaskLater(MCPets.getInstance(), () -> loadingPets.remove(uuid), 40L);
                cancel();
            }
        }.runTaskTimer(MCPets.getInstance(), 1L, 5L);
    }

    /**
     * Handle random despawn
     */
    private final Map<UUID, Integer> repeatRespawn = new HashMap<>();
    private static final NamespacedKey PET_ID_KEY = new NamespacedKey("mcpets", "pet_id");
    @EventHandler
    public void despawn(MythicMobDespawnEvent e) {
        if (e.getEntity() == null) return;

        Pet pet = Pet.getFromEntity(e.getEntity());
        if (pet == null) return;
        if (pet.isRemoved()) return;

        pet.despawn(PetDespawnReason.MYTHICMOBS);

        UUID ownerUUID = pet.getOwner();
        if (ownerUUID == null) return;

        Player owner = Bukkit.getPlayer(pet.getOwner());
        if (owner == null) return;
        if (repeatRespawn.containsKey(ownerUUID) && repeatRespawn.get(ownerUUID) == 3) {
            Language.REVOKED_UNKNOWN.sendMessage(owner);
            repeatRespawn.remove(owner.getUniqueId());
            return;
        }
        int value = 1;
        if (repeatRespawn.containsKey(ownerUUID)) value = repeatRespawn.get(ownerUUID);
        pet.spawn(owner, owner.getLocation());
        pet.setRecurrent_spawn(false);
        repeatRespawn.put(owner.getUniqueId(), value + 1);
        new BukkitRunnable() {
            @Override
            public void run() {
                repeatRespawn.remove(owner.getUniqueId());
            }
        }.runTaskLater(MCPets.getInstance(), 10L);
    }

    /**
     * Handle death of the pet
     */
    @EventHandler
    public void death(MythicMobDeathEvent e) {
        if (e.getEntity() == null) return;

        Pet pet = Pet.getFromEntity(e.getEntity());
        if (pet == null) return;
        if (pet.isRemoved()) return;

        pet.despawn(PetDespawnReason.DEATH);

        if (pet.getOwner() == null) return;

        Player owner = Bukkit.getPlayer(pet.getOwner());
        if (owner != null) {
            Language.REVOKED.sendMessage(owner);
        }
    }

    /**
     * Blacklisted world system
     */
    @EventHandler
    public void blacklistedWorld(PetSpawnEvent e) {
        if (!GlobalConfig.getInstance().hasBlackListedWorld(e.getWhere().getWorld().getName())) return;

        e.setCancelled(true);
        Debugger.send("§cSpawn of §6" + e.getPet().getId() + "§c cancelled: world §6" + e.getWhere().getWorld().getName() + "§c is blacklisted.");

        Player p = Bukkit.getPlayer(e.getPet().getOwner());
        if (p != null) {
            Language.BLACKLISTED_WORLD.sendMessage(p);
        }
    }

    @EventHandler
    public void cancelDefaultTaming(EntityTameEvent e) {
        if (Pet.getFromEntity(e.getEntity()) == null) return;

        // Cancel the event, so it doesn't give other type of item by default to the anchor
        e.setCancelled(true);
    }

    @EventHandler
    public void mountingPet(EntityMountPetEvent e) {
        if (e.getEntity() == null) return;

        // if it's not the owner or an admin mounting the pet, then we cancel it
        if (!e.getPet().getOwner().equals(e.getEntity().getUniqueId()) && !e.getEntity().hasPermission(PPermission.ADMIN.getPermission())) {
            e.setCancelled(true);
            Debugger.send("§c" + e.getEntity().getName() + " can not mount " + e.getPet().getId() + " as he's not the owner, nor an admin.");
        }
        // If user doesn't have the perm to mount the pet, cancel the event
        if (e.getPet().getMountPermission() != null && !e.getEntity().hasPermission(e.getPet().getMountPermission())) {
            e.setCancelled(true);
            Language.CANT_MOUNT_PET_YET.sendMessage(e.getEntity());
        }
    }

    @EventHandler
    public void checkFlagMount(EntityMountPetEvent e) {
        Pet pet = e.getPet();
        Entity player = e.getEntity();
        if (player instanceof Player &&
                GlobalConfig.getInstance().isWorldGuardSupport() &&
                FlagsManager.getFlag(DismountPetFlag.NAME) != null &&
                FlagsManager.getFlag(DismountPetFlag.NAME).testState(player.getLocation())) {

            e.setCancelled(true);
            Debugger.send("[EntityMountPetEvent] §c" + player.getName() + " can not mount model of " + pet.getId() + " as a region is preventing mounting.");
            Language.NOT_MOUNTABLE_HERE.sendMessage(player);
            if (pet.isDespawnOnDismount()) pet.despawn(PetDespawnReason.FLAG);
        }
    }


    @EventHandler
    public void fastMount(PlayerInteractEntityEvent e){
        // Check if inventories should be opening instead of fast mounting
        if (GlobalConfig.getInstance().isRightClickToOpen()) return;

        Player p = e.getPlayer();
        if (GlobalConfig.getInstance().isSneakMode() && p.isSneaking()) return;

        // Do not mount if the player has a signal stick
        if (GlobalConfig.getInstance().isDisableFastMountWhileHoldingSignalStick()) {
            ItemStack it = p.getInventory().getItemInMainHand();
            if (Items.isSignalStick(it)) return;
        }
        //If it's pet food in the main hand then do not mount
        if (PetFood.getFromItem(p.getInventory().getItemInMainHand()) != null) {
            return;
        }

        if (!GlobalConfig.getInstance().isFastMount()) return;

        Entity ent = e.getRightClicked();
        Pet pet = Pet.getFromEntity(ent);
        if (pet != null && pet.getOwner() != null &&
                pet.getOwner().equals(p.getUniqueId())) {
            PetOwnerInteractEvent event = new PetOwnerInteractEvent(pet);
            Utils.callEvent(event);
            if (event.isCancelled()) return;

            PetInteractionMenuListener.mount(p, pet);

        }

    }

}
