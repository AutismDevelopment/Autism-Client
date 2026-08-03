package autismclient.util.macro;

import autismclient.util.AutismGuiActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;

public class InventoryAction implements MacroAction, WaitsForGui {
    public enum InvMode {
        OPEN,
        CLOSE
    }

    public InvMode mode = InvMode.OPEN;
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = true;
    public String guiName = "";

    public boolean sendPacket = true;

    public InventoryAction() {}

    @Override
    public void execute(Minecraft mc) {
        if (mc.player == null) return;

        if (mode == InvMode.OPEN) {
            mc.execute(() -> {
                mc.gui.setScreen(new InventoryScreen(mc.player));
            });
        } else {
            mc.execute(() -> {
                AutismGuiActions.closeCurrentScreen(mc, sendPacket, false);
            });
        }
    }

    @Override public boolean isWaitForGuiBefore() { return mode == InvMode.OPEN && waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { this.waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter() { return mode == InvMode.OPEN && waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { this.waitForGuiAfter = v; }
    @Override public String getWaitGuiName() { return ""; }
    @Override public void setWaitGuiName(String name) { this.guiName = ""; }

    @Override
    public MacroActionType getType() {
        return MacroActionType.INVENTORY;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "INVENTORY");
        tag.putString("mode", mode.name());
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putString("guiName", guiName);
        tag.putBoolean("sendPacket", sendPacket);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("mode")) {
            try {
                this.mode = InvMode.valueOf(tag.getStringOr("mode", "OPEN"));
            } catch (IllegalArgumentException ignored) {
                this.mode = InvMode.OPEN;
            }
        }
        waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        waitForGuiAfter = WaitsForGui.loadAfter(tag, true);
        if (tag.contains("guiName")) this.guiName = tag.getStringOr("guiName", "");
        if (tag.contains("sendPacket")) this.sendPacket = tag.getBooleanOr("sendPacket", true);
    }

    @Override
    public String getDisplayName() {
        if (mode == InvMode.OPEN) return "Inv Open" + WaitsForGui.timingLabel(this);
        return sendPacket ? "Inv Close" : "Inv Close (no pkt)";
    }

    @Override
    public String getIcon() {
        return "I";
    }
}
