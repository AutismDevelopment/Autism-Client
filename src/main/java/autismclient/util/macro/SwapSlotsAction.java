package autismclient.util.macro;

import autismclient.util.AutismInventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class SwapSlotsAction implements MacroAction {
    public int fromSlot = -1;
    public int toSlot = -1;
    public String fromItemName = "";
    public String toItemName = "";
    public ItemTarget fromItemTarget = new ItemTarget();
    public ItemTarget toItemTarget = new ItemTarget();
    public boolean fromUseItemName = false;
    public boolean toUseItemName = false;
    public boolean useHandlerSlots = true;
    private boolean enabled = true;

    public SwapSlotsAction() {}

    @Override
    public void execute(Minecraft mc) {
        if (mc.player == null) return;

        int resolvedFrom = fromSlot;
        int resolvedTo = toSlot;

        if (fromUseItemName) {
            ItemTarget target = resolvedFromItemTarget().resolveTemplate(mc);
            if (target == null) return;
            if (!target.hasSlot() && !target.hasIdentity()) return;
            resolvedFrom = AutismInventoryHelper.findUserVisibleSlot(mc, target);
            if (resolvedFrom == -1) return;
        }
        if (toUseItemName) {
            ItemTarget target = resolvedToItemTarget().resolveTemplate(mc);
            if (target == null) return;
            if (!target.hasSlot() && !target.hasIdentity()) return;
            resolvedTo = AutismInventoryHelper.findUserVisibleSlot(mc, target);
            if (resolvedTo == -1) return;
        }

        int fromHandlerSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, resolvedFrom);
        int toHandlerSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, resolvedTo);
        if (fromHandlerSlot < 0 || toHandlerSlot < 0) return;

        AutismInventoryHelper.swapHandlerSlots(mc, fromHandlerSlot, toHandlerSlot);
    }

    @Override
    public MacroActionType getType() { return MacroActionType.SWAP_SLOTS; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "SWAP_SLOTS");
        tag.putInt("fromSlot", fromSlot);
        tag.putInt("toSlot", toSlot);
        ItemTarget fromTarget = resolvedFromItemTarget();
        ItemTarget toTarget = resolvedToItemTarget();
        if (fromTarget.hasSlot() || fromTarget.hasIdentity()) tag.put("fromItemName", fromTarget.toTag());
        if (toTarget.hasSlot() || toTarget.hasIdentity()) tag.put("toItemName", toTarget.toTag());
        tag.putBoolean("fromUseItemName", fromUseItemName);
        tag.putBoolean("toUseItemName", toUseItemName);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        this.fromSlot = tag.getIntOr("fromSlot", -1);
        this.toSlot = tag.getIntOr("toSlot", -1);
        this.fromItemTarget = tag.getCompound("fromItemName").map(ItemTarget::fromTag).orElseGet(() -> ItemTarget.fromLegacyEntry(tag.getStringOr("fromItemName", "")));
        this.toItemTarget = tag.getCompound("toItemName").map(ItemTarget::fromTag).orElseGet(() -> ItemTarget.fromLegacyEntry(tag.getStringOr("toItemName", "")));
        this.fromItemName = fromItemTarget.toLegacyEntry();
        this.toItemName = toItemTarget.toLegacyEntry();
        this.fromUseItemName = tag.contains("fromUseItemName") ? tag.getBooleanOr("fromUseItemName", false) : !this.fromItemName.isEmpty();
        this.toUseItemName = tag.contains("toUseItemName") ? tag.getBooleanOr("toUseItemName", false) : !this.toItemName.isEmpty();
        this.useHandlerSlots = true;
        if (tag.contains("enabled")) this.enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public String getDisplayName() {
        String from = fromUseItemName ? "\"" + resolvedFromItemTarget().summaryText() + "\"" : String.valueOf(fromSlot);
        String to = toUseItemName ? "\"" + resolvedToItemTarget().summaryText() + "\"" : String.valueOf(toSlot);
        return "Swap " + from + " <-> " + to;
    }

    @Override
    public String getIcon() { return "SWP"; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    private ItemTarget resolvedFromItemTarget() {
        if (fromItemTarget != null && (fromItemTarget.hasSlot() || fromItemTarget.hasIdentity())) return fromItemTarget;
        fromItemTarget = ItemTarget.fromLegacyEntry(fromItemName);
        return fromItemTarget;
    }

    private ItemTarget resolvedToItemTarget() {
        if (toItemTarget != null && (toItemTarget.hasSlot() || toItemTarget.hasIdentity())) return toItemTarget;
        toItemTarget = ItemTarget.fromLegacyEntry(toItemName);
        return toItemTarget;
    }
}
