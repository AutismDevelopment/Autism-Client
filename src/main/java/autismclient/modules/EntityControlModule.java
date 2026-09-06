

package autismclient.modules;
import autismclient.api.module.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public final class EntityControlModule extends Module {

    private static final String[] DEFAULT_ENTITY_CANDIDATES = {
        "minecraft:pig", "minecraft:strider", "minecraft:horse", "minecraft:donkey", "minecraft:mule",
        "minecraft:skeleton_horse", "minecraft:zombie_horse", "minecraft:camel", "minecraft:happy_ghast",
        "minecraft:oak_boat", "minecraft:spruce_boat", "minecraft:birch_boat", "minecraft:jungle_boat",
        "minecraft:acacia_boat", "minecraft:cherry_boat", "minecraft:dark_oak_boat", "minecraft:pale_oak_boat",
        "minecraft:mangrove_boat", "minecraft:bamboo_raft",
        "minecraft:oak_chest_boat", "minecraft:spruce_chest_boat", "minecraft:birch_chest_boat",
        "minecraft:jungle_chest_boat", "minecraft:acacia_chest_boat", "minecraft:cherry_chest_boat",
        "minecraft:dark_oak_chest_boat", "minecraft:pale_oak_chest_boat", "minecraft:mangrove_chest_boat",
        "minecraft:bamboo_chest_raft"};

    private static EntityControlModule instance;

    private final Set<String> selectedEntities = new HashSet<>();
    private String selectedEntitiesValue = "";
    private int antiKickTicks;
    private double lastPacketY = Double.MAX_VALUE;
    private boolean restorePacketPending;
    private boolean sendingSyntheticPacket;

    public EntityControlModule() {
        super("entity-control", "EntityControl", ModuleCategory.MOVEMENT, "Controls selected rideable entities.");
        instance = this;

        add(RegistryListSetting.entityTypes("entities", "Entities", defaultEntities())
            .group("Control").description("Choose controlled mounts.").build());
        add(new BoolSetting("spoof-saddle", "Spoof Saddle", true)
            .group("Control").description("Control without saddles.").build());
        add(new BoolSetting("max-jump", "Max Jump", true)
            .group("Control").description("Always charge fully.").build());
        add(new BoolSetting("lock-yaw", "Lock Yaw", true)
            .group("Control").description("Match your view.").build());
        add(new BoolSetting("cancel-server-packets", "Cancel Server Packets", true)
            .group("Control").description("Ignore server corrections.").build());

        add(new BoolSetting("speed", "Speed", false)
            .group("Speed").description("Boost mount speed.").build());
        add(new DoubleSetting("horizontal-speed", "Horizontal Speed", 10.0, 0.0, 100.0, 0.5)
            .sliderRange(0.0, 50.0).group("Speed")
            .visibleWhen(() -> bool("speed")).description("Sets horizontal speed.").build());
        add(new BoolSetting("only-on-ground", "Only Ground", false)
            .group("Speed").visibleWhen(() -> bool("speed")).description("Require ground contact.").build());
        add(new BoolSetting("in-water", "In Water", true)
            .group("Speed").visibleWhen(() -> bool("speed")).description("Allow water speed.").build());

        add(new BoolSetting("fly", "Fly", false)
            .group("Flight").description("Enable mount flight.").build());
        add(new DoubleSetting("vertical-speed", "Vertical Speed", 6.0, 0.0, 100.0, 0.5)
            .sliderRange(0.0, 20.0).group("Flight")
            .visibleWhen(() -> bool("fly")).description("Sets vertical speed.").build());
        add(new DoubleSetting("fall-speed", "Fall Speed", 0.0, 0.0, 100.0, 0.25)
            .sliderRange(0.0, 20.0).group("Flight")
            .visibleWhen(() -> bool("fly")).description("Sets downward drift.").build());
        add(new BoolSetting("anti-kick", "Anti Kick", true)
            .group("Flight").visibleWhen(() -> bool("fly")).description("Reduce flight kicks.").build());
        add(new IntSetting("anti-kick-delay", "Anti Kick Delay", 40, 1, 80, 1)
            .group("Flight").visibleWhen(() -> bool("fly") && bool("anti-kick"))
            .description("Sets anti-kick interval.").build());
    }

    @Override
    public void onEnable() {
        resetAntiKick();
        refreshSelectedEntities();
    }

    @Override
    public void onDisable() {
        resetAntiKick();
    }

    @Override
    public void onGameLeft() {
        resetAntiKick();
    }

    @Override
    protected void onOptionValueChanged(String optionId) {
        if ("entities".equals(optionId)) refreshSelectedEntities();
        if ("anti-kick-delay".equals(optionId)) antiKickTicks = integer("anti-kick-delay");
    }

    @Override
    protected void onSettingsReset() {
        refreshSelectedEntities();
        resetAntiKick();
    }

    @Override
    public void preMovementTick() {
        if (MC.player == null || MC.getConnection() == null) return;

        if (restorePacketPending) {
            Entity vehicle = MC.player.getVehicle();
            if (isControlledVehicle(vehicle)) {
                sendSynthetic(new ServerboundMoveVehiclePacket(
                    new Vec3(vehicle.getX(), lastPacketY, vehicle.getZ()),
                    vehicle.getYRot(), vehicle.getXRot(), vehicle.onGround()));
            }
            restorePacketPending = false;
        }

        if (antiKickTicks > 0) antiKickTicks--;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (sendingSyntheticPacket || !bool("fly") || !bool("anti-kick")) return false;
        if (!(packet instanceof ServerboundMoveVehiclePacket movePacket)) return false;

        Entity vehicle = MC.player == null ? null : MC.player.getVehicle();
        if (!isControlledVehicle(vehicle) || vehicle.isFlyingVehicle() || !isOnAir(vehicle)) {
            lastPacketY = movePacket.position().y;
            return false;
        }

        double currentY = movePacket.position().y;
        if (antiKickTicks <= 0 && !restorePacketPending && shouldFlyDown(currentY)) {
            double baseline = lastPacketY == Double.MAX_VALUE ? currentY : lastPacketY;
            ServerboundMoveVehiclePacket lowered = new ServerboundMoveVehiclePacket(
                new Vec3(movePacket.position().x, baseline - 0.03130D, movePacket.position().z),
                movePacket.yRot(), movePacket.xRot(), movePacket.onGround());
            sendSynthetic(lowered);
            restorePacketPending = true;
            antiKickTicks = integer("anti-kick-delay");
            lastPacketY = currentY;
            return true;
        }

        lastPacketY = currentY;
        return false;
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        return bool("cancel-server-packets") && packet instanceof ClientboundMoveVehiclePacket;
    }

    static Vec3 modifyVehicleMovement(Entity vehicle, MoverType type, Vec3 movement) {
        EntityControlModule module = instance;
        if (!isActive(module) || type != MoverType.SELF || !module.isControlledVehicle(vehicle)) return movement;

        double velocityX = movement.x;
        double velocityY = movement.y;
        double velocityZ = movement.z;

        if (module.bool("lock-yaw")) vehicle.setYRot(MC.player.getYRot());

        if (module.bool("speed")
            && (!module.bool("only-on-ground") || vehicle.onGround() || vehicle.isFlyingVehicle())
            && (module.bool("in-water") || !vehicle.isInWater())) {
            Vec3 horizontal = horizontalVelocity(module.decimal("horizontal-speed"));
            velocityX = horizontal.x;
            velocityZ = horizontal.z;
        }

        if (module.bool("fly")) {
            velocityY = -module.decimal("fall-speed") / 20.0;
            if (MC.options.keyJump.isDown()) velocityY += module.decimal("vertical-speed") / 20.0;
            if (MC.options.keySprint.isDown()) velocityY -= module.decimal("vertical-speed") / 20.0;
        }

        return new Vec3(velocityX, velocityY, velocityZ);
    }

    public static boolean shouldLockBoatYaw() {
        EntityControlModule module = instance;
        return isActive(module) && module.bool("lock-yaw")
            && module.isControlledVehicle(MC.player == null ? null : MC.player.getVehicle());
    }

    public static boolean shouldSpoofSaddle(Mob mob) {
        EntityControlModule module = instance;
        return isActive(module) && module.bool("spoof-saddle") && module.isSelected(mob);
    }

    public static boolean shouldMaxJump() {
        EntityControlModule module = instance;
        Entity vehicle = MC.player == null ? null : MC.player.getVehicle();
        return isActive(module) && module.bool("max-jump") && module.isControlledVehicle(vehicle);
    }

    public static boolean shouldCancelRidingJump() {
        EntityControlModule module = instance;
        Entity vehicle = MC.player == null ? null : MC.player.getVehicle();
        return isActive(module) && module.bool("fly") && vehicle instanceof PlayerRideableJumping
            && module.isControlledVehicle(vehicle);
    }

    public static boolean shouldControlSteer(Entity entity) {
        EntityControlModule module = instance;
        return isActive(module) && entity != null
            && MC.player != null && MC.player.getVehicle() == entity
            && MC.player == entity.getFirstPassenger()
            && module.isSelected(entity);
    }

    private static String defaultEntities() {
        StringBuilder out = new StringBuilder();
        for (String id : DEFAULT_ENTITY_CANDIDATES) {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed != null && BuiltInRegistries.ENTITY_TYPE.getOptional(parsed).isPresent()) {
                if (!out.isEmpty()) out.append('|');
                out.append(id);
            }
        }
        return out.toString();
    }

    private static boolean isActive(EntityControlModule module) {
        return module != null && module.isEnabled() && !PackHideState.isHardLocked() && MC.player != null;
    }

    private boolean isControlledVehicle(Entity vehicle) {
        return vehicle != null && MC.player != null && MC.player.getVehicle() == vehicle
            && vehicle.getControllingPassenger() == MC.player && isSelected(vehicle);
    }

    private boolean isSelected(Entity entity) {
        if (entity == null) return false;
        refreshSelectedEntities();
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && selectedEntities.contains(id.toString());
    }

    private void refreshSelectedEntities() {
        String current = value("entities");
        if (current.equals(selectedEntitiesValue)) return;
        selectedEntitiesValue = current;
        selectedEntities.clear();
        selectedEntities.addAll(list("entities"));
    }

    private void resetAntiKick() {
        antiKickTicks = integer("anti-kick-delay");
        lastPacketY = Double.MAX_VALUE;
        restorePacketPending = false;
        sendingSyntheticPacket = false;
    }

    private void sendSynthetic(ServerboundMoveVehiclePacket packet) {
        if (MC.getConnection() == null) return;
        sendingSyntheticPacket = true;
        try {
            MC.getConnection().send(packet);
        } finally {
            sendingSyntheticPacket = false;
        }
    }

    private boolean shouldFlyDown(double currentY) {
        return currentY >= lastPacketY || lastPacketY - currentY < 0.03130D;
    }

    private static boolean isOnAir(Entity entity) {
        return entity.level().getBlockStates(entity.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0))
            .allMatch(BlockBehaviour.BlockStateBase::isAir);
    }

    private static Vec3 horizontalVelocity(double blocksPerSecond) {
        double speed = blocksPerSecond / 20.0;
        float forward = 0.0F;
        float sideways = 0.0F;
        if (MC.options.keyUp.isDown()) forward += 1.0F;
        if (MC.options.keyDown.isDown()) forward -= 1.0F;
        if (MC.options.keyLeft.isDown()) sideways += 1.0F;
        if (MC.options.keyRight.isDown()) sideways -= 1.0F;
        if (forward == 0.0F && sideways == 0.0F) return Vec3.ZERO;

        double length = Math.sqrt(forward * forward + sideways * sideways);
        forward /= (float) length;
        sideways /= (float) length;
        double yaw = Math.toRadians(MC.player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        return new Vec3((sideways * cos - forward * sin) * speed, 0.0,
            (forward * cos + sideways * sin) * speed);
    }
}
