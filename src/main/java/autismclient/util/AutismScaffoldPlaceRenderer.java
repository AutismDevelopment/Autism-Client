package autismclient.util;

import autismclient.modules.PackHideState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class AutismScaffoldPlaceRenderer {
    private static final int CAP = 32;
    private static final long DURATION_NS = 320_000_000L;
    private static final double FADE_START = 0.62;
    private static final double INFLATE = 0.0035;
    private static final float BASE_ALPHA = 0.55f;

    private static final int[] PX = new int[CAP];
    private static final int[] PY = new int[CAP];
    private static final int[] PZ = new int[CAP];
    private static final long[] AT = new long[CAP];
    private static int idx;

    private static volatile boolean on;
    private static volatile boolean customColor;
    private static volatile int color = 0xFFFF3B3B;

    private AutismScaffoldPlaceRenderer() {}

    public static void push(boolean enabled, boolean custom, int customArgb) {
        on = enabled;
        customColor = custom;
        color = customArgb;
    }

    public static void disable() {
        on = false;
    }

    public static void recordPlacement(BlockPos pos) {
        if (!on || pos == null) return;
        PX[idx] = pos.getX();
        PY[idx] = pos.getY();
        PZ[idx] = pos.getZ();
        AT[idx] = System.nanoTime();
        idx = (idx + 1) % CAP;
    }

    public static boolean isActive() {
        return on && !PackHideState.isHardLocked();
    }

    public static void initialize() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (!isActive()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;
            long now = System.nanoTime();
            int argb = baseColor();
            Vec3 origin = context.levelState().cameraRenderState.pos;
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                AutismRenderTypes.storageEspFillSeeThrough(), (pose, buffer) -> {
                    for (int i = 0; i < CAP; i++) {
                        long t = AT[i];
                        if (t == 0L) continue;
                        long age = now - t;
                        if (age < 0L || age >= DURATION_NS) continue;
                        ripple(pose, buffer, PX[i] - origin.x, PY[i] - origin.y, PZ[i] - origin.z,
                            age / (double) DURATION_NS, argb);
                    }
                });
        });
    }

    private static int baseColor() {
        if (customColor) return color | 0xFF000000;
        return AutismTheme.recolor(0xFFFF3B3B, AutismTheme.Channel.ACCENT);
    }

    private static void ripple(PoseStack.Pose pose, VertexConsumer buffer,
                               double bx, double by, double bz, double t, int argb) {
        double inv = 1.0 - t;
        double scale = 1.0 - inv * inv * inv;
        double fade = t < FADE_START ? 1.0 : 1.0 - (t - FADE_START) / (1.0 - FADE_START);
        int alpha = (int) (BASE_ALPHA * fade * 255.0);
        if (alpha <= 0) return;
        int c = (argb & 0x00FFFFFF) | (alpha << 24);
        double half = 0.5 * scale + INFLATE;
        double x1 = bx + 0.5 - half, y1 = by + 0.5 - half, z1 = bz + 0.5 - half;
        double x2 = bx + 0.5 + half, y2 = by + 0.5 + half, z2 = bz + 0.5 + half;
        quad(pose, buffer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, c);
        quad(pose, buffer, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, c);
        quad(pose, buffer, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, c);
        quad(pose, buffer, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, c);
        quad(pose, buffer, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, c);
        quad(pose, buffer, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, c);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3,
                             double x4, double y4, double z4, int color) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(color);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(color);
    }
}
