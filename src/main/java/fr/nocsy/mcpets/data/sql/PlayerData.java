package fr.nocsy.mcpets.data.sql;

import fr.nocsy.mcpets.data.PetAIMode;
import fr.nocsy.mcpets.data.config.GlobalConfig;
import fr.nocsy.mcpets.data.inventories.PetInventory;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerData {

    @Getter
    private static final ConcurrentHashMap<UUID, PlayerData> registeredData = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private ConcurrentHashMap<String, String> mapOfRegisteredNames = new ConcurrentHashMap<>();
    @Getter
    @Setter
    private ConcurrentHashMap<String, String> mapOfRegisteredInventories = new ConcurrentHashMap<>();
    @Getter
    @Setter
    private ConcurrentHashMap<String, String> mapOfRegisteredPetStats = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private List<String> lastActivePets;

    @Setter
    @Getter
    private UUID uuid;

    private static final String ACTIVE_PET_SKIN_DELIMITER = ":";

    private PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    private PlayerData() {
    }

    public static boolean isRegistered(UUID player) {
        return registeredData.containsKey(player);
    }

    public static PlayerData get(UUID owner) {

        if (registeredData.containsKey(owner)) {
            return registeredData.get(owner);
        }
        else {
            if (!GlobalConfig.getInstance().isDatabaseSupport()) {
                PlayerData data = new PlayerData();
                data.setUuid(owner);
                PlayerDataNoDatabase pdn = PlayerDataNoDatabase.get(owner);
                data.setMapOfRegisteredNames(pdn.mapOfRegisteredNames);
                data.setMapOfRegisteredInventories(pdn.mapOfRegisteredInventories);
                data.setLastActivePets(pdn.getLastActivePets());
                registeredData.put(owner, data);

                return data;
            }

            PlayerData data = new PlayerData(owner);
            registeredData.put(owner, data);
            return data;
        }
    }

    public void addLastActivePet(String pet) {
        if (!lastActivePets.contains(pet)) lastActivePets.add(pet);
    }

    public static PlayerData getEmpty(UUID owner) {
        PlayerData data = new PlayerData();
        data.setUuid(owner);
        return data;
    }

    public static String encodeActivePet(String petId, PetAIMode petAI, String skinPathId) {
        if (petId == null)
            petId = "";
        petId += ACTIVE_PET_SKIN_DELIMITER + petAI.toString();
        return (skinPathId == null || skinPathId.isEmpty())
                ? petId
                : petId + ACTIVE_PET_SKIN_DELIMITER + skinPathId;
    }

    public static String decodeActivePetId(String value) {
        if (value == null)
            return null;
        int idx = value.indexOf(ACTIVE_PET_SKIN_DELIMITER);
        return idx < 0 ? value : value.substring(0, idx);
    }

    public static String decodeActiveSkinId(String value) {
        if (value == null)
            return null;
        int count = (value.length() - value.replace(ACTIVE_PET_SKIN_DELIMITER, "").length()) / ACTIVE_PET_SKIN_DELIMITER.length();
        return count <= 1 ? null : value.split(ACTIVE_PET_SKIN_DELIMITER)[2];
    }

    public static String decodeActiveAI(String value) {
        if (value == null)
            return null;
        return value.split(ACTIVE_PET_SKIN_DELIMITER)[1];
    }

    public static void saveDB() {
        if(GlobalConfig.getInstance().isDatabaseSupport())
            Databases.saveData();
        else {
            PlayerDataNoDatabase.getCacheMap().values().forEach(PlayerDataNoDatabase::save);
        }
    }

    public static void reloadAllPlayerData() {
        Databases.loadData();
    }

    /**
     * Register the Pet inventory for future save
     */
    public void setPetInventory(PetInventory petInventory) {
        mapOfRegisteredInventories.put(petInventory.getPetId(), petInventory.serialize());
    }

    public void save() {
        if (GlobalConfig.getInstance().isDatabaseSupport())
            Databases.savePlayerData(uuid);
        else {
            PlayerDataNoDatabase pdn = PlayerDataNoDatabase.get(uuid);
            pdn.setMapOfRegisteredNames(mapOfRegisteredNames);
            pdn.setLastActivePets(lastActivePets);

            mapOfRegisteredInventories.clear();
            HashMap<String, PetInventory> inventories = PetInventory.getPetInventories().get(this.getUuid());
            if (inventories != null) {
                for (String petId : inventories.keySet()) {
                    mapOfRegisteredInventories.put(petId, inventories.get(petId).serialize());
                }
            }

            pdn.setMapOfRegisteredInventories(mapOfRegisteredInventories);
            pdn.save();
        }
    }

    public static void initAll() {
        if (GlobalConfig.getInstance().isDatabaseSupport()) {
            Databases.loadData();
        }
        else {
            PlayerDataNoDatabase.getCacheMap().values().forEach(PlayerDataNoDatabase::reload);
        }
    }

    public static void remove(UUID uuid) {
        registeredData.remove(uuid);
        PlayerDataNoDatabase.getCacheMap().remove(uuid);
    }

    public static void reloadAll(UUID uuid) {
        if (GlobalConfig.getInstance().isDatabaseSupport()) {
            Databases.loadData(uuid);
        }
        else {
            PlayerDataNoDatabase.get(uuid).reload();
        }
    }

    public void reload() {
        if (!Databases.loadData()) {
            PlayerDataNoDatabase.get(uuid).reload();
        }
    }
}
