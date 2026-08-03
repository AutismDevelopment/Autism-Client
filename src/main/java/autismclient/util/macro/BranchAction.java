package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class BranchAction implements MacroAction {
    public enum ConditionKind { ALWAYS, GUI_TYPE, INVENTORY_ITEM, ENTITY_TARGET, HELD_ITEM }

    public ConditionKind conditionKind = ConditionKind.ALWAYS;
    public String value = "";
    public int thenSteps = 1;
    public int elseSteps = 0;

    @Override public void execute(Minecraft mc) {}
    @Override public MacroActionType getType() { return MacroActionType.BRANCH; }

    public boolean matches(Minecraft mc) {
        if (mc == null) return false;
        MacroTemplate.Resolution resolution = MacroVariables.resolve(value, mc);
        if (!resolution.success()) return false;
        String resolved = resolution.value();
        return switch (conditionKind) {
            case ALWAYS -> true;
            case GUI_TYPE -> MacroGuiMatcher.matches(mc.gui.screen(), resolved);
            case INVENTORY_ITEM -> WaitInventoryPredicateAction.hasInventoryItem(mc, ItemTarget.fromLegacyEntry(resolved));
            case HELD_ITEM -> mc.player != null && ItemTarget.fromLegacyEntry(resolved).score(mc.player.getMainHandItem(), mc.player.getInventory().getSelectedSlot()) >= 0;
            case ENTITY_TARGET -> mc.crosshairPickEntity != null;
        };
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "BRANCH");
        tag.putString("conditionKind", conditionKind.name());
        tag.putString("value", value);
        tag.putInt("thenSteps", thenSteps);
        tag.putInt("elseSteps", elseSteps);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        conditionKind = MacroStringList.enumValue(ConditionKind.class, tag.getStringOr("conditionKind", "ALWAYS"), ConditionKind.ALWAYS);
        value = tag.getStringOr("value", "");
        thenSteps = tag.getIntOr("thenSteps", 1);
        elseSteps = tag.getIntOr("elseSteps", 0);
    }

    @Override public String getDisplayName() { return "If " + conditionKind + " then " + thenSteps + " else " + elseSteps; }
    @Override public String getIcon() { return "?"; }
}
