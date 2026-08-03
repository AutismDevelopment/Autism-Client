package autismclient.util;

import autismclient.modules.PackHideState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AutismWorldHighlightRenderer {
    private static final double INFLATE = 0.0025;
    private static final double CORNER = 0.25;

    private static volatile boolean on;
    private static volatile int lineColor = 0xFFFFFFFF;
    private static volatile int fillColor;
    private static volatile float lineWidth = 2.0f;
    private static volatile boolean corners;
    private static volatile boolean fill;

    private static volatile boolean progressDynamic;

    private AutismWorldHighlightRenderer() {}

    public static void push(boolean enabled, int line, float width, boolean cornerStyle, boolean filled, int fillArgb) {
        lineColor = line;
        lineWidth = Math.max(0.1f, width);
        corners = cornerStyle;
        fill = filled;
        fillColor = fillArgb;
        on = enabled;
    }

    public static void pushProgress(boolean dynamic) {
        progressDynamic = dynamic;
    }

    public static void disable() {
        on = false;
    }

    public static boolean isActive() {
        return on && !PackHideState.isHardLocked();
    }

    public static void initialize() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (!isActive()) return;

            if (AutismFreecamHighlightRenderer.isReplacingVanillaOutline()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null || mc.player == null) return;
            var cameraState = context.levelState().cameraRenderState;
            Vec3 origin = cameraState.pos;
            Vec3 direction = Vec3.directionFromRotation(cameraState.xRot, cameraState.yRot);
            double range = Math.max(4.5D, mc.player.blockInteractionRange());
            BlockHitResult hit = mc.level.clip(new ClipContext(origin, origin.add(direction.scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
            AABB box = targetBox(mc, hit.getBlockPos()).inflate(INFLATE).move(-origin.x, -origin.y, -origin.z);
            PoseStack poseStack = context.poseStack();

            float progress = progressDynamic && mc.gameMode instanceof autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor accessor
                ? Math.max(0.0f, Math.min(1.0f, accessor.autism$getDestroyProgress()))
                : 0.0f;
            boolean dynamic = progress > 0.0f;
            int line = dynamic ? lerpColor(0xFF35D873, 0xFFFF3B3B, progress) : lineColor;
            if (fill) {
                int fc = dynamic
                    ? (fillColor & 0xFF000000) | (lerpColor(0xFF35D873, 0xFFFF3B3B, progress) & 0x00FFFFFF)
                    : fillColor;
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    AutismRenderTypes.storageEspFillSeeThrough(), (pose, buffer) -> fillBox(pose, buffer, box, fc));
            }
            context.submitNodeCollector().submitCustomGeometry(poseStack,
                AutismRenderTypes.storageEspLinesSeeThrough(),
                (pose, buffer) -> edges(pose, buffer, box, line, lineWidth, corners));
        });
    }

    private static AABB targetBox(Minecraft mc, BlockPos pos) {
        try {
            VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
            if (!shape.isEmpty()) return shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
        } catch (Throwable ignored) {  }
        return new AABB(pos);
    }

    private static void edges(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width, boolean corners) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ, x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        edge(pose, buffer, x1, y1, z1, x2, y1, z1, color, width, corners);
        edge(pose, buffer, x2, y1, z1, x2, y1, z2, color, width, corners);
        edge(pose, buffer, x2, y1, z2, x1, y1, z2, color, width, corners);
        edge(pose, buffer, x1, y1, z2, x1, y1, z1, color, width, corners);
        edge(pose, buffer, x1, y2, z1, x2, y2, z1, color, width, corners);
        edge(pose, buffer, x2, y2, z1, x2, y2, z2, color, width, corners);
        edge(pose, buffer, x2, y2, z2, x1, y2, z2, color, width, corners);
        edge(pose, buffer, x1, y2, z2, x1, y2, z1, color, width, corners);
        edge(pose, buffer, x1, y1, z1, x1, y2, z1, color, width, corners);
        edge(pose, buffer, x2, y1, z1, x2, y2, z1, color, width, corners);
        edge(pose, buffer, x2, y1, z2, x2, y2, z2, color, width, corners);
        edge(pose, buffer, x1, y1, z2, x1, y2, z2, color, width, corners);
    }

    private static void edge(PoseStack.Pose pose, VertexConsumer buffer, double ax, double ay, double az,
                             double bx, double by, double bz, int color, float width, boolean corners) {
        if (!corners) {
            AutismWorldGeometry.line(pose, buffer, ax, ay, az, bx, by, bz, color, width);
            return;
        }
        double dx = (bx - ax) * CORNER, dy = (by - ay) * CORNER, dz = (bz - az) * CORNER;
        AutismWorldGeometry.line(pose, buffer, ax, ay, az, ax + dx, ay + dy, az + dz, color, width);
        AutismWorldGeometry.line(pose, buffer, bx, by, bz, bx - dx, by - dy, bz - dz, color, width);
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int ar = (from >> 16) & 0xFF, ag = (from >> 8) & 0xFF, ab = from & 0xFF, aa = (from >>> 24) & 0xFF;
        int br = (to >> 16) & 0xFF, bg = (to >> 8) & 0xFF, bb = to & 0xFF, ba = (to >>> 24) & 0xFF;
        int r = Math.round(ar + (br - ar) * clamped);
        int g = Math.round(ag + (bg - ag) * clamped);
        int b = Math.round(ab + (bb - ab) * clamped);
        int a = Math.round(aa + (ba - aa) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void fillBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color);
        quad(pose, buffer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int color) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(color);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(color);
    }
}
