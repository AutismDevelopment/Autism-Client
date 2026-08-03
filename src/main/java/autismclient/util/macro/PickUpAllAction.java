package autismclient.util.macro;

import autismclient.util.AutismCursorClickHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class PickUpAllAction implements MacroAction {
    public int times = 1;

    @Override
    public void execute(Minecraft mc) {
        AutismCursorClickHelper.click(mc, null, times);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "PICK_UP_ALL");
        tag.putInt("times", Math.max(1, times));
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        times = Math.max(1, tag.getIntOr("times", 1));
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.PICK_UP_ALL;
    }

    @Override
    public String getDisplayName() {
        return "Pick Up All" + (times > 1 ? " x" + times : "");
    }

    @Override
    public String getIcon() {
        return "All";
    }
}
