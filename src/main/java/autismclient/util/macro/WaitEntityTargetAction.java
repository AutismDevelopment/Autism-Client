package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public class WaitEntityTargetAction implements MacroAction {
    public enum EntityCondition { LOOKING_AT, WITHIN_REACH, MOUNTED_IN, NEARBY }

    public EntityCondition condition = EntityCondition.LOOKING_AT;
    public String entityId = "";
    public double range = 5.0;
    public boolean containerEntitiesOnly = false;
    public int timeoutMs = 0;
    public boolean listenDuringPreviousAction = false;

    @Override public void execute(Minecraft mc) {}
    @Override public MacroActionType getType() { return MacroActionType.WAIT_ENTITY_TARGET; }

    public boolean matches(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return false;
        MacroTemplate.Resolution entityResolution = MacroVariables.resolve(entityId, mc);
        if (!entityResolution.success()) return false;
        String expectedEntity = entityResolution.value();
        return switch (condition) {
            case LOOKING_AT -> matchesEntity(mc.crosshairPickEntity, expectedEntity);
            case MOUNTED_IN -> matchesEntity(mc.player.getVehicle(), expectedEntity);
            case WITHIN_REACH, NEARBY -> {
                boolean found = false;
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity == mc.player) continue;
                    if (mc.player.distanceTo(entity) <= range && matchesEntity(entity, expectedEntity)) { found = true; break; }
                }
                yield found;
            }
        };
    }

    private boolean matchesEntity(Entity entity, String expectedEntity) {
        if (entity == null) return false;
        String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (containerEntitiesOnly && !(type.contains("boat") || type.contains("minecart") || type.contains("llama") || type.contains("chest"))) return false;
        if (expectedEntity == null || expectedEntity.isBlank()) return true;
        return type.equalsIgnoreCase(expectedEntity) || entity.getStringUUID().equalsIgnoreCase(expectedEntity)
            || entity.getName().getString().equalsIgnoreCase(expectedEntity);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "WAIT_ENTITY_TARGET");
        tag.putString("condition", condition.name());
        tag.putString("entityId", entityId);
        tag.putDouble("range", range);
        tag.putBoolean("containerEntitiesOnly", containerEntitiesOnly);
        tag.putInt("timeoutMs", timeoutMs);
        MacroWaitOptions.write(tag, this);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        condition = MacroStringList.enumValue(EntityCondition.class, tag.getStringOr("condition", "LOOKING_AT"), EntityCondition.LOOKING_AT);
        entityId = tag.getStringOr("entityId", "");
        range = tag.getDoubleOr("range", 5.0);
        containerEntitiesOnly = tag.getBooleanOr("containerEntitiesOnly", false);
        timeoutMs = tag.getIntOr("timeoutMs", 0);
        MacroWaitOptions.read(tag, this);
    }

    @Override public String getDisplayName() { return "Wait entity " + condition; }
    @Override public String getIcon() { return "E"; }
}
