package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class FlowAction implements MacroAction {

    public enum Target { FORWARD, BACK, STEP, LABEL, TOP, END, STOP }
    public enum MissingPolicy { CONTINUE, STOP }

    public Target target = Target.LABEL;
    public int amount = 1;
    public String labelName = "";
    public MissingPolicy onMissingLabel = MissingPolicy.CONTINUE;
    public boolean conditional = false;
    public MacroCondition condition = MacroCondition.defaultRoot();
    private boolean enabled = true;

    @Override public void execute(Minecraft mc) {}
    @Override public MacroActionType getType() { return MacroActionType.FLOW; }

    public boolean shouldTake(Minecraft mc) {
        return !conditional || condition == null || condition.evaluate(mc);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "FLOW");
        tag.putString("target", target.name());
        tag.putInt("amount", amount);
        tag.putString("labelName", labelName == null ? "" : labelName);
        tag.putString("onMissingLabel", onMissingLabel.name());
        tag.putBoolean("conditional", conditional);
        MacroCondition c = condition == null ? MacroCondition.defaultRoot() : condition;
        tag.put("condition", c.toTag());
        c.writeFlat(tag, "cond_");
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        target = MacroStringList.enumValue(Target.class, tag.getStringOr("target", "LABEL"), Target.LABEL);
        amount = Math.max(0, tag.getIntOr("amount", 1));
        labelName = tag.getStringOr("labelName", "");
        onMissingLabel = MacroStringList.enumValue(MissingPolicy.class, tag.getStringOr("onMissingLabel", "CONTINUE"), MissingPolicy.CONTINUE);
        conditional = tag.getBooleanOr("conditional", false);
        condition = MacroCondition.readForEditor(tag, "condition", "cond_");
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public String getDisplayName() {
        String where = switch (target) {
            case FORWARD -> "skip " + amount;
            case BACK -> "back " + amount;
            case STEP -> "step " + amount;
            case LABEL -> "→ " + (labelName == null || labelName.isBlank() ? "?" : labelName);
            case TOP -> "→ top";
            case END -> "→ end";
            case STOP -> "stop";
        };
        return "Goto " + where + (conditional ? " if " + (condition == null ? "" : condition.summary()) : "");
    }

    @Override public String getIcon() { return "GO"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { enabled = e; }
}
