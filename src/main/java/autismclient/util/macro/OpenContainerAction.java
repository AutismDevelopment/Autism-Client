package autismclient.util.macro;

import autismclient.util.AutismContainerTarget;
import autismclient.util.AutismRegistryLabels;
import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class OpenContainerAction implements MacroAction, WaitsForGui {
    public enum TargetMode { BLOCK, ENTITY, LAST_TARGET }

    public TargetMode targetMode = TargetMode.BLOCK;
    public BlockPos blockPos = BlockPos.ZERO;
    public String entityTarget = "";
    public List<String> entityTargets = new ArrayList<>();
    public boolean waitForTarget = true;
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = true;
    public String guiName = "";
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
        tryExecute(mc);
    }

    public boolean tryExecute(Minecraft mc) {
        if (mc == null || mc.player == null) return false;

        if (targetMode == TargetMode.ENTITY) {
            List<String> refs = effectiveEntityTargets();
            boolean sentAny = false;
            for (String ref : refs) {
                if (ref == null || ref.isBlank()) continue;
                AutismContainerTarget target = AutismContainerTarget.forEntityRef(ref);
                if (target == null) continue;
                if (waitForTarget && !target.canInteract(mc)) return false;
                sentAny |= target.interact(mc);
            }
            return sentAny;
        }

        AutismContainerTarget target = targetMode == TargetMode.BLOCK
            ? AutismContainerTarget.forBlock(blockPos)
            : AutismSharedState.get().getLastContainerTarget();

        return target != null && (!waitForTarget || target.canInteract(mc)) && target.interact(mc);
    }

    public boolean canExecuteNow(Minecraft mc) {
        if (mc == null || mc.player == null) return false;

        if (targetMode == TargetMode.ENTITY) {
            List<String> refs = effectiveEntityTargets();
            boolean hasTarget = false;
            for (String ref : refs) {
                if (ref == null || ref.isBlank()) continue;
                AutismContainerTarget target = AutismContainerTarget.forEntityRef(ref);
                if (target == null || !target.canInteract(mc)) return false;
                hasTarget = true;
            }
            return hasTarget;
        }

        AutismContainerTarget target = targetMode == TargetMode.BLOCK
            ? AutismContainerTarget.forBlock(blockPos)
            : AutismSharedState.get().getLastContainerTarget();
        return target != null && target.canInteract(mc);
    }

    public void captureCurrentLookTarget(Minecraft mc) {
        if (mc == null) return;
        if (mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() != null) {
            targetMode = TargetMode.ENTITY;
            entityTarget = AutismContainerTarget.toSpecificEntityRef(entityHit.getEntity());
            if (!entityTarget.isBlank() && !entityTargets.contains(entityTarget)) entityTargets.add(entityTarget);
        } else if (mc.hitResult instanceof BlockHitResult blockHit) {
            targetMode = TargetMode.BLOCK;
            blockPos = blockHit.getBlockPos();
            entityTarget = "";
            entityTargets.clear();
        }
    }

    public String entityTargetLabel() {
        return entityTargetLabel(entityTarget);
    }

    private static String entityTargetLabel(String targetRef) {
        if (targetRef == null || targetRef.isBlank()) return "(none)";
        if (targetRef.startsWith("~")) {
            String[] parts = targetRef.split("~", 4);
            String name = parts.length >= 4 ? parts[3] : "?";
            String type = parts.length >= 3 ? AutismRegistryLabels.entity(parts[2]) : "?";
            return name.isBlank() ? type : name + " (" + type + ")";
        }
        return AutismRegistryLabels.entity(targetRef);
    }

    @Override public boolean isWaitForGuiBefore() { return waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { this.waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter() { return waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { this.waitForGuiAfter = v; }
    @Override public String getWaitGuiName() { return guiName; }
    @Override public void setWaitGuiName(String name) { this.guiName = name; }

    @Override public MacroActionType getType() { return MacroActionType.OPEN_CONTAINER; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
    @Override public String getIcon() { return "OC"; }

    @Override
    public String getDisplayName() {
        String targetLabel = switch (targetMode) {
            case BLOCK -> blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ();
            case ENTITY -> {
                List<String> refs = effectiveEntityTargets();
                if (refs.isEmpty()) yield "(none)";
                if (refs.size() == 1) yield entityTargetLabel(refs.get(0));
                yield refs.size() + " entities";
            }
            case LAST_TARGET -> "Last Target";
        };
        String base = "Open Container " + targetLabel;
        return base + WaitsForGui.timingLabel(this);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "OPEN_CONTAINER");
        tag.putString("targetMode", targetMode.name());
        tag.putInt("x", blockPos.getX());
        tag.putInt("y", blockPos.getY());
        tag.putInt("z", blockPos.getZ());
        List<String> refs = effectiveEntityTargets();
        tag.putString("entityTarget", refs.isEmpty() ? "" : refs.get(0));
        ListTag entityTargets = new ListTag();
        for (String ref : refs) {
            if (ref != null && !ref.isBlank()) entityTargets.add(StringTag.valueOf(ref));
        }
        tag.put("entityTargets", entityTargets);
        tag.putBoolean("waitForTarget", waitForTarget);
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putString("guiName", guiName);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        int x = tag.getIntOr("x", 0), y = tag.getIntOr("y", 0), z = tag.getIntOr("z", 0);
        blockPos = new BlockPos(x, y, z);
        try {
            targetMode = TargetMode.valueOf(tag.getStringOr("targetMode", "BLOCK"));
        } catch (IllegalArgumentException ignored) {
            targetMode = TargetMode.BLOCK;
        }
        entityTargets.clear();
        if (tag.contains("entityTargets")) {
            ListTag entityTargets = tag.getList("entityTargets").orElse(new ListTag());
            for (Tag element : entityTargets) {
                String value = element.asString().orElse("");
                if (value != null && !value.isBlank() && !this.entityTargets.contains(value)) {
                    this.entityTargets.add(value);
                }
            }
        }
        entityTarget = tag.getStringOr("entityTarget", "");
        if (this.entityTargets.isEmpty() && entityTarget != null && !entityTarget.isBlank()) {
            this.entityTargets.add(entityTarget);
        } else if (!this.entityTargets.isEmpty()) {
            entityTarget = this.entityTargets.get(0);
        }
        waitForTarget = tag.getBooleanOr("waitForTarget", true);
        waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        waitForGuiAfter = WaitsForGui.loadAfter(tag, true);
        guiName = tag.getStringOr("guiName", "");
        enabled = tag.getBooleanOr("enabled", true);
    }

    private List<String> effectiveEntityTargets() {
        if (!entityTargets.isEmpty()) return entityTargets;
        if (entityTarget != null && !entityTarget.isBlank()) return List.of(entityTarget);
        return List.of();
    }
}
