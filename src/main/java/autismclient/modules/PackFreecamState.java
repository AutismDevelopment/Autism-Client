package autismclient.modules;

import autismclient.util.AutismKeyMappingBridge;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class PackFreecamState {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final double SCROLL_SPEED_STEP = 0.5;
    private static final double MIN_SPEED = 0.05;
    private static final double MAX_SPEED = 30.0;

    private static boolean active;
    private static Vec3 pos = Vec3.ZERO;
    private static Vec3 prevPos = Vec3.ZERO;
    private static float yaw;
    private static float pitch;
    private static float lastYaw;
    private static float lastPitch;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean reloadChunks;

    private static boolean interactEnabled = true;

    private PackFreecamState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean interactEnabled() {
        return interactEnabled;
    }

    public static void setInteractEnabled(boolean enabled) {
        interactEnabled = enabled;
    }

    public static void enable(boolean shouldReloadChunks) {
        if (MC == null || MC.player == null || MC.level == null) return;

        Vec3 startingPos = MC.gameRenderer.mainCamera().position();
        net.minecraft.world.entity.Entity startingView = MC.getCameraEntity() == null ? MC.player : MC.getCameraEntity();
        float startingYaw = startingView.getYRot();
        float startingPitch = startingView.getXRot();
        if (autismclient.util.AutismRemoteView.isActive()) {
            autismclient.util.AutismRemoteView.stop(false);
        }
        reloadChunks = shouldReloadChunks;
        previousCameraType = MC.options.getCameraType();
        pos = startingPos;
        prevPos = pos;

        yaw = startingYaw;
        pitch = startingPitch;
        lastYaw = yaw;
        lastPitch = pitch;
        active = true;
        clearMovementKeys();
        MC.options.setCameraType(CameraType.FIRST_PERSON);
        if (reloadChunks) ModuleRenderUtil.refreshWorldRenderer();
    }

    public static void disable() {
        if (!active) return;
        active = false;
        if (MC != null) {
            MC.options.setCameraType(previousCameraType);
            if (reloadChunks) ModuleRenderUtil.refreshWorldRenderer();
        }
        reloadChunks = false;
    }

    public static void tickMovement(double speed, double verticalSpeed) {
        if (!active) return;
        if (MC == null || MC.player == null || MC.level == null) {
            active = false;
            return;
        }

        lastYaw = yaw;
        lastPitch = pitch;
        prevPos = pos;

        boolean forward = actuallyDown(MC.options.keyUp);
        boolean backward = actuallyDown(MC.options.keyDown);
        boolean right = actuallyDown(MC.options.keyRight);
        boolean left = actuallyDown(MC.options.keyLeft);
        boolean up = actuallyDown(MC.options.keyJump);
        boolean down = actuallyDown(MC.options.keyShift);
        boolean sprint = actuallyDown(MC.options.keySprint);

        clearMovementKeys();

        if (MC.gui.screen() != null) return;

        double horizontal = Math.max(0.0, speed) * (sprint ? 1.0 : 0.5);
        double vertical = Math.max(0.0, verticalSpeed) * (sprint ? 1.0 : 0.5);
        Vec3 forwardVec = Vec3.directionFromRotation(0.0F, yaw);
        Vec3 rightVec = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
        double dx = 0.0;
        double dy = 0.0;
        double dz = 0.0;
        boolean movedForward = false;
        boolean movedSideways = false;

        if (forward) {
            dx += forwardVec.x * horizontal;
            dz += forwardVec.z * horizontal;
            movedForward = true;
        }
        if (backward) {
            dx -= forwardVec.x * horizontal;
            dz -= forwardVec.z * horizontal;
            movedForward = true;
        }
        if (right) {
            dx += rightVec.x * horizontal;
            dz += rightVec.z * horizontal;
            movedSideways = true;
        }
        if (left) {
            dx -= rightVec.x * horizontal;
            dz -= rightVec.z * horizontal;
            movedSideways = true;
        }
        if (movedForward && movedSideways) {
            double diagonal = 1.0 / Math.sqrt(2.0);
            dx *= diagonal;
            dz *= diagonal;
        }
        if (up) dy += vertical;
        if (down) dy -= vertical;

        pos = pos.add(dx, dy, dz);
    }

    public static Vec3 onPlayerMove(MoverType type, Vec3 movement) {

        return movement;
    }

    public static boolean onMouseScroll(double scrollY) {
        if (!active || scrollY == 0.0) return false;
        if (MC == null || MC.player == null || MC.gui.screen() != null || MC.gui.overlay() != null) return false;
        Module freecam = ModuleRegistry.get("freecam");
        if (freecam == null) return false;
        double step = scrollY > 0.0 ? SCROLL_SPEED_STEP : -SCROLL_SPEED_STEP;
        double speed = snapSpeed(parseSpeed(freecam.value("speed")) + step);
        double vertical = snapSpeed(parseSpeed(freecam.value("vertical-speed")) + step);
        freecam.setValueTransient("speed", formatSpeed(speed));
        freecam.setValueTransient("vertical-speed", formatSpeed(vertical));
        MC.gui.hud.setOverlayMessage(speedMessage(speed), false);
        return true;
    }

    private static double snapSpeed(double value) {
        return clampSpeed(Math.ceil(value * 2.0) / 2.0);
    }

    private static net.minecraft.network.chat.Component speedMessage(double speed) {
        return net.minecraft.network.chat.Component.empty()
            .append(styled("[", autismclient.util.AutismColors.textMuted(), false))
            .append(styled("AUTISM", autismclient.util.AutismColors.accent(), true))
            .append(styled("] ", autismclient.util.AutismColors.textMuted(), false))
            .append(styled("Freecam Speed", autismclient.util.AutismColors.packetGray(), true))
            .append(styled(": ", autismclient.util.AutismColors.textMuted(), false))
            .append(styled(formatSpeed(speed), autismclient.util.AutismColors.packetLightYellow(), true));
    }

    private static net.minecraft.network.chat.MutableComponent styled(String value, int color, boolean bold) {
        net.minecraft.network.chat.Style style = net.minecraft.network.chat.Style.EMPTY.withColor(color);
        if (bold) style = style.withBold(true);
        return net.minecraft.network.chat.Component.literal(value).setStyle(style);
    }

    private static double parseSpeed(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException | NullPointerException ignored) {
            return 1.0;
        }
    }

    private static double clampSpeed(double value) {
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, value));
    }

    private static String formatSpeed(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    public static void turn(double deltaYaw, double deltaPitch) {
        if (!active) return;
        yaw += (float) (deltaYaw * 0.15);
        pitch += (float) (deltaPitch * 0.15);
        pitch = Mth.clamp(pitch, -90.0F, 90.0F);
    }

    public static double getX(float tickDelta) {
        return Mth.lerp((double) tickDelta, prevPos.x, pos.x);
    }

    public static double getY(float tickDelta) {
        return Mth.lerp((double) tickDelta, prevPos.y, pos.y);
    }

    public static double getZ(float tickDelta) {
        return Mth.lerp((double) tickDelta, prevPos.z, pos.z);
    }

    public static float getYaw(float tickDelta) {
        return Mth.lerp(tickDelta, lastYaw, yaw);
    }

    public static float getPitch(float tickDelta) {
        return Mth.lerp(tickDelta, lastPitch, pitch);
    }

    private static boolean actuallyDown(KeyMapping mapping) {
        return mapping != null && AutismKeyMappingBridge.of(mapping).autism$isActuallyDown();
    }

    private static void clearMovementKeys() {
        if (MC == null || MC.options == null) return;
        MC.options.keyUp.setDown(false);
        MC.options.keyDown.setDown(false);
        MC.options.keyRight.setDown(false);
        MC.options.keyLeft.setDown(false);
        MC.options.keyJump.setDown(false);
        MC.options.keyShift.setDown(false);
        MC.options.keySprint.setDown(false);
    }
}
