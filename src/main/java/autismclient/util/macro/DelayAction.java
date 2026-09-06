package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class DelayAction implements MacroAction {
    public int delayMs = 1000;
    public boolean useTicks = false;
    public int delayTicks = 20;

    public DelayAction() {}

    public DelayAction(int delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putInt("delayMs", delayMs);
        tag.putBoolean("useTicks", useTicks);
        tag.putInt("delayTicks", delayTicks);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("delayMs")) {
            delayMs = tag.getIntOr("delayMs", 0);
        } else if (tag.contains("delayTicks") && !tag.contains("useTicks")) {

            delayMs = tag.getIntOr("delayTicks", 0) * 50;
        }

        if (tag.contains("useTicks")) {
            useTicks = tag.getBooleanOr("useTicks", false);
        }

        if (tag.contains("delayTicks")) {
            delayTicks = tag.getIntOr("delayTicks", 20);
        }
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.DELAY;
    }

    @Override
    public String getDisplayName() {
        return useTicks ? "Delay: " + delayTicks + " ticks" : "Delay: " + delayMs + " ms";
    }

    @Override
    public String getIcon() {
        return "Wait";
    }
}
