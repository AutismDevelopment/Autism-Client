package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.KeybindSetting;
import autismclient.util.AutismBindUtil;
import autismclient.util.multi.PacketTeleportController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class TpClickModule extends Module {
    private static final int COLOR_VALID = 0xFF46D160;
    private static final int COLOR_ADJUSTED = 0xFFFFB03B;
    private static final int COLOR_INVALID = 0xFFFF3B3B;

    private static final int MAX_CLIMB = 4;

    private static final int SEARCH_HORIZONTAL = 8;
    private static final int SEARCH_DOWN = 2;
    private static final int SEARCH_UP = 8;

    private AABB highlightBox;
    private boolean targetValid;
    private boolean adjustedTarget;
    private Vec3 destination;
    private boolean keyWasDown;

    TpClickModule() {
        super("tp-click", "TpClick", ModuleCategory.PLAYER, "Teleport where you click.");

        add(new IntSetting("reach", "Reach", 50, 10, 250, 5)
            .description("Maximum click teleport distance.").build());
        add(new KeybindSetting("click-key", "Click Key", AutismBindUtil.encodeMouseButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT))
            .description("Button that teleports you.").build());
        add(new BoolSetting("block-movement", "Block Movement", true)
            .description("Freeze movement keys while teleporting.").build());
        add(new BoolSetting("no-fall", "No Fall", true)
            .description("Prevent fall damage while teleporting.").build());
        add(new BoolSetting("anti-kick", "Anti Kick", true)
            .description("Prevent floating kicks while airborne.").build());
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onGameLeft() {
        reset();
    }

    private void reset() {
        highlightBox = null;
        targetValid = false;
        adjustedTarget = false;
        destination = null;
        keyWasDown = false;
    }

    public static boolean blocksMovement() {
        Module module = ModuleRegistry.get("tp-click");
        return module instanceof TpClickModule tpClick && tpClick.isEnabled() && tpClick.bool("block-movement");
    }

    public static boolean noFallActive() {
        Module module = ModuleRegistry.get("tp-click");
        return module instanceof TpClickModule tpClick && tpClick.bool("no-fall");
    }

    public static boolean antiKickActive() {
        Module module = ModuleRegistry.get("tp-click");
        return module instanceof TpClickModule tpClick && tpClick.bool("anti-kick");
    }

    @Override
    public void tick() {
        if (!isEnabled()) {
            reset();
            return;
        }
        if (MC == null || MC.player == null || MC.level == null || PackHideState.isHardLocked()) {
            reset();
            return;
        }

        updateTarget();

        int bind = bindCode();
        boolean down = bind != -1 && AutismBindUtil.isBindPressed(MC, bind);
        boolean pressed = down && !keyWasDown;
        keyWasDown = down;
        if (!pressed || !targetValid || destination == null) return;
        if (MC.gui.screen() != null) return;
        if (MC.options.keyShift.isDown()) return;

        String result = PacketTeleportController.executeMain(
            String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", destination.x, destination.y, destination.z));

        if (result != null && !result.startsWith("TP started")) {
            autismclient.util.AutismClientMessaging.sendPrefixed("§e" + result);
        }
    }

    private void updateTarget() {
        highlightBox = null;
        targetValid = false;
        adjustedTarget = false;
        destination = null;

        if (MC.options.keyShift.isDown()) return;

        boolean riding = MC.player.getVehicle() != null;
        HitResult picked = MC.player.pick(Math.max(10, integer("reach")), 0.0F, riding);
        if (!(picked instanceof BlockHitResult blockHit) || picked.getType() != HitResult.Type.BLOCK) return;

        BlockPos base = blockHit.getBlockPos();
        for (int climb = 1; climb <= MAX_CLIMB; climb++) {
            Vec3 spot = standingSpot(base.above(climb));
            if (spot != null) {
                accept(spot, false);
                return;
            }
        }

        Vec3 nearest = nearestViable(base);
        if (nearest != null) {
            accept(nearest, true);
            return;
        }

        highlightBox = new AABB(base).inflate(0.002D);
    }

    private void accept(Vec3 spot, boolean adjusted) {
        destination = spot;
        highlightBox = new AABB(BlockPos.containing(spot).below()).inflate(0.002D);
        targetValid = true;
        adjustedTarget = adjusted;
    }

    private Vec3 nearestViable(BlockPos base) {
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = -SEARCH_DOWN; dy <= SEARCH_UP; dy++) {
            for (int dx = -SEARCH_HORIZONTAL; dx <= SEARCH_HORIZONTAL; dx++) {
                for (int dz = -SEARCH_HORIZONTAL; dz <= SEARCH_HORIZONTAL; dz++) {
                    Vec3 spot = standingSpot(base.offset(dx, dy, dz));
                    if (spot == null) continue;
                    double score = dx * dx + dz * dz + Math.abs(dy) * 4.0;
                    if (score < bestScore) {
                        bestScore = score;
                        best = spot;
                    }
                }
            }
        }
        return best;
    }

    private static Vec3 standingSpot(BlockPos feet) {
        if (!isFree(feet) || !isFree(feet.above()) || !isGroundBelow(feet)) return null;
        return Vec3.atBottomCenterOf(feet);
    }

    private static boolean isFree(BlockPos pos) {
        return MC.level.getBlockState(pos).getCollisionShape(MC.level, pos).isEmpty();
    }

    private static boolean isGroundBelow(BlockPos feet) {
        BlockPos below = feet.below();
        if (!MC.level.getBlockState(below).getCollisionShape(MC.level, below).isEmpty()) return true;

        return MC.player.getVehicle() != null && !MC.level.getFluidState(below).isEmpty();
    }

    private int bindCode() {
        try {
            return Integer.parseInt(value("click-key"));
        } catch (NumberFormatException ignored) {
            return AutismBindUtil.encodeMouseButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        }
    }

    @Override
    public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
        if (MC != null && MC.options != null && MC.options.keyShift.isDown()) return false;
        return bindCode() == AutismBindUtil.encodeMouseButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT) && targetValid;
    }

    public AABB highlightBox() {
        return highlightBox;
    }

    public int highlightColor() {
        return targetValid ? (adjustedTarget ? COLOR_ADJUSTED : COLOR_VALID) : COLOR_INVALID;
    }
}
