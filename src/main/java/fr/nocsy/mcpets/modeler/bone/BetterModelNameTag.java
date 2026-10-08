package fr.nocsy.mcpets.modeler.bone;

import fr.nocsy.mcpets.data.config.GlobalConfig;
import fr.nocsy.mcpets.utils.Utils;
import kr.toxicity.model.api.nms.ModelNametag;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.UUID;

public class BetterModelNameTag implements AbstractNameTag {

    private final ModelNametag delegate;
    private final UUID petUUID;

    public BetterModelNameTag(ModelNametag delegate, UUID petUUID) {
        this.delegate = delegate;
        this.petUUID = petUUID;
    }

    @Override
    public void setString(String string) {
        if (GlobalConfig.getInstance().isNameTagAlwaysVisible()) {
            LookAtNameDisplay.remove(petUUID);
            delegate.component(string == null ? null : LegacyComponentSerializer.legacySection().deserialize(string));
            return;
        }
        // BetterModel's look check is off by the player's eye height, so MCPets draws the name itself
        delegate.component(null);
        if (string == null)
            LookAtNameDisplay.remove(petUUID);
        else
            LookAtNameDisplay.set(petUUID, Utils.toComponent(string));
    }

    @Override
    public void setVisible(boolean visible) {
        if (GlobalConfig.getInstance().isNameTagAlwaysVisible())
            delegate.alwaysVisible(visible);
        else if (!visible)
            LookAtNameDisplay.remove(petUUID);
    }
}
