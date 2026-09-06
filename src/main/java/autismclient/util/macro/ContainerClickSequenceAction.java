package autismclient.util.macro;

import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismSharedState;
import autismclient.modules.PackHideState;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerInput;

public class ContainerClickSequenceAction implements MacroAction {
    public enum SlotSource { SINGLE, RANGE, LIST, CAPTURED_SEQUENCE }
    public enum ContainerSource { CURRENT, SAVED_GUI, PLAYER_INVENTORY, MANUAL }

    public SlotSource slotSource = SlotSource.SINGLE;
    public ContainerSource containerSource = ContainerSource.CURRENT;
    public int slot = 0;
    public int startSlot = 0;
    public int endSlot = 0;
    public ArrayList<String> slots = new ArrayList<>();
    public int manualContainerId = 0;
    public int savedContainerId = -1;
    public String button = "0 (Left/Primary)";
    public String containerInput = "PICKUP";
    public int repeatCount = 1;
    public int delayTicks = 0;

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (mc == null || mc.player == null || mc.gameMode == null || mc.player.containerMenu == null) return;
        java.util.List<Integer> clickSlots = resolvedSlots();
        for (int r = 0; r < Math.max(1, repeatCount); r++) {
            for (int visibleSlot : clickSlots) {
                executeClick(mc, visibleSlot);
            }
        }
    }

    boolean executeClick(Minecraft mc, int visibleSlot) {
        if (PackHideState.isHardLocked() || mc == null || mc.player == null || mc.gameMode == null
                || mc.player.containerMenu == null) return false;
        int handlerSlot = containerSource == ContainerSource.CURRENT || containerSource == ContainerSource.PLAYER_INVENTORY
            ? AutismInventoryHelper.toHandlerSlot(mc, visibleSlot)
            : visibleSlot;
        if (handlerSlot < 0) return false;
        int buttonNum = 0;
        try { buttonNum = Integer.parseInt(button.split(" ")[0]); } catch (Exception ignored) {  }
        ContainerInput input = MacroStringList.enumValue(ContainerInput.class, containerInput, ContainerInput.PICKUP);
        mc.gameMode.handleContainerInput(resolvedContainerId(mc), handlerSlot, buttonNum, input, mc.player);
        return true;
    }

    private int resolvedContainerId(Minecraft mc) {
        return switch (containerSource) {
            case CURRENT -> mc.player.containerMenu.containerId;
            case SAVED_GUI -> {
                var saved = AutismSharedState.get().getStoredAbstractContainerMenu();
                yield saved == null ? savedContainerId : saved.containerId;
            }
            case PLAYER_INVENTORY -> 0;
            case MANUAL -> manualContainerId;
        };
    }

    public java.util.List<Integer> resolvedSlots() {
        ArrayList<Integer> out = new ArrayList<>();
        switch (slotSource) {
            case SINGLE -> out.add(slot);
            case RANGE -> {
                int a = Math.min(startSlot, endSlot);
                int b = Math.max(startSlot, endSlot);
                for (int i = a; i <= b; i++) out.add(i);
            }
            case LIST -> {
                for (String raw : slots) {
                    try { out.add(Integer.parseInt(raw.trim())); } catch (Exception ignored) {  }
                }
            }
            case CAPTURED_SEQUENCE -> {
                for (String raw : slots) {
                    try { out.add(Integer.parseInt(raw.trim())); } catch (Exception ignored) {  }
                }
            }
        }
        return out;
    }

    @Override public MacroActionType getType() { return MacroActionType.CONTAINER_CLICK_SEQUENCE; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "CONTAINER_CLICK_SEQUENCE");
        tag.putString("slotSource", slotSource.name());
        tag.putString("containerSource", containerSource.name());
        tag.putInt("slot", slot);
        tag.putInt("startSlot", startSlot);
        tag.putInt("endSlot", endSlot);
        tag.put("slots", MacroStringList.toTag(slots));
        tag.putInt("manualContainerId", manualContainerId);
        tag.putInt("savedContainerId", savedContainerId);
        tag.putString("button", button);
        tag.putString("containerInput", containerInput);
        tag.putInt("repeatCount", repeatCount);
        tag.putInt("delayTicks", delayTicks);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        slotSource = MacroStringList.enumValue(SlotSource.class, tag.getStringOr("slotSource", "SINGLE"), SlotSource.SINGLE);
        containerSource = MacroStringList.enumValue(ContainerSource.class, tag.getStringOr("containerSource", "CURRENT"), ContainerSource.CURRENT);
        slot = tag.getIntOr("slot", 0);
        startSlot = tag.getIntOr("startSlot", 0);
        endSlot = tag.getIntOr("endSlot", 0);
        slots = MacroStringList.fromTag(tag.getList("slots").orElse(new net.minecraft.nbt.ListTag()));
        manualContainerId = tag.getIntOr("manualContainerId", 0);
        savedContainerId = tag.getIntOr("savedContainerId", -1);
        String btnStr = tag.getStringOr("button", "");
        if (btnStr.isEmpty() || btnStr.matches("\\d+")) {
            int b = tag.getIntOr("button", btnStr.isEmpty() ? 0 : Integer.parseInt(btnStr));
            if (b == 0) button = "0 (Left/Primary)";
            else if (b == 1) button = "1 (Right/Secondary)";
            else if (b == 2) button = "2 (Middle)";
            else button = String.valueOf(b);
        } else {
            button = btnStr;
        }
        containerInput = tag.getStringOr("containerInput", "PICKUP");
        repeatCount = tag.getIntOr("repeatCount", 1);
        delayTicks = tag.getIntOr("delayTicks", 0);
    }

    @Override public String getDisplayName() { return "Click slots " + slotSource + " " + containerInput; }
    @Override public String getIcon() { return "C"; }
}
