package autismclient.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ModuleMovementUtil {
    private static final Minecraft MC = Minecraft.getInstance();
    private static volatile SprintState sprintState = SprintState.inactive(-1);
    private static volatile MovementState movementState = MovementState.inactive(-1);

    private ModuleMovementUtil() {
    }

    public static boolean shouldCancelNoFallBounce(Entity entity) {
        if (autismclient.util.multi.MultiPovModuleController.isControlling(entity)) {
            return autismclient.util.multi.MultiPovModuleController.cancelNoFallBounce(entity);
        }
        return movementState().noFallAntiBounce() && MC != null && entity == MC.player;
    }

    public static float speedTimerMultiplier() {
        if (autismclient.util.multi.MultiPilot.isActive()) return 1.0F;
        float multiplier = 1.0f;
        for (Module module : ModuleRegistry.activeModules()) {
            if (!module.shouldApplySpeedTimer()) continue;
            multiplier = Math.max(multiplier, module.speedTimerMultiplier());
        }
        return Math.max(0.01f, Math.min(10.0f, multiplier));
    }

    public static float flightFlyingSpeed(Player player) {
        if (autismclient.util.multi.MultiPilot.isActive()) {
            return autismclient.util.multi.MultiPovModuleController.flyingSpeed(player);
        }
        BuiltinModules.FlightModule flight = movementState().flight();
        if (flight != null) return flight.getFlyingSpeed();
        return -1.0f;
    }

    public static boolean flightNoSneak(Player player) {
        if (autismclient.util.multi.MultiPilot.isActive()) {
            return autismclient.util.multi.MultiPovModuleController.flightNoSneak(player);
        }
        BuiltinModules.FlightModule flight = movementState().flight();
        return flight != null && flight.noSneak();
    }

    public static boolean sprintKeepsRunning() {
        return sprintIsOmnidirectional();
    }

    public static boolean sprintIsOmnidirectional() {
        return sprintState().omnidirectional();
    }

    public static boolean sprintIgnoresCollision() {
        return sprintState().ignoreCollision();
    }

    public static boolean sprintIgnoresBlindness() {
        return sprintState().ignoreBlindness();
    }

    public static boolean sprintDecision(boolean original, boolean movementTick) {
        if (autismclient.util.multi.MultiPilot.isActive()) return original;
        Module module = ModuleRegistry.get("sprint");
        if (!(module instanceof BuiltinModules.SprintModule sprint) || !sprint.isEnabled()) return original;
        return sprint.sprintDecision(original, movementTick);
    }

    public static boolean sprintShouldPrevent() {
        if (autismclient.util.multi.MultiPilot.isActive()) return false;
        Module module = ModuleRegistry.get("sprint");
        return module instanceof BuiltinModules.SprintModule sprint && sprint.isEnabled()
            && sprint.shouldPreventSprintPublic();
    }

    public static boolean sprintNetworkAllowed(boolean original) {
        if (autismclient.util.multi.MultiPilot.isActive()) return original;
        if (AntiHungerModule.noSprintRequested()) return false;
        return original;
    }

    public static boolean sprintJumpUsesMovementYaw() {
        Module module = ModuleRegistry.get("sprint");
        return module instanceof BuiltinModules.SprintModule sprint && sprint.isEnabled()
            && sprint.jumpUsesMovementYaw();
    }

    public static float movementDirectionYaw(net.minecraft.client.player.LocalPlayer player) {
        net.minecraft.world.entity.player.Input keys = player.input.keyPresses;
        float yaw = player.getYRot();
        float multiplier;
        if (keys.backward() && !keys.forward()) {
            yaw += 180.0F;
            multiplier = -0.5F;
        } else if (keys.forward() && !keys.backward()) {
            multiplier = 0.5F;
        } else {
            multiplier = 1.0F;
        }
        if (keys.left() && !keys.right()) yaw -= 90.0F * multiplier;
        if (keys.right() && !keys.left()) yaw += 90.0F * multiplier;
        return yaw;
    }

    public static boolean sprintModuleEnabled() {
        Module module = ModuleRegistry.get("sprint");
        return module instanceof BuiltinModules.SprintModule sprint && sprint.isEnabled();
    }

    private static SprintState sprintState() {
        int revision = ModuleRegistry.revision();
        SprintState snapshot = sprintState;
        if (snapshot.revision() == revision) return snapshot;
        Module module = ModuleRegistry.get("sprint");
        if (!(module instanceof BuiltinModules.SprintModule sprint) || !sprint.isEnabled()) {
            snapshot = SprintState.inactive(revision);
        } else {
            snapshot = new SprintState(
                revision,
                sprint.omnidirectional(),
                sprint.ignoreCollision(),
                sprint.ignoreBlindness()
            );
        }
        sprintState = snapshot;
        return snapshot;
    }

    private static MovementState movementState() {
        int revision = ModuleRegistry.revision();
        MovementState snapshot = movementState;
        if (snapshot.revision() == revision) return snapshot;

        Module flightModule = ModuleRegistry.get("flight");
        BuiltinModules.FlightModule flight =
            flightModule instanceof BuiltinModules.FlightModule typed && typed.isEnabled() ? typed : null;

        Module speedModule = ModuleRegistry.get("speed");
        BuiltinModules.SpeedModule speed =
            speedModule instanceof BuiltinModules.SpeedModule typed && typed.isEnabled() ? typed : null;

        Module noFall = ModuleRegistry.get("no-fall");
        boolean noFallAntiBounce = noFall != null && noFall.isEnabled() && Boolean.parseBoolean(noFall.value("anti-bounce"));

        snapshot = new MovementState(revision, flight, speed, noFallAntiBounce);
        movementState = snapshot;
        return snapshot;
    }

    public static void preMovementTick() {
        if (autismclient.util.multi.MultiPilot.isActive()) return;
        if (autismclient.util.multi.PacketTeleportController.ownsMainMovement()) return;
        if (!autismclient.util.AutismRuntimeActivity.has(autismclient.util.AutismRuntimeActivity.PRE_MOVEMENT)) return;
        ModuleRegistry.preMovementTick();
    }

    public static Vec3 onPlayerMove(Entity entity, MoverType type, Vec3 movement) {
        if (autismclient.util.multi.MultiPovModuleController.isControlling(entity)) {
            return autismclient.util.multi.MultiPovModuleController.modifyMovement(entity, type, movement);
        }
        if (autismclient.util.multi.PacketTeleportController.ownsMainMovement() && MC.player != null
            && (entity == MC.player || entity == MC.player.getVehicle())) return Vec3.ZERO;
        if (entity != MC.player) {
            if (MC.player == null || entity != MC.player.getVehicle() || PackHideState.isActive()) return movement;
            Vec3 adjusted = EntityControlModule.modifyVehicleMovement(entity, type, movement);
            adjusted = BoatFlyModule.modifyVehicleMovement(entity, type, adjusted);
            if (type == MoverType.SELF && adjusted != movement) entity.setDeltaMovement(adjusted);
            return adjusted;
        }
        if (autismclient.util.multi.MultiPilot.isActive()) return movement;
        if (!autismclient.util.AutismRuntimeActivity.has(autismclient.util.AutismRuntimeActivity.MOVEMENT)) return movement;
        Vec3 adjusted = ModuleRegistry.onPlayerMove(type, movement);
        if (type == MoverType.SELF && adjusted != movement) {
            MC.player.setDeltaMovement(adjusted);
        }
        return adjusted;
    }

    public static void applySpeedAfterLiquidTravel(Entity entity) {
        if (autismclient.util.multi.PacketTeleportController.ownsMainMovement() && MC.player != null
            && (entity == MC.player || entity == MC.player.getVehicle())) return;
        if (autismclient.util.multi.MultiPovModuleController.isControlling(entity)) {
            Vec3 movement = entity.getDeltaMovement();
            Vec3 adjusted = autismclient.util.multi.MultiPovModuleController.afterLiquidTravel(entity, movement);
            if (adjusted != null && adjusted != movement) entity.setDeltaMovement(adjusted);
            return;
        }
        if (entity != MC.player || PackHideState.isActive()) return;
        MovementState state = movementState();
        BuiltinModules.SpeedModule speed = state.speed();
        if (speed == null) return;
        Vec3 movement = entity.getDeltaMovement();
        Vec3 adjusted = speed.afterLiquidTravel(movement);
        if (adjusted != null && adjusted != movement) entity.setDeltaMovement(adjusted);
    }

    private record SprintState(
        int revision,
        boolean omnidirectional,
        boolean ignoreCollision,
        boolean ignoreBlindness
    ) {
        static SprintState inactive(int revision) {
            return new SprintState(revision, false, false, false);
        }
    }

    private record MovementState(
        int revision,
        BuiltinModules.FlightModule flight,
        BuiltinModules.SpeedModule speed,
        boolean noFallAntiBounce
    ) {
        static MovementState inactive(int revision) {
            return new MovementState(revision, null, null, false);
        }
    }
}
