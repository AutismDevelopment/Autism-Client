package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class TrajectoriesModule extends Module {

    public record Marker(AABB box, int color) {
    }

    public record Path(List<Vec3> points, int color, float lineWidth, List<Marker> markers) {
    }

    public TrajectoriesModule() {
        super("trajectories", "Trajectories", ModuleCategory.RENDER, "Shows where throws land.");

        add(new IntSetting("traj-ticks", "Max Ticks", 120, 20, 1000, 10)
            .description("Simulation length.").build());
        add(new BoolSetting("traj-multishot", "Multishot", true)
            .description("Show crossbow spread.").build());
        add(new BoolSetting("traj-always-bow", "Always Show Bow", false)
            .description("Show before drawing.").build());
        add(new BoolSetting("traj-hit-marker", "Hit Marker", true)
            .description("Box at impact.").build());
        add(new DoubleSetting("traj-line-width", "Line Width", 1.0, 1.0, 8.0, 0.5)
            .description("Trajectory line thickness.").build());
        add(new ColorSetting("traj-color", "Color", 0xFFFF3B3B)
            .description("Trajectory line color.").build());
        add(new ColorSetting("traj-hit-color", "Hit Color", 0xFFFF3B3B)
            .description("Block impact color.").build());
        add(new ColorSetting("traj-entity-color", "Entity Color", 0xFF35D873)
            .description("Entity hit color.").build());
    }

    @Override
    public void onDisable() {
        ModuleWorldRenderer.setTrajectoryPaths(List.of());
    }

    public static void collect(float partialTick) {
        Module module = ModuleRegistry.get("trajectories");
        if (!(module instanceof TrajectoriesModule trajectories) || !trajectories.isEnabled()) {
            ModuleWorldRenderer.setTrajectoryPaths(List.of());
            return;
        }
        ModuleWorldRenderer.setTrajectoryPaths(trajectories.buildPaths(partialTick));
    }

    private List<Path> buildPaths(float partialTick) {
        if (MC == null || MC.player == null || MC.level == null) return List.of();
        Player player = MC.player;
        int maxTicks = integer("traj-ticks");
        boolean multiShot = bool("traj-multishot");
        boolean alwaysBow = bool("traj-always-bow");
        boolean hitMarker = bool("traj-hit-marker");
        int color = ModuleRenderUtil.color(this, "traj-color", 0xFFFF3B3B);
        int blockColor = ModuleRenderUtil.color(this, "traj-hit-color", 0xFFFF3B3B);
        int entityColor = ModuleRenderUtil.color(this, "traj-entity-color", 0xFF35D873);
        float lineWidth = (float) decimal("traj-line-width");

        float yaw = player.getYRot();
        float pitch = player.getXRot();

        double yawRadians = Math.toRadians(yaw);
        Vec3 offset = interpolated(player, partialTick).subtract(player.position())
            .add(-Math.cos(yawRadians) * 0.16, 0.0, -Math.sin(yawRadians) * 0.16);

        Vec3 inherited = TrajectorySim.inheritedVelocity(player);

        List<Path> paths = new ArrayList<>(2);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            for (TrajectorySim.Shot shot : TrajectorySim.shotsFor(player, stack, alwaysBow, multiShot)) {
                TrajectorySim.Result result = TrajectorySim.simulate(MC.level, player, shot.info(),
                    yaw + shot.yawOffsetDegrees(), pitch, maxTicks, inherited);
                if (result.points().size() < 2) continue;
                List<Vec3> points = new ArrayList<>(result.points().size());
                for (Vec3 point : result.points()) points.add(point.add(offset));
                List<Marker> markers = new ArrayList<>(1);
                if (hitMarker && result.hit() != null) {

                    if (result.hit() instanceof EntityHitResult entityHit) {
                        markers.add(new Marker(interpolatedBox(entityHit.getEntity(), partialTick), entityColor));
                    } else {
                        markers.add(new Marker(landingBox(result.hit().getLocation().add(offset),
                            shot.info().hitboxRadius()), blockColor));
                    }
                    if (shot.info().isSplashPotion()) {

                        splashTargets(result.hit().getLocation(), partialTick, entityColor, markers);
                    }
                }
                paths.add(new Path(points, color, lineWidth, markers));
            }

            if (!paths.isEmpty()) break;
        }
        return paths;
    }

    private static AABB interpolatedBox(net.minecraft.world.entity.Entity entity, float partialTick) {
        Vec3 at = new Vec3(
            Mth.lerp(partialTick, entity.xo, entity.getX()),
            Mth.lerp(partialTick, entity.yo, entity.getY()),
            Mth.lerp(partialTick, entity.zo, entity.getZ()));
        return entity.getDimensions(entity.getPose()).makeBoundingBox(at);
    }

    private static void splashTargets(Vec3 landing, float partialTick, int color, List<Marker> out) {
        if (MC == null || MC.level == null || landing == null) return;
        AABB cloud = new AABB(landing, landing).inflate(4.0, 2.0, 4.0);
        for (LivingEntity target : MC.level.getEntitiesOfClass(LivingEntity.class, cloud,
            candidate -> candidate.distanceToSqr(landing) <= 16.0 && candidate.isAffectedByPotions())) {
            if (target == MC.player) continue;
            out.add(new Marker(interpolatedBox(target, partialTick), color));
        }
    }

    private static Vec3 interpolated(Player player, float partialTick) {
        if (player.tickCount == 0) return player.position();
        return new Vec3(
            Math.fma((double) partialTick, player.getX() - player.xOld, player.xOld),
            Math.fma((double) partialTick, player.getY() - player.yOld, player.yOld),
            Math.fma((double) partialTick, player.getZ() - player.zOld, player.zOld));
    }

    private static AABB landingBox(Vec3 at, double hitboxRadius) {
        double size = Math.max(0.20, hitboxRadius * 0.8);
        return new AABB(at.x - size, at.y - size, at.z - size, at.x + size, at.y + size, at.z + size);
    }
}
