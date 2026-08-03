package autismclient.modules;

import autismclient.api.module.ChoiceSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AirJumpModule extends Module {

    private boolean doubleJump = true;

    public AirJumpModule() {
        super("air-jump", "AirJump", ModuleCategory.MOVEMENT, "Jump in mid air.");
        add(new ChoiceSetting("mode", "Mode", "JumpFreely", "JumpFreely", "DoubleJump", "GhostBlock").build());
    }

    @Override
    public void preMovementTick() {

        if (MC.player != null && MC.player.onGround()) doubleJump = true;
    }

    private boolean allowJump() {
        return "JumpFreely".equals(choice("mode"))
            || ("DoubleJump".equals(choice("mode")) && doubleJump);
    }

    public static boolean shouldAirJump() {
        Module module = ModuleRegistry.get("air-jump");
        return module instanceof AirJumpModule airJump && airJump.isEnabled() && airJump.allowJump();
    }

    public static void onJumpFromGround(LivingEntity entity) {
        if (MC == null || MC.player == null || entity != MC.player) return;
        Module module = ModuleRegistry.get("air-jump");
        if (module instanceof AirJumpModule airJump && airJump.isEnabled()
            && airJump.doubleJump && !MC.player.onGround()) {
            airJump.doubleJump = false;
        }
    }

    public static VoxelShape ghostBlockShape(VoxelShape original, BlockPos pos) {
        if (MC == null || MC.player == null || MC.options == null) return original;
        Module module = ModuleRegistry.get("air-jump");
        if (module instanceof AirJumpModule airJump && airJump.isEnabled()
            && "GhostBlock".equals(airJump.choice("mode"))
            && pos.getY() < MC.player.blockPosition().getY()
            && ghostJumpHeld()) {
            return Shapes.block();
        }
        return original;
    }

    private static volatile long lastJumpDownAtMs;

    public static boolean ghostJumpHeld() {
        if (MC == null || MC.options == null) return false;
        long now = System.currentTimeMillis();
        if (MC.options.keyJump.isDown()) {
            lastJumpDownAtMs = now;
            return true;
        }
        return now - lastJumpDownAtMs <= ghostGraceMs();
    }

    private static long ghostGraceMs() {
        if (MC.player == null) return 300L;
        net.minecraft.world.phys.Vec3 v = MC.player.getDeltaMovement();
        double speed = Math.max(Math.hypot(v.x, v.z), Math.abs(v.y));
        if (speed <= 0.22) return 300L;
        if (speed >= 0.80) return 900L;
        return 300L + (long) ((speed - 0.22) / 0.58 * 600.0);
    }
}
