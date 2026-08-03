package autismclient.util.macro;

import autismclient.util.AutismInventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class UseItemAction implements MacroAction, WaitsForGui {
    public enum UseMode { AUTOMATIC, CUSTOM_HOLD }

    public String itemName = "";
    public ItemTarget itemTarget = new ItemTarget();
    public int slot = -1;
    public UseMode useMode = UseMode.AUTOMATIC;
    public boolean waitForFinish = true;
    public int holdTicks = 20;
    public int useCount = 1;

    public boolean sneak = false;

    public boolean waitForGui = false;
    public String guiName = "";

    private boolean enabled = true;

    public UseItemAction() {}

    @Override
    public void execute(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return;

        ItemTarget target = resolvedItemTarget();
        target = target.resolveTemplate(mc);
        if (target == null) return;
        if (target.hasSlot() || target.hasIdentity()) {
            AutismInventoryHelper.selectHotbarItem(mc, target, mc.player.getInventory().getSelectedSlot());
        }

        mc.getConnection().send(
            new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), mc.player.getXRot())
        );
    }

    public void sendRelease(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
            BlockPos.ZERO,
            Direction.DOWN
        ));
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.USE_ITEM;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "USE_ITEM");
        ItemTarget target = resolvedItemTarget();
        if (target.hasSlot() || target.hasIdentity()) tag.put("itemName", target.toTag());
        if (slot >= 0) tag.putInt("slot", slot);
        tag.putString("useMode", useMode.name());
        tag.putBoolean("waitForFinish", waitForFinish);
        tag.putInt("holdTicks", holdTicks);
        tag.putInt("useCount", useCount);
        tag.putBoolean("sneak", sneak);
        tag.putBoolean("waitForGui", waitForGui);
        tag.putString("guiName", guiName == null ? "" : guiName);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        itemTarget = tag.getCompound("itemName").map(ItemTarget::fromTag).orElseGet(() -> ItemTarget.fromLegacyEntry(tag.getStringOr("itemName", "")));
        slot = tag.contains("slot") ? Math.max(-1, tag.getIntOr("slot", -1)) : itemTarget.slot;
        itemTarget.slot = -1;
        itemName = itemTarget.toLegacyEntry();
        if (tag.contains("useMode")) {
            String modeName = tag.getStringOr("useMode", "AUTOMATIC");
            if ("INSTANT".equals(modeName)) modeName = "AUTOMATIC";
            if ("HOLD".equals(modeName)) modeName = "CUSTOM_HOLD";
            try { useMode = UseMode.valueOf(modeName); }
            catch (IllegalArgumentException ignored) { useMode = UseMode.AUTOMATIC; }
        }
        waitForFinish = tag.getBooleanOr("waitForFinish", true);
        if (tag.contains("holdTicks")) holdTicks = tag.getIntOr("holdTicks", 20);
        if (tag.contains("useCount")) useCount = Math.max(1, tag.getIntOr("useCount", 1));
        sneak = tag.getBooleanOr("sneak", false);
        waitForGui = tag.getBooleanOr("waitForGui", false);
        guiName = tag.getStringOr("guiName", "");
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override public boolean isWaitForGuiBefore() { return false; }
    @Override public void setWaitForGuiBefore(boolean v) {  }
    @Override public boolean isWaitForGuiAfter() { return waitForGui; }
    @Override public void setWaitForGuiAfter(boolean v) { waitForGui = v; }
    @Override public String getWaitGuiName() { return guiName == null ? "" : guiName; }
    @Override public void setWaitGuiName(String name) { guiName = name == null ? "" : name; }

    @Override
    public String getDisplayName() {
        ItemTarget target = resolvedItemTarget();
        String item = (target.hasSlot() || target.hasIdentity()) ? target.summaryText() : "current";
        String count = useCount > 1 ? " x" + useCount : "";
        String wait = waitForFinish ? ", wait" : "";
        String sneakLabel = sneak ? ", sneak" : "";
        String base = useMode == UseMode.CUSTOM_HOLD
            ? "Use Item (" + item + ", hold " + holdTicks + "t" + wait + sneakLabel + ")"
            : "Use Item (" + item + count + wait + sneakLabel + ")";
        return base + WaitsForGui.timingLabel(this);
    }

    @Override public String getIcon() { return "U"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    private ItemTarget resolvedItemTarget() {
        ItemTarget target = itemTarget != null ? itemTarget.copy() : ItemTarget.fromLegacyEntry(itemName);
        if (!target.hasSlot() && !target.hasIdentity()) target = ItemTarget.fromLegacyEntry(itemName);
        target.slot = slot;
        return target;
    }
}
