package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class ServerTickSyncAction implements MacroAction {

    public int bufferMs = 5;

    public int maxWaitMs = 3000;

    public boolean ignorePing = false;

    public int preGenCount = -1;

    public ServerTickSyncAction() {}

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.SERVER_TICK_SYNC;
    }

    @Override
    public String getIcon() {
        return "Srv";
    }

    @Override
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder("ServerSync");
        sb.append(" buf:").append(bufferMs).append("ms");
        if (ignorePing) {
            sb.append(" (no ping)");
        }
        if (preGenCount > 0) {
            sb.append(" (").append(preGenCount).append(" pkts)");
        } else if (preGenCount == -1) {
            sb.append(" (all)");
        }
        return sb.toString();
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putInt("bufferMs", bufferMs);
        tag.putInt("maxWaitMs", maxWaitMs);
        tag.putBoolean("ignorePing", ignorePing);
        tag.putInt("preGenCount", preGenCount);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        bufferMs = tag.getIntOr("bufferMs", 5);
        maxWaitMs = tag.getIntOr("maxWaitMs", 3000);
        ignorePing = tag.getBooleanOr("ignorePing", false);
        preGenCount = tag.getIntOr("preGenCount", -1);
    }
}
