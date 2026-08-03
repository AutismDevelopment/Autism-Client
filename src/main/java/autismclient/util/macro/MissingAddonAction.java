package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public final class MissingAddonAction implements MacroAction {
    private String typeId;
    private CompoundTag raw;

    public MissingAddonAction() {
        this.typeId = "";
        this.raw = new CompoundTag();
    }

    public MissingAddonAction(String typeId, CompoundTag raw) {
        this.typeId = typeId == null ? "" : typeId;
        this.raw = raw == null ? new CompoundTag() : raw;
    }

    public String missingTypeId() {
        return typeId;
    }

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {

        return raw.copy();
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag == null) return;
        this.raw = tag.copy();
        this.typeId = tag.getStringOr("type", this.typeId);
    }

    @Override
    public String getTypeId() {
        return typeId;
    }

    @Override
    public String getDisplayName() {
        return "! Missing: " + typeId;
    }

    @Override
    public String getIcon() {
        return "!";
    }

    @Override
    public void sanitizeForSharing() {

        if (raw == null) return;
        raw.remove("customFilePath");
        raw.remove("javaSource");
        raw.remove("payloadScript");
        raw.remove("payloadScriptEnabled");
        raw.remove("commandAfter");
    }
}
