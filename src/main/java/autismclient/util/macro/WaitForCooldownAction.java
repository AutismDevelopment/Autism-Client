package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitForCooldownAction implements MacroAction {
    public String itemName = "";
    public ItemTarget itemTarget = new ItemTarget();
    public boolean checkMainInteractionHand = true;
    public boolean listenDuringPreviousAction = false;

    public WaitForCooldownAction() {}

    public WaitForCooldownAction(String itemName, boolean checkMainHand) {
        this.itemName = itemName;
        this.itemTarget = ItemTarget.fromLegacyEntry(itemName);
        this.checkMainInteractionHand = checkMainHand;
    }

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        ItemTarget target = resolvedItemTarget();
        if (target.hasSlot() || target.hasIdentity()) tag.put("itemName", target.toTag());
        tag.putBoolean("checkMainHand", checkMainInteractionHand);
        MacroWaitOptions.write(tag, this);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        itemTarget = tag.getCompound("itemName").map(ItemTarget::fromTag).orElseGet(() -> ItemTarget.fromLegacyEntry(tag.getStringOr("itemName", "")));
        itemName = itemTarget.toLegacyEntry();
        if (tag.contains("checkMainHand")) checkMainInteractionHand = tag.getBooleanOr("checkMainHand", true);
        MacroWaitOptions.read(tag, this);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.WAIT_COOLDOWN;
    }

    @Override
    public String getDisplayName() {
        ItemTarget target = resolvedItemTarget();
        String base = (target.hasSlot() || target.hasIdentity()) ? target.summaryText() : (checkMainInteractionHand ? "Main Hand" : "Off Hand");
        if (base.length() > 10) base = base.substring(0, 8) + "..";
        return "Wait Cooldown (" + base + ")";
    }

    @Override
    public String getIcon() {
        return "CD";
    }

    private ItemTarget resolvedItemTarget() {
        if (itemTarget != null && (itemTarget.hasSlot() || itemTarget.hasIdentity())) return itemTarget;
        itemTarget = ItemTarget.fromLegacyEntry(itemName);
        return itemTarget;
    }
}
