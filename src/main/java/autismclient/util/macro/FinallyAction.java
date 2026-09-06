package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class FinallyAction implements MacroAction {
    public int bodyCount = 1;

    @Override public void execute(Minecraft mc) {}
    @Override public MacroActionType getType() { return MacroActionType.FINALLY; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "FINALLY");
        tag.putInt("bodyCount", bodyCount);
        return tag;
    }

    @Override public void fromTag(CompoundTag tag) { bodyCount = tag.getIntOr("bodyCount", 1); }
    @Override public String getDisplayName() { return "Finally cleanup next " + bodyCount; }
    @Override public String getIcon() { return "F"; }
}
