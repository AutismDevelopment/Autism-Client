package autismclient.util.macro;

import autismclient.util.AutismRegistryLabels;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class WaitForEntityAction implements MacroAction, MacroCaptureOutput {
    public enum CheckMode { RADIUS, LOOKING_AT, WITHIN_REACH, MOUNTED_IN, NEARBY }

    public List<String> entityIds = new ArrayList<>();

    public boolean mustBeLookingAt = false;
    public CheckMode checkMode = CheckMode.RADIUS;

    public boolean centerOnPlayer = true;

    public double radius = 6.0;
    public boolean containerEntitiesOnly = false;
    public int timeoutMs = 0;

    public double x = 0, y = 0, z = 0;
    public boolean listenDuringPreviousAction = false;
    public String saveAs = "";
    private boolean enabled = true;

    public WaitForEntityAction() {}

    @Override public void execute(Minecraft mc) {}

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        ListTag list = new ListTag();
        for (String id : entityIds) list.add(StringTag.valueOf(id));
        tag.put("entityIds", list);
        tag.putBoolean("mustBeLookingAt", mustBeLookingAt);
        tag.putBoolean("centerOnPlayer", centerOnPlayer);
        tag.putDouble("radius", radius);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putBoolean("containerEntitiesOnly", containerEntitiesOnly);
        tag.putInt("timeoutMs", timeoutMs);
        tag.putBoolean("enabled", enabled);
        tag.putString("checkMode", checkMode.name());
        MacroWaitOptions.write(tag, this);
        tag.putString("saveAs", saveAs);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        entityIds.clear();
        if (tag.contains("entityIds")) {
            ListTag list = tag.getList("entityIds").orElse(new ListTag());
            for (Tag el : list) {
                String s = el.asString().orElse("");
                if (!s.isEmpty()) entityIds.add(s);
            }
        } else if (tag.contains("entityName")) {

            String oldName = tag.getStringOr("entityName", "");
            if (!oldName.isEmpty()) {

                String tryId = "minecraft:" + oldName.toLowerCase().replace(" ", "_");
                try {
                    net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(tryId);
                    if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                        entityIds.add(tryId);
                    }
                } catch (Exception ignored) {  }
            }
        }
        mustBeLookingAt = tag.getBooleanOr("mustBeLookingAt", false);
        if (tag.contains("checkMode")) {
            try { checkMode = CheckMode.valueOf(tag.getStringOr("checkMode", "RADIUS")); } catch (Exception e) { checkMode = CheckMode.RADIUS; }
        } else {
            checkMode = mustBeLookingAt ? CheckMode.LOOKING_AT : CheckMode.RADIUS;
        }
        centerOnPlayer = tag.getBooleanOr("centerOnPlayer", true);
        radius = tag.getDoubleOr("radius", 6.0);
        containerEntitiesOnly = tag.getBooleanOr("containerEntitiesOnly", false);
        timeoutMs = tag.getIntOr("timeoutMs", 0);
        x = tag.getDoubleOr("x", 0);
        y = tag.getDoubleOr("y", 0);
        z = tag.getDoubleOr("z", 0);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
        saveAs = tag.getStringOr("saveAs", "");
    }

    public void fromLegacyTargetTag(CompoundTag tag) {
        entityIds.clear();
        String entityId = tag.getStringOr("entityId", "");
        if (!entityId.isBlank()) entityIds.add(entityId);
        String condition = tag.getStringOr("condition", "LOOKING_AT");
        checkMode = switch (condition) {
            case "WITHIN_REACH" -> CheckMode.WITHIN_REACH;
            case "MOUNTED_IN" -> CheckMode.MOUNTED_IN;
            case "NEARBY" -> CheckMode.NEARBY;
            default -> CheckMode.LOOKING_AT;
        };
        radius = tag.getDoubleOr("range", 5.0);
        centerOnPlayer = true;
        containerEntitiesOnly = tag.getBooleanOr("containerEntitiesOnly", false);
        timeoutMs = tag.getIntOr("timeoutMs", 0);
        MacroWaitOptions.read(tag, this);
    }

    public boolean matchesEntityTarget(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return false;
        return switch (checkMode) {
            case LOOKING_AT -> entityMatches(mc.crosshairPickEntity);
            case MOUNTED_IN -> entityMatches(mc.player.getVehicle());
            case WITHIN_REACH -> {
                double reachSq = mc.player.blockInteractionRange() * mc.player.blockInteractionRange();
                boolean found = false;
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity == mc.player || entity.distanceToSqr(mc.player) > reachSq) continue;
                    if (entityMatches(entity)) { found = true; break; }
                }
                yield found;
            }
            case RADIUS, NEARBY -> {
                Vec3 center = centerOnPlayer
                    ? new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                    : new Vec3(x, y, z);
                double radiusSq = radius * radius;
                boolean found = false;
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity == mc.player || entity.distanceToSqr(center) > radiusSq) continue;
                    if (entityMatches(entity)) { found = true; break; }
                }
                yield found;
            }
        };
    }

    public boolean entityMatches(Entity entity) {
        if (entity == null) return false;
        String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (containerEntitiesOnly && !(type.contains("boat") || type.contains("minecart") || type.contains("llama") || type.contains("chest"))) {
            return false;
        }
        if (entityIds.isEmpty()) return true;
        for (String entry : entityIds) {
            if (entry == null || entry.isBlank()) continue;
            if (entry.startsWith("~")) {
                String[] parts = entry.split("~", 4);
                if (parts.length >= 2 && entity.getStringUUID().equalsIgnoreCase(parts[1])) return true;
                if (parts.length >= 3 && type.equalsIgnoreCase(parts[2])) return true;
            } else if (type.equalsIgnoreCase(entry)
                    || entity.getStringUUID().equalsIgnoreCase(entry)
                    || entity.getName().getString().equalsIgnoreCase(entry)) {
                return true;
            }
        }
        return false;
    }

    public Entity findMatchingEntity(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return null;
        if (checkMode == CheckMode.LOOKING_AT) return entityMatches(mc.crosshairPickEntity) ? mc.crosshairPickEntity : null;
        if (checkMode == CheckMode.MOUNTED_IN) return entityMatches(mc.player.getVehicle()) ? mc.player.getVehicle() : null;
        Vec3 center = centerOnPlayer ? mc.player.position() : new Vec3(x, y, z);
        double maxDistance = checkMode == CheckMode.WITHIN_REACH ? mc.player.blockInteractionRange() : radius;
        double maxDistanceSq = maxDistance * maxDistance;
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !entityMatches(entity)) continue;
            double distance = entity.distanceToSqr(center);
            if (distance <= maxDistanceSq && distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    @Override public MacroActionType getType() { return MacroActionType.WAIT_ENTITY; }

    @Override
    public String getDisplayName() {
        String first;
        if (entityIds.isEmpty()) {
            first = "any";
        } else {
            String e0 = entityIds.get(0);
            if (e0.startsWith("~")) {

                String[] p = e0.split("~", 4);
                String display = p.length >= 4 ? p[3] : "";
                String type = p.length >= 3 ? AutismRegistryLabels.entity(p[2]) : "?";
                first = "SPEC " + (display == null || display.isBlank() ? type : display);
            } else {
                first = AutismRegistryLabels.entity(e0);
            }
            if (entityIds.size() > 1) first += " (+" + (entityIds.size() - 1) + ")";
        }
        String modeStr = switch (checkMode) {
            case RADIUS -> "r=" + (int) radius;
            case LOOKING_AT -> "look";
            case WITHIN_REACH -> "reach";
            case MOUNTED_IN -> "mounted";
            case NEARBY -> "nearby " + (int) radius;
        };
        return "Wait Entity: " + first + " (" + modeStr + ")";
    }

    @Override public String getIcon() { return "ENT"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
    @Override public String getSaveAs() { return saveAs; }
    @Override public void setSaveAs(String name) { saveAs = name == null ? "" : name; }
}
