package autismclient.util.macro;

import autismclient.util.AutismContainerTarget;
import autismclient.util.AutismRegistryLabels;
import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.List;

public class InteractEntityAction implements MacroAction, WaitsForGui {
    public enum TargetMode { ENTITY, LAST_TARGET }

    public TargetMode targetMode = TargetMode.ENTITY;
    public List<String> entityTargets = new ArrayList<>();
    public boolean waitForTarget = true;
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = false;
    public String guiName = "";
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
        tryExecute(mc);
    }

    public boolean tryExecute(Minecraft mc) {
        if (mc == null || mc.player == null) return false;

        if (targetMode == TargetMode.LAST_TARGET) {
            AutismContainerTarget target = AutismSharedState.get().getLastContainerTarget();
            return target != null && (!waitForTarget || target.canInteract(mc)) && target.interact(mc);
        }

        boolean sentAny = false;
        for (String ref : entityTargets) {
            if (ref == null || ref.isBlank()) continue;
            AutismContainerTarget target = AutismContainerTarget.forEntityRef(ref);
            if (target == null) continue;
            if (waitForTarget && !target.canInteract(mc)) return false;
            sentAny |= target.interact(mc);
        }
        return sentAny;
    }

    public boolean canExecuteNow(Minecraft mc) {
        if (mc == null || mc.player == null) return false;

        if (targetMode == TargetMode.LAST_TARGET) {
            AutismContainerTarget target = AutismSharedState.get().getLastContainerTarget();
            return target != null && target.canInteract(mc);
        }

        boolean hasTarget = false;
        for (String ref : entityTargets) {
            if (ref == null || ref.isBlank()) continue;
            AutismContainerTarget target = AutismContainerTarget.forEntityRef(ref);
            if (target == null || !target.canInteract(mc)) return false;
            hasTarget = true;
        }
        return hasTarget;
    }

    public void captureCurrentLookTarget(Minecraft mc) {
        if (mc == null) return;

        if (mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() != null) {
            targetMode = TargetMode.ENTITY;
            String ref = AutismContainerTarget.toSpecificEntityRef(entityHit.getEntity());
            if (!ref.isBlank() && !entityTargets.contains(ref)) entityTargets.add(ref);
        }
    }

    private String entityTargetLabel(String ref) {
        if (ref == null || ref.isBlank()) return "(none)";
        if (ref.startsWith("~")) {
            String[] parts = ref.split("~", 4);
            String name = parts.length >= 4 ? parts[3] : "?";
            String type = parts.length >= 3 ? AutismRegistryLabels.entity(parts[2]) : "?";
            return name.isBlank() ? type : name + " (" + type + ")";
        }
        return AutismRegistryLabels.entity(ref);
    }

    @Override public boolean isWaitForGuiBefore() { return waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { this.waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter() { return waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { this.waitForGuiAfter = v; }
    @Override public String getWaitGuiName() { return guiName; }
    @Override public void setWaitGuiName(String name) { this.guiName = name; }

    @Override public MacroActionType getType() { return MacroActionType.INTERACT_ENTITY; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
    @Override public String getIcon() { return "IE"; }

    @Override
    public String getDisplayName() {
        String targetLabel;
        if (targetMode == TargetMode.LAST_TARGET) {
            targetLabel = "Last Target";
        } else if (entityTargets.isEmpty()) {
            targetLabel = "(none)";
        } else if (entityTargets.size() == 1) {
            targetLabel = entityTargetLabel(entityTargets.get(0));
        } else {
            targetLabel = entityTargets.size() + " entities";
        }
        return "Interact Entity " + targetLabel + WaitsForGui.timingLabel(this);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "INTERACT_ENTITY");
        tag.putString("targetMode", targetMode.name());

        ListTag list = new ListTag();
        for (String ref : entityTargets) {
            if (ref != null && !ref.isBlank()) list.add(StringTag.valueOf(ref));
        }
        tag.put("entityTargets", list);
        tag.putString("entityTarget", entityTargets.isEmpty() ? "" : entityTargets.get(0));
        tag.putBoolean("waitForTarget", waitForTarget);
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putString("guiName", guiName);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {

        String mode = tag.getStringOr("targetMode", "ENTITY");
        if ("BLOCK".equals(mode)) mode = "ENTITY";
        try {
            targetMode = TargetMode.valueOf(mode);
        } catch (IllegalArgumentException ignored) {
            targetMode = TargetMode.ENTITY;
        }
        entityTargets.clear();
        if (tag.contains("entityTargets")) {
            ListTag list = tag.getList("entityTargets").orElse(new ListTag());
            for (Tag element : list) {
                String value = element.asString().orElse("");
                if (value != null && !value.isBlank() && !entityTargets.contains(value)) {
                    entityTargets.add(value);
                }
            }
        }

        if (entityTargets.isEmpty()) {
            String single = tag.getStringOr("entityTarget", "");
            if (single != null && !single.isBlank()) entityTargets.add(single);
        }
        waitForTarget = tag.getBooleanOr("waitForTarget", true);
        waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
        guiName = tag.getStringOr("guiName", "");
        enabled = tag.getBooleanOr("enabled", true);
    }
}
