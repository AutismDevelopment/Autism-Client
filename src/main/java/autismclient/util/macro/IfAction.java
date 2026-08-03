package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

public class IfAction implements MacroAction {

    public MacroCondition condition = MacroCondition.defaultRoot();
    public int thenSteps = 1;
    public int elseSteps = 0;
    private boolean enabled = true;

    @Override public void execute(Minecraft mc) {}
    @Override public MacroActionType getType() { return MacroActionType.IF; }

    public boolean evaluate(Minecraft mc) {
        return condition == null || condition.evaluate(mc);
    }

    public int normalizedChildCount(List<MacroAction> actions, int headerIndex) {
        if (actions == null || headerIndex < 0 || headerIndex >= actions.size()) return 0;
        int want = Math.max(0, thenSteps) + Math.max(0, elseSteps);
        int max = Math.min(want, actions.size() - headerIndex - 1);
        int count = 0;
        for (int i = headerIndex + 1; i < actions.size() && count < max; i++) {
            MacroAction a = actions.get(i);
            if (a instanceof IfAction || a instanceof RaceAction || a instanceof ReportAction || a instanceof PacketGateAction) break;
            count++;
        }
        return count;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "IF");
        MacroCondition c = condition == null ? MacroCondition.defaultRoot() : condition;
        tag.put("condition", c.toTag());
        c.writeFlat(tag, "cond_");
        tag.putInt("thenSteps", thenSteps);
        tag.putInt("elseSteps", elseSteps);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        condition = MacroCondition.readForEditor(tag, "condition", "cond_");
        thenSteps = Math.max(0, tag.getIntOr("thenSteps", 1));
        elseSteps = Math.max(0, tag.getIntOr("elseSteps", 0));
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public String getDisplayName() {
        String cond = condition == null ? "always" : condition.summary();
        return "If " + cond + " → " + thenSteps + (elseSteps > 0 ? " / else " + elseSteps : "");
    }

    @Override public String getIcon() { return "IF"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { enabled = e; }
}
