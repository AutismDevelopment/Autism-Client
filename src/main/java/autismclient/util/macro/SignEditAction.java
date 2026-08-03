package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismSignEditAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

public class SignEditAction implements MacroAction, WaitsForGui {
    public enum TargetMode { CURRENT_SIGN_GUI, LAST_INTERACTED_BLOCK, MANUAL_POS }
    public enum CloseMode { STAY_OPEN, CLOSE_LOCAL, CLOSE_WITH_PACKET, SEND_CLOSE_PACKET_ONLY }

    public TargetMode targetMode = TargetMode.CURRENT_SIGN_GUI;
    public String line1 = "";
    public String line2 = "";
    public String line3 = "";
    public String line4 = "";
    public boolean frontText = true;
    public int x = 0;
    public int y = 0;
    public int z = 0;
    public boolean waitForGuiBefore = true;
    public boolean waitForGuiAfter = false;
    public String guiName = "SIGN";
    public CloseMode closeMode = CloseMode.STAY_OPEN;
    public boolean closeAfterEdit = false;
    public boolean closeWithPacket = false;
    public boolean sendCommandAfter = false;
    public String commandAfter = "";
    public boolean sendClosePacketAfter = false;
    public int closePacketContainerId = 0;

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (mc == null || mc.getConnection() == null) return;
        BlockPos pos = resolvePos(mc);
        if (pos == null) return;
        MacroTemplate.Resolution resolvedLine1 = MacroVariables.resolve(line1, mc);
        MacroTemplate.Resolution resolvedLine2 = MacroVariables.resolve(line2, mc);
        MacroTemplate.Resolution resolvedLine3 = MacroVariables.resolve(line3, mc);
        MacroTemplate.Resolution resolvedLine4 = MacroVariables.resolve(line4, mc);
        if (!resolvedLine1.success() || !resolvedLine2.success() || !resolvedLine3.success() || !resolvedLine4.success()) return;
        MacroTemplate.Resolution resolvedCommand = MacroTemplate.Resolution.ok("");
        if (sendCommandAfter && commandAfter != null && !commandAfter.isBlank()) {
            resolvedCommand = MacroVariables.resolve(commandAfter, mc);
            if (!resolvedCommand.success()) return;
        }
        AutismSharedState.get().setForceNextSignUpdatePacket(true);
        mc.getConnection().send(new ServerboundSignUpdatePacket(
            pos,
            resolveFront(mc),
            resolvedLine1.value(),
            resolvedLine2.value(),
            resolvedLine3.value(),
            resolvedLine4.value()
        ));
        if (sendCommandAfter && commandAfter != null && !commandAfter.isBlank()) {
            String command = resolvedCommand.value().trim();
            if (command.startsWith("/")) command = command.substring(1);
            if (!command.isBlank()) mc.getConnection().sendCommand(command);
        }
        CloseMode mode = closeMode == null ? CloseMode.STAY_OPEN : closeMode;
        if (mode == CloseMode.SEND_CLOSE_PACKET_ONLY) {
            mc.getConnection().send(new ServerboundContainerClosePacket(closePacketContainerId));
        }
        if (mode == CloseMode.CLOSE_LOCAL || mode == CloseMode.CLOSE_WITH_PACKET) {
            if (mode == CloseMode.CLOSE_WITH_PACKET && mc.player != null && mc.player.containerMenu != null) {
                mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            }
            mc.gui.setScreen(null);
        }
    }

    private BlockPos resolvePos(Minecraft mc) {
        if (targetMode == TargetMode.MANUAL_POS) return new BlockPos(x, y, z);
        if (targetMode == TargetMode.LAST_INTERACTED_BLOCK) return AutismSharedState.get().getLastInteractedBlockPos();
        if (mc.gui.screen() instanceof AutismSignEditAccess access) return access.autism$getSignPos();
        return null;
    }

    private boolean resolveFront(Minecraft mc) {
        if (targetMode == TargetMode.CURRENT_SIGN_GUI && mc.gui.screen() instanceof AutismSignEditAccess access) return access.autism$isFrontText();
        return frontText;
    }

    @Override public MacroActionType getType() { return MacroActionType.SIGN_EDIT; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "SIGN_EDIT");
        tag.putString("targetMode", targetMode.name());
        tag.putString("line1", line1);
        tag.putString("line2", line2);
        tag.putString("line3", line3);
        tag.putString("line4", line4);
        tag.putBoolean("frontText", frontText);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putString("guiName", guiName);
        tag.putString("closeMode", closeMode == null ? CloseMode.STAY_OPEN.name() : closeMode.name());
        tag.putBoolean("sendCommandAfter", sendCommandAfter);
        tag.putString("commandAfter", commandAfter);
        tag.putInt("closePacketContainerId", closePacketContainerId);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        targetMode = MacroStringList.enumValue(TargetMode.class, tag.getStringOr("targetMode", "CURRENT_SIGN_GUI"), TargetMode.CURRENT_SIGN_GUI);
        line1 = tag.getStringOr("line1", "");
        line2 = tag.getStringOr("line2", "");
        line3 = tag.getStringOr("line3", "");
        line4 = tag.getStringOr("line4", "");
        frontText = tag.getBooleanOr("frontText", true);
        x = tag.getIntOr("x", 0);
        y = tag.getIntOr("y", 0);
        z = tag.getIntOr("z", 0);
        waitForGuiBefore = tag.getBooleanOr("waitForGuiBefore", true);
        waitForGuiAfter = tag.getBooleanOr("waitForGuiAfter", false);
        guiName = tag.getStringOr("guiName", "SIGN");
        if (tag.contains("closeMode")) {
            closeMode = MacroStringList.enumValue(CloseMode.class, tag.getStringOr("closeMode", "STAY_OPEN"), CloseMode.STAY_OPEN);
        } else if (tag.getBooleanOr("sendClosePacketAfter", false)) {
            closeMode = CloseMode.SEND_CLOSE_PACKET_ONLY;
        } else if (tag.getBooleanOr("closeAfterEdit", false)) {
            closeMode = tag.getBooleanOr("closeWithPacket", false) ? CloseMode.CLOSE_WITH_PACKET : CloseMode.CLOSE_LOCAL;
        } else {
            closeMode = CloseMode.STAY_OPEN;
        }
        closeAfterEdit = closeMode == CloseMode.CLOSE_LOCAL || closeMode == CloseMode.CLOSE_WITH_PACKET;
        closeWithPacket = closeMode == CloseMode.CLOSE_WITH_PACKET;
        sendCommandAfter = tag.getBooleanOr("sendCommandAfter", false);
        commandAfter = tag.getStringOr("commandAfter", "");
        sendClosePacketAfter = closeMode == CloseMode.SEND_CLOSE_PACKET_ONLY;
        closePacketContainerId = tag.getIntOr("closePacketContainerId", 0);
    }

    @Override public String getDisplayName() {
        String suffix = closeMode != CloseMode.STAY_OPEN ? " + " + closeMode.name().toLowerCase(java.util.Locale.ROOT) : (sendCommandAfter ? " + command" : "");
        return "Edit sign" + suffix;
    }
    @Override public String getIcon() { return "S"; }
    @Override public boolean isWaitForGuiBefore() { return waitForGuiBefore; }
    @Override public boolean isWaitForGuiAfter() { return waitForGuiAfter; }
    @Override public String getWaitGuiName() { return guiName; }
    @Override public void setWaitForGuiBefore(boolean value) { waitForGuiBefore = value; }
    @Override public void setWaitForGuiAfter(boolean value) { waitForGuiAfter = value; }
    @Override public void setWaitGuiName(String value) { guiName = value; }
}
