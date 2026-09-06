package autismclient.util.macro;

import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismGuiActions;
import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class CloseGuiAction implements MacroAction {
    public String guiName = "";
    public String itemName = "";
    public ItemTarget itemTarget = new ItemTarget();
    public int targetSlot = -1;
    public boolean useItemFilter = false;
    public boolean sendPacket = true;
    private boolean enabled = true;

    public CloseGuiAction() {}

    @Override
    public void execute(Minecraft mc) {
        if (mc.player == null || mc.gui.screen() == null) return;

        boolean matchGui = true;
        if (!guiName.isEmpty()) {
            MacroTemplate.Resolution gui = MacroVariables.resolve(guiName, mc);
            if (!gui.success()) return;
            matchGui = mc.gui.screen().getTitle().getString().toLowerCase().contains(gui.value().toLowerCase());
        }

        boolean matchItem = true;
        ItemTarget target = resolvedItemTarget().resolveTemplate(mc);
        if (useItemFilter && target == null) return;
        if (useItemFilter && (target.hasSlot() || target.hasIdentity())) {
            matchItem = false;
            if (mc.player.containerMenu != null) {
                int effectiveSlot = target.hasSlot() ? target.slot : targetSlot;
                int resolvedTargetSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, effectiveSlot);
                if (resolvedTargetSlot >= 0 && resolvedTargetSlot < mc.player.containerMenu.slots.size()) {
                    net.minecraft.world.item.ItemStack stack = mc.player.containerMenu.slots.get(resolvedTargetSlot).getItem();
                    int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, resolvedTargetSlot);
                    if (!stack.isEmpty() && target.matches(stack, visibleSlot)) {
                        matchItem = true;
                    }
                } else if (effectiveSlot == -1) {
                    for (net.minecraft.world.inventory.Slot slot : mc.player.containerMenu.slots) {
                        net.minecraft.world.item.ItemStack stack = slot.getItem();
                        int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                        if (!stack.isEmpty() && target.matches(stack, visibleSlot)) {
                            matchItem = true;
                            break;
                        }
                    }
                }
            }
        }

        if (matchGui && matchItem) {
            AutismGuiActions.closeCurrentScreen(mc, sendPacket, false);
        }
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.CLOSE_GUI;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "CLOSE_GUI");
        tag.putString("guiName", guiName);
        ItemTarget target = resolvedItemTarget();
        if (target.hasSlot() || target.hasIdentity()) tag.put("itemName", target.toTag());
        tag.putBoolean("useItemFilter", useItemFilter);
        tag.putBoolean("sendPacket", sendPacket);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("guiName")) this.guiName = tag.getStringOr("guiName", "");
        this.itemTarget = tag.getCompound("itemName").map(ItemTarget::fromTag).orElseGet(() -> {
            int legacySlot = tag.contains("targetSlot") ? tag.getIntOr("targetSlot", -1) : -1;
            String legacyName = tag.getStringOr("itemName", "");
            if (legacySlot >= 0 && !legacyName.isBlank()) return ItemTarget.fromLegacyEntry("#" + legacySlot + "|" + legacyName);
            if (legacySlot >= 0) return ItemTarget.slotOnly(legacySlot);
            return ItemTarget.fromLegacyEntry(legacyName);
        });
        this.itemName = itemTarget.toLegacyEntry();
        this.targetSlot = itemTarget.hasSlot() ? itemTarget.slot : -1;
        if (tag.contains("useItemFilter")) this.useItemFilter = tag.getBooleanOr("useItemFilter", false);
        else this.useItemFilter = itemTarget.hasSlot() || itemTarget.hasIdentity();
        if (tag.contains("sendPacket")) this.sendPacket = tag.getBooleanOr("sendPacket", true);
        if (tag.contains("enabled")) this.enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public String getDisplayName() {
        String base = !guiName.isEmpty() ? "Close GUI: " + guiName : "Close GUI";
        return sendPacket ? base : base + " (desync)";
    }

    @Override
    public String getIcon() {
        return "X";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    private ItemTarget resolvedItemTarget() {
        if (itemTarget != null && (itemTarget.hasSlot() || itemTarget.hasIdentity())) return itemTarget;
        if (targetSlot >= 0 && !itemName.isBlank()) itemTarget = ItemTarget.fromLegacyEntry("#" + targetSlot + "|" + itemName);
        else if (targetSlot >= 0) itemTarget = ItemTarget.slotOnly(targetSlot);
        else itemTarget = ItemTarget.fromLegacyEntry(itemName);
        return itemTarget;
    }
}
