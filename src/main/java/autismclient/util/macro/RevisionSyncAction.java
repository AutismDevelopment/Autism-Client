package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class RevisionSyncAction implements MacroAction {

    public int revisionOffset = 1;

    public int preGenCount = -1;

    public RevisionSyncAction() {}

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putInt("revisionOffset", revisionOffset);
        tag.putInt("preGenCount", preGenCount);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        revisionOffset = tag.getIntOr("revisionOffset", 1);
        preGenCount = tag.getIntOr("preGenCount", -1);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.REVISION_SYNC;
    }

    @Override
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder("Rev Sync");
        sb.append(" +").append(revisionOffset);
        if (preGenCount > 0) {
            sb.append(" (").append(preGenCount).append(" pkts)");
        } else if (preGenCount == -1) {
            sb.append(" (all)");
        }
        return sb.toString();
    }

    @Override
    public String getIcon() {
        return "Rev";
    }
}
