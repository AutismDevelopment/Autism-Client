package autismclient.modules;

import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RangeSetting;
import autismclient.api.module.ValueRange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Random;

public final class SafeWalkModule extends Module {
    private static final Minecraft MC = Minecraft.getInstance();

    private static final double DROP_PROBE_DEPTH = 0.55D;

    private static final double PROBE_INSET = 0.05D;

    static final double MIN_EDGE_DISTANCE = 0.15D;

    static double safeEdgeDistance(double configured) {
        return Math.max(MIN_EDGE_DISTANCE, configured);
    }

    private static final int RELEASE_CLEAR_TICKS = 3;

    private final Random random = new Random();
    private long holdUntilMs;
    private boolean holding;
    private int clearTicks;

    public SafeWalkModule() {
        super("safe-walk", "SafeWalk", ModuleCategory.MOVEMENT, "Sneaks at ledges.");
        add(new IntSetting("look-down", "Look Down", 25, 0, 90, 1)
            .unit("deg")
            .description("Minimum downward look"));
        add(new DoubleSetting("edge-distance", "Edge Distance", 0.30D, MIN_EDGE_DISTANCE, 1.50D, 0.05D)
            .unit("blocks")
            .description("Ledge probe distance"));
        add(new RangeSetting("hold-time", "Hold Time", new ValueRange(100, 200), 0, 500, 5)
            .unit("ms")
            .description("Random sneak hold time"));
    }

    @Override
    public void onDisable() {
        holding = false;
        holdUntilMs = 0L;
        clearTicks = 0;
    }

    public static Input modifyMovementInput(ClientInput source, Input original) {
        if (original == null || MC == null || MC.player == null || MC.player.input != source) return original;
        Module module = ModuleRegistry.get("safe-walk");
        if (!(module instanceof SafeWalkModule safeWalk)) return original;
        if (!safeWalk.isEnabled()) {
            safeWalk.holding = false;
            return original;
        }
        if (!safeWalk.sneakRequested()) return original;
        if (original.shift()) return original;
        return new Input(original.forward(), original.backward(), original.left(), original.right(),
            original.jump(), true, original.sprint());
    }

    private boolean sneakRequested() {
        if (scaffoldOwnsTheEdge()) {
            holding = false;
            return false;
        }
        boolean atEdge = conditionsMet();
        long now = System.currentTimeMillis();
        clearTicks = atEdge ? 0 : clearTicks + 1;
        if (holding && now < holdUntilMs) return true;
        if (atEdge) {

            holding = true;
            holdUntilMs = now + holdMillis();
            return true;
        }
        if (holdsThroughFlicker(holding, clearTicks, RELEASE_CLEAR_TICKS)) return true;
        holding = false;
        return false;
    }

    static boolean holdsThroughFlicker(boolean holding, int clearTicks, int releaseTicks) {
        return holding && clearTicks < releaseTicks;
    }

    private long holdMillis() {
        ValueRange band = ValueRange.parse(value("hold-time"), new ValueRange(100, 200));
        return Math.round(band.random(random));
    }

    private boolean conditionsMet() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null) return false;
        if (!player.onGround() || player.isPassenger() || player.getAbilities().flying) return false;

        if (player.getXRot() < integer("look-down")) return false;
        return dropAhead(safeEdgeDistance(decimal("edge-distance")));
    }

    private boolean dropAhead(double distance) {
        LocalPlayer player = MC.player;
        Vec3 velocity = player.getDeltaMovement();
        Vec3 direction = velocity.horizontalDistanceSqr() > 1.0E-6D
            ? new Vec3(velocity.x, 0.0D, velocity.z).normalize()
            : Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 probe = player.position().add(direction.scale(distance));

        BlockPos underProbe = BlockPos.containing(probe).below();
        if (!MC.level.isOutsideBuildHeight(underProbe)
            && ScaffoldModule.standableSupportState(
                MC.level.getBlockState(underProbe), MC.level, underProbe, CollisionContext.of(player))) {
            return false;
        }
        double half = Math.max(0.05D, player.getBbWidth() / 2.0D - PROBE_INSET);
        AABB below = new AABB(
            probe.x - half, probe.y - DROP_PROBE_DEPTH, probe.z - half,
            probe.x + half, probe.y, probe.z + half);
        return MC.level.noCollision(player, below);
    }

    private static boolean scaffoldOwnsTheEdge() {
        Module scaffold = ModuleRegistry.get("scaffold");
        return scaffold != null && scaffold.isEnabled();
    }
}
