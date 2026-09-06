package autismclient.util.macro;

import autismclient.util.AutismRegistryLabels;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LookAtBlockAction implements MacroAction {
    public enum TargetMode { SPECIFIC, BLOCK, ENTITY }

    public int blockX = 0;
    public int blockY = 0;
    public int blockZ = 0;
    public TargetMode targetMode = TargetMode.SPECIFIC;
    public List<String> blockIds = new ArrayList<>();
    public List<String> entityIds = new ArrayList<>();
    public double searchRadius = 16.0;
    public boolean smooth;
    public int smoothness = 6;
    public boolean waitForCompletion = true;
    private boolean enabled = true;

    public LookAtBlockAction() {}

    public LookAtBlockAction(int x, int y, int z) {
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
        this.targetMode = TargetMode.SPECIFIC;
    }

    public static String toSpecificEntityEntry(Entity entity) {
        if (entity == null) return "";
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        String displayName = entity.getDisplayName().getString().replaceAll("§.", "").trim();
        return "~" + entity.getStringUUID() + "~" + typeId + "~" + displayName;
    }

    public double getRotationStep() {
        return RotateAction.smoothnessToRotationStep(smoothness);
    }

    public RotationTarget resolveRotationTarget(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return null;
        return switch (targetMode) {
            case SPECIFIC -> rotationTo(mc.player.getEyePosition(), new Vec3(blockX + 0.5, blockY + 0.5, blockZ + 0.5));
            case BLOCK -> resolveNearestBlockTarget(mc);
            case ENTITY -> resolveNearestEntityTarget(mc);
        };
    }

    @Override
    public void execute(Minecraft mc) {
        if (mc == null || mc.player == null) return;
        RotationTarget target = resolveRotationTarget(mc);
        if (target == null || smooth) return;
        mc.player.setYRot(target.yaw());
        mc.player.setXRot(target.pitch());
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putInt("blockX", blockX);
        tag.putInt("blockY", blockY);
        tag.putInt("blockZ", blockZ);
        tag.putString("targetMode", targetMode.name());
        ListTag blocks = new ListTag();
        for (String id : blockIds) blocks.add(StringTag.valueOf(id));
        tag.put("blockIds", blocks);
        ListTag entities = new ListTag();
        for (String id : entityIds) entities.add(StringTag.valueOf(id));
        tag.put("entityIds", entities);
        tag.putDouble("searchRadius", searchRadius);
        tag.putBoolean("smooth", smooth);
        tag.putInt("smoothness", RotateAction.clampSmoothness(smoothness));
        tag.putBoolean("waitForCompletion", waitForCompletion);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("blockX")) blockX = tag.getIntOr("blockX", 0);
        if (tag.contains("blockY")) blockY = tag.getIntOr("blockY", 0);
        if (tag.contains("blockZ")) blockZ = tag.getIntOr("blockZ", 0);
        if (tag.contains("targetMode")) {
            try {
                targetMode = TargetMode.valueOf(tag.getStringOr("targetMode", TargetMode.SPECIFIC.name()));
            } catch (Exception ignored) {
                targetMode = TargetMode.SPECIFIC;
            }
        } else {
            targetMode = TargetMode.SPECIFIC;
        }
        blockIds.clear();
        if (tag.contains("blockIds")) {
            ListTag blocks = tag.getList("blockIds").orElse(new ListTag());
            for (Tag el : blocks) {
                String value = el.asString().orElse("");
                if (!value.isEmpty()) blockIds.add(value);
            }
        }
        entityIds.clear();
        if (tag.contains("entityIds")) {
            ListTag entities = tag.getList("entityIds").orElse(new ListTag());
            for (Tag el : entities) {
                String value = el.asString().orElse("");
                if (!value.isEmpty()) entityIds.add(value);
            }
        }
        searchRadius = Math.max(1.0, tag.getDoubleOr("searchRadius", 16.0));
        smooth = tag.getBooleanOr("smooth", false);
        smoothness = RotateAction.clampSmoothness(tag.getIntOr("smoothness", 6));
        waitForCompletion = tag.getBooleanOr("waitForCompletion", true);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.LOOK_AT_BLOCK;
    }

    @Override
    public String getDisplayName() {
        String targetLabel = switch (targetMode) {
            case SPECIFIC -> "(" + blockX + ", " + blockY + ", " + blockZ + ")";
            case BLOCK -> formatBlockTargets();
            case ENTITY -> formatEntityTargets();
        };
        String suffix = smooth ? " (Smooth)" : "";
        if (!waitForCompletion) suffix += " [NoWait]";
        return "Look At " + targetLabel + suffix;
    }

    @Override
    public String getIcon() {
        return "LAB";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    private RotationTarget resolveNearestBlockTarget(Minecraft mc) {
        if (mc.player == null || mc.level == null || blockIds.isEmpty()) return null;

        Vec3 eyePos = mc.player.getEyePosition();
        BlockPos center = mc.player.blockPosition();
        int radius = Math.max(1, Mth.ceil(searchRadius));
        double bestSq = searchRadius * searchRadius;
        BlockPos bestPos = null;
        Set<String> wanted = new HashSet<>(blockIds);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    Vec3 targetPos = Vec3.atCenterOf(mutable);
                    double distSq = eyePos.distanceToSqr(targetPos);
                    if (distSq > bestSq) continue;

                    Block block = mc.level.getBlockState(mutable).getBlock();
                    String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                    if (!wanted.contains(id)) continue;

                    bestSq = distSq;
                    bestPos = mutable.immutable();
                }
            }
        }

        return bestPos == null ? null : rotationTo(eyePos, Vec3.atCenterOf(bestPos));
    }

    private RotationTarget resolveNearestEntityTarget(Minecraft mc) {
        if (mc.player == null || mc.level == null || entityIds.isEmpty()) return null;

        Set<String> typeIds = new HashSet<>();
        Set<String> specificUuids = new HashSet<>();
        for (String entry : entityIds) {
            if (entry == null || entry.isBlank()) continue;
            if (entry.startsWith("~")) {
                String[] parts = entry.split("~", 4);
                if (parts.length >= 2 && !parts[1].isBlank()) specificUuids.add(parts[1]);
            } else {
                typeIds.add(entry);
            }
        }

        double maxSq = searchRadius * searchRadius;
        double bestSq = maxSq;
        Entity bestEntity = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null || entity == mc.player || !entity.isAlive()) continue;
            double distSq = mc.player.distanceToSqr(entity);
            if (distSq > bestSq) continue;
            if (!matchesEntity(entity, typeIds, specificUuids)) continue;
            bestSq = distSq;
            bestEntity = entity;
        }

        if (bestEntity == null) return null;
        Vec3 targetPos = bestEntity.getBoundingBox().getCenter();
        return rotationTo(mc.player.getEyePosition(), targetPos);
    }

    private boolean matchesEntity(Entity entity, Set<String> typeIds, Set<String> specificUuids) {
        if (specificUuids.contains(entity.getStringUUID())) return true;
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return typeIds.contains(typeId);
    }

    private String formatBlockTargets() {
        if (blockIds.isEmpty()) return "Block (none)";
        String first = AutismRegistryLabels.block(blockIds.get(0));
        return blockIds.size() == 1 ? "Nearest " + first : "Nearest " + first + " (+" + (blockIds.size() - 1) + ")";
    }

    private String formatEntityTargets() {
        if (entityIds.isEmpty()) return "Entity (none)";
        String firstEntry = entityIds.get(0);
        String first;
        if (firstEntry.startsWith("~")) {
            String[] parts = firstEntry.split("~", 4);
            String rawName = parts.length >= 4 ? parts[3] : "";
            String type = parts.length >= 3 ? AutismRegistryLabels.entity(parts[2]) : "?";
            first = rawName == null || rawName.isBlank() ? type : rawName + " (" + type + ")";
        } else {
            first = AutismRegistryLabels.entity(firstEntry);
        }
        return entityIds.size() == 1 ? "Nearest " + first : "Nearest " + first + " (+" + (entityIds.size() - 1) + ")";
    }

    private static RotationTarget rotationTo(Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        double dist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, dist));
        return new RotationTarget(yaw, pitch);
    }

    public record RotationTarget(float yaw, float pitch) {}
}
