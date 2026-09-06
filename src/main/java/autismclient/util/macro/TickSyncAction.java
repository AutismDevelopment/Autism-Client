package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class TickSyncAction implements MacroAction {

    public int tickOffset = 1;

    public int preGenCount = -1;

    public TickSyncAction() {}

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putInt("tickOffset", tickOffset);
        tag.putInt("preGenCount", preGenCount);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        tickOffset = tag.getIntOr("tickOffset", 1);
        preGenCount = tag.getIntOr("preGenCount", -1);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.TICK_SYNC;
    }

    @Override
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder("Tick Sync");
        if (tickOffset > 0) {
            sb.append(" +").append(tickOffset);
        }
        if (preGenCount > 0) {
            sb.append(" (").append(preGenCount).append(" pkts)");
        } else if (preGenCount == -1) {
            sb.append(" (all)");
        }
        return sb.toString();
    }

    @Override
    public String getIcon() {
        return "Tick";
    }
}
