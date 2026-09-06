package autismclient.util;

import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.ModuleRenderUtil;
import autismclient.modules.PackHideState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class AutismSkeletonRenderer {

    private static final double HIP_Y = 0.72, NECK_Y = 1.35, HEAD_LEN = 0.30;
    private static final double HIP_HALF = 0.11, SHOULDER_HALF = 0.32;
    private static final double LEG_LEN = 0.72, ARM_LEN = 0.66;
    private static final double CROUCH_LEAN = 0.3;
    private static final double CROUCH_ARM = 0.3;

    private static final double DISTANCE_SPAN = 48.0;

    private static final AutismBufferSource.Holder BUFFERS = new AutismBufferSource.Holder(
        net.minecraft.client.renderer.rendertype.RenderType.SMALL_BUFFER_SIZE);

    private static volatile List<Bone> pending = List.of();

    private AutismSkeletonRenderer() {
    }

    public static void initialize() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            pending = List.of();
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null || mc.player == null) return;
            if (PackHideState.isActive() || mc.gui.hud.isHidden()) return;
            Module esp = ModuleRegistry.get("esp");
            if (esp == null || !esp.isEnabled() || !Boolean.parseBoolean(esp.value("skeleton"))) return;
            if (ModuleRenderUtil.shouldSuppressEspForUi()) return;

            boolean distanceColor = "Distance".equals(esp.value("skeleton-color-mode"));
            int staticColor = ModuleRenderUtil.color(esp, "skeleton-color", 0xFFFFFFFF);
            float width = (float) Math.max(0.5, Math.min(6.0, parseDouble(esp.value("skeleton-width"), 2.0)));

            Vec3 camera = context.levelState().cameraRenderState.pos;
            float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

            List<Bone> bones = new ArrayList<>();
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living) || living.isRemoved() || living == mc.player) continue;
                if (!ModuleRenderUtil.shouldEsp(entity)) continue;
                int color = distanceColor ? distanceColor(camera, living, partial) : staticColor;
                addSkeleton(bones, living, partial, camera, color, width);
            }
            if (!bones.isEmpty()) pending = bones;
        });
    }

    public static boolean hasPending() {
        return !pending.isEmpty();
    }

    public static void flush(PoseStack matrices) {
        List<Bone> bones = pending;
        if (bones.isEmpty()) return;
        pending = List.of();
        AutismBufferSource bufferSource = BUFFERS.get();
        VertexConsumer buffer = bufferSource.getBuffer(AutismRenderTypes.tracerEspLines());
        PoseStack.Pose pose = matrices.last();
        for (Bone bone : bones) {
            drawLine(pose, buffer, bone.a, bone.b, bone.color, bone.width);
        }
        bufferSource.uploadAndDraw();
    }

    private static void addSkeleton(List<Bone> bones, LivingEntity e, float partial, Vec3 cam, int color, float width) {
        double px = Mth.lerp((double) partial, e.xOld, e.getX());
        double py = Mth.lerp((double) partial, e.yOld, e.getY());
        double pz = Mth.lerp((double) partial, e.zOld, e.getZ());

        float bodyYaw = e.yBodyRotO + Mth.wrapDegrees(e.yBodyRot - e.yBodyRotO) * partial;
        float headYaw = e.yHeadRotO + Mth.wrapDegrees(e.yHeadRot - e.yHeadRotO) * partial;
        float netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float headPitch = e.getViewXRot(partial);
        float limbPos = e.walkAnimation.position(partial);
        float limbSpeed = Math.min(1.0f, e.walkAnimation.speed(partial));
        boolean crouch = e.isCrouching();

        double s = Math.max(0.1, e.getBbHeight() / 1.8);

        double hipY = HIP_Y * s, neckY = NECK_Y * s;
        double hipHalf = HIP_HALF * s, shoulderHalf = SHOULDER_HALF * s;
        double legLen = LEG_LEN * s, armLen = ARM_LEN * s, headLen = HEAD_LEN * s;

        float t = limbPos * 0.6662f;
        float legR = Mth.cos(t) * 1.4f * limbSpeed;
        float legL = Mth.cos(t + (float) Math.PI) * 1.4f * limbSpeed;

        double swingR = Math.cos(t) * limbSpeed;
        double swingL = -Math.cos(t) * limbSpeed;
        double age = e.tickCount + partial;
        double bobPitch = Math.sin(age * 0.067) * 0.05;
        double bobRoll = Math.cos(age * 0.09) * 0.03 + 0.03;
        double crouchArm = crouch ? CROUCH_ARM : 0.0;
        boolean rightDominant = e.getMainArm() == HumanoidArm.RIGHT;
        boolean rightHeld = !(rightDominant ? e.getMainHandItem() : e.getOffhandItem()).isEmpty();
        boolean leftHeld = !(rightDominant ? e.getOffhandItem() : e.getMainHandItem()).isEmpty();
        boolean using = e.isUsingItem();
        boolean rightUsing = using && ((e.getUsedItemHand() == InteractionHand.MAIN_HAND) == rightDominant);
        boolean leftUsing = using && !rightUsing;
        double pitchR = armPitch(swingR, rightHeld, rightUsing) - bobPitch + crouchArm;
        double pitchL = armPitch(swingL, leftHeld, leftUsing) + bobPitch + crouchArm;

        double[] pelvis = {0, hipY, 0};
        double[] neck = {0, neckY, 0};
        double[] hipL = {-hipHalf, hipY, 0};
        double[] hipR = {hipHalf, hipY, 0};
        double[] shL = {-shoulderHalf, neckY, 0};
        double[] shR = {shoulderHalf, neckY, 0};
        double[] footL = {-hipHalf, hipY - legLen * Math.cos(legL), -legLen * Math.sin(legL)};
        double[] footR = {hipHalf, hipY - legLen * Math.cos(legR), -legLen * Math.sin(legR)};
        double[] handR = armLocal(1.0, shoulderHalf, neckY, armLen, pitchR, bobRoll);
        double[] handL = armLocal(-1.0, shoulderHalf, neckY, armLen, pitchL, bobRoll);

        if (crouch) {
            leanUpper(neck, hipY);
            leanUpper(shL, hipY);
            leanUpper(shR, hipY);
            leanUpper(handL, hipY);
            leanUpper(handR, hipY);
        }

        double[] head = headFrom(neck, headLen, headPitch, netHeadYaw);

        double rad = Math.toRadians(bodyYaw);
        double sin = Math.sin(rad), cos = Math.cos(rad);

        Vec3 wPelvis = world(px, py, pz, pelvis, sin, cos, cam);
        Vec3 wNeck = world(px, py, pz, neck, sin, cos, cam);
        Vec3 wHipL = world(px, py, pz, hipL, sin, cos, cam);
        Vec3 wHipR = world(px, py, pz, hipR, sin, cos, cam);
        Vec3 wShL = world(px, py, pz, shL, sin, cos, cam);
        Vec3 wShR = world(px, py, pz, shR, sin, cos, cam);
        Vec3 wFootL = world(px, py, pz, footL, sin, cos, cam);
        Vec3 wFootR = world(px, py, pz, footR, sin, cos, cam);
        Vec3 wHandL = world(px, py, pz, handL, sin, cos, cam);
        Vec3 wHandR = world(px, py, pz, handR, sin, cos, cam);
        Vec3 wHead = world(px, py, pz, head, sin, cos, cam);

        add(bones, wPelvis, wNeck, color, width);
        add(bones, wHipL, wHipR, color, width);
        add(bones, wShL, wShR, color, width);
        add(bones, wNeck, wHead, color, width);
        add(bones, wShL, wHandL, color, width);
        add(bones, wShR, wHandR, color, width);
        add(bones, wHipL, wFootL, color, width);
        add(bones, wHipR, wFootR, color, width);
    }

    private static double armPitch(double swing, boolean holdingItem, boolean using) {
        if (using) return swing * 0.5 + 0.9;
        if (holdingItem) return swing * 0.5 + 0.31415927;
        return swing;
    }

    private static double[] armLocal(double side, double shoulderHalf, double neckY, double armLen,
                                     double pitch, double roll) {
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double cr = Math.cos(roll), sr = Math.sin(roll);
        return new double[]{
            side * (shoulderHalf + armLen * cp * sr),
            neckY - armLen * cp * cr,
            armLen * sp};
    }

    private static double[] headFrom(double[] neck, double headLen, float headPitch, float netHeadYaw) {
        double hpr = Math.toRadians(headPitch);
        double upY = headLen * Math.cos(hpr);
        double upZ = headLen * Math.sin(hpr);
        double hyr = Math.toRadians(netHeadYaw);
        return new double[]{neck[0] + upZ * Math.sin(hyr), neck[1] + upY, neck[2] + upZ * Math.cos(hyr)};
    }

    private static void leanUpper(double[] p, double pivotY) {
        double ry = p[1] - pivotY, rz = p[2];
        double c = Math.cos(CROUCH_LEAN), s = Math.sin(CROUCH_LEAN);
        p[1] = pivotY + ry * c - rz * s;
        p[2] = ry * s + rz * c;
    }

    private static Vec3 world(double px, double py, double pz, double[] local, double sin, double cos, Vec3 cam) {
        double lx = local[0], ly = local[1], lz = local[2];
        double wx = px + lx * (-cos) + lz * (-sin);
        double wz = pz + lx * (-sin) + lz * cos;
        double wy = py + ly;
        return new Vec3(wx - cam.x, wy - cam.y, wz - cam.z);
    }

    private static void drawLine(PoseStack.Pose entry, VertexConsumer buffer, Vec3 a, Vec3 b, int color, float width) {
        Vector3f normal = new Vector3f((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z));
        if (normal.lengthSquared() <= 1.0E-8f) return;
        normal.normalize();
        float x1 = (float) a.x, y1 = (float) a.y, z1 = (float) a.z;
        float x2 = (float) b.x, y2 = (float) b.y, z2 = (float) b.z;
        buffer.addVertex(entry, x1, y1, z1).setColor(color).setNormal(entry, normal).setLineWidth(width);
        float distToCam = new Vector3f(x1, y1, z1).negate().dot(normal);
        float length = new Vector3f(x2, y2, z2).sub(x1, y1, z1).length();
        if (distToCam > 0 && distToCam < length) {
            Vector3f mid = new Vector3f(normal).mul(distToCam).add(x1, y1, z1);
            buffer.addVertex(entry, mid.x, mid.y, mid.z).setColor(color).setNormal(entry, normal).setLineWidth(width);
            buffer.addVertex(entry, mid.x, mid.y, mid.z).setColor(color).setNormal(entry, normal).setLineWidth(width);
        }
        buffer.addVertex(entry, x2, y2, z2).setColor(color).setNormal(entry, normal).setLineWidth(width);
    }

    private static void add(List<Bone> bones, Vec3 a, Vec3 b, int color, float width) {
        bones.add(new Bone(a, b, color, width));
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int distanceColor(Vec3 cam, LivingEntity e, float partial) {
        double dx = Mth.lerp((double) partial, e.xOld, e.getX()) - cam.x;
        double dy = Mth.lerp((double) partial, e.yOld, e.getY()) - cam.y;
        double dz = Mth.lerp((double) partial, e.zOld, e.getZ()) - cam.z;
        return ModuleRenderUtil.distanceHueColor(Math.sqrt(dx * dx + dy * dy + dz * dz), DISTANCE_SPAN);
    }

    private record Bone(Vec3 a, Vec3 b, int color, float width) {
    }
}
