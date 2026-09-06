package autismclient.util;

import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

public final class AutismMouseInputSimulator {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final double AIM_DISTRIBUTION_SECONDS = 1.0 / 20.0;
    private static final EnumMap<Source, RawMouseAccumulator> ACCUMULATORS = new EnumMap<>(Source.class);
    private static final SmoothedMouseAccumulator AIM_ACCUMULATOR = new SmoothedMouseAccumulator();
    private static volatile Source exclusiveSource;
    private static long lastConsumeNanos;

    static {
        for (Source source : Source.values()) ACCUMULATORS.put(source, new RawMouseAccumulator());
    }

    private AutismMouseInputSimulator() {
    }

    public enum Source {
        GENERIC,
        AIM_ASSIST,
        AUTO_FISH,
        SCAFFOLD_TELLY
    }

    public record Delta(double x, double y) {
        public boolean isZero() {
            return Math.abs(x) < 1.0E-7 && Math.abs(y) < 1.0E-7;
        }
    }

    public static void queueRotation(AutismRotationUtil.Rotation current, AutismRotationUtil.Rotation target) {
        queueRotation(Source.GENERIC, current, target);
    }

    public static void queueRotation(Source source, AutismRotationUtil.Rotation current,
                                     AutismRotationUtil.Rotation target) {
        if (current == null || target == null) return;
        float yawDelta = AutismRotationUtil.angleDifference(target.yaw(), current.yaw());
        float pitchDelta = Mth.clamp(target.pitch(), -90.0f, 90.0f) - Mth.clamp(current.pitch(), -90.0f, 90.0f);
        queueRotationDelta(source, yawDelta, pitchDelta);
    }

    public static void queueRotationDelta(float yawDelta, float pitchDelta) {
        queueRotationDelta(Source.GENERIC, yawDelta, pitchDelta);
    }

    public static void queueRotationDelta(Source source, float yawDelta, float pitchDelta) {
        if (!canUseMouseLook()) {
            clear(source);
            return;
        }

        double degreesPerRawInput = AutismRotationUtil.mouseDegreesPerRawInput();
        if (degreesPerRawInput <= 1.0E-8 || !Double.isFinite(degreesPerRawInput)) return;

        double rawX = yawDelta / degreesPerRawInput;
        double rawY = pitchDelta / degreesPerRawInput;
        if (MC.options.invertMouseX().get()) rawX = -rawX;
        if (MC.options.invertMouseY().get()) rawY = -rawY;
        queueRawDelta(source, rawX, rawY);
    }

    public static void queueRotationCounts(Source source, long yawCounts, long pitchCounts) {
        if (!canUseMouseLook()) {
            clear(source);
            return;
        }
        double rawX = yawCounts;
        double rawY = pitchCounts;
        if (MC.options.invertMouseX().get()) rawX = -rawX;
        if (MC.options.invertMouseY().get()) rawY = -rawY;
        queueRawDelta(source, rawX, rawY);
    }

    public static void queueRawDelta(double deltaX, double deltaY) {
        queueRawDelta(Source.GENERIC, deltaX, deltaY);
    }

    public static void queueRawDelta(Source source, double deltaX, double deltaY) {
        if (!canUseMouseLook()) {
            clear(source);
            return;
        }
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY)) return;
        if (source == Source.AIM_ASSIST) {
            AIM_ACCUMULATOR.replace(deltaX, deltaY, AIM_DISTRIBUTION_SECONDS);
            return;
        }
        if (source == Source.SCAFFOLD_TELLY) {

            accumulator(source).replaceQueued(deltaX, deltaY);
            return;
        }
        if (Math.abs(deltaX) < 1.0E-7 && Math.abs(deltaY) < 1.0E-7) return;
        accumulator(source).queue(deltaX, deltaY);
    }

    public static Delta consume() {
        if (!canUseMouseLook()) {
            clear();
            return new Delta(0.0, 0.0);
        }
        long now = System.nanoTime();
        double elapsedSeconds = lastConsumeNanos == 0L
            ? 1.0 / 60.0
            : Math.clamp((now - lastConsumeNanos) / 1_000_000_000.0, 1.0 / 1000.0, 0.05);
        lastConsumeNanos = now;
        Source exclusive = exclusiveSource;
        if (exclusive != null) {
            clearExcept(exclusive);
            RawMouseAccumulator.Counts counts = consumeSource(exclusive, elapsedSeconds);
            return new Delta(counts.x(), counts.y());
        }
        long x = 0L;
        long y = 0L;
        for (Map.Entry<Source, RawMouseAccumulator> entry : ACCUMULATORS.entrySet()) {
            if (entry.getKey() == Source.AIM_ASSIST || entry.getKey() == Source.SCAFFOLD_TELLY) continue;
            RawMouseAccumulator accumulator = entry.getValue();
            RawMouseAccumulator.Counts counts = accumulator.consume();
            x += counts.x();
            y += counts.y();
        }
        RawMouseAccumulator.Counts aim = AIM_ACCUMULATOR.consume(elapsedSeconds);
        x += aim.x();
        y += aim.y();
        RawMouseAccumulator.Counts telly = accumulator(Source.SCAFFOLD_TELLY).consume();
        x += telly.x();
        y += telly.y();
        return new Delta(x, y);
    }

    public static void clearIfUnavailable() {
        if (!canUseMouseLook()) clear();
    }

    public static void clear() {
        for (RawMouseAccumulator accumulator : ACCUMULATORS.values()) accumulator.clear();
        AIM_ACCUMULATOR.clear();
        exclusiveSource = null;
        lastConsumeNanos = 0L;
    }

    public static void clear(Source source) {
        if (source == Source.AIM_ASSIST) {
            AIM_ACCUMULATOR.clear();
            accumulator(source).clear();
            return;
        }
        if (source == Source.SCAFFOLD_TELLY) {
            accumulator(source).clear();
            return;
        }
        accumulator(source).clear();
    }

    public static void setExclusive(Source source, boolean exclusive) {
        if (exclusive) {
            if (source == null) return;
            exclusiveSource = source;
            clearExcept(source);
            return;
        }
        if (source == null || exclusiveSource == source) {
            exclusiveSource = null;
            if (source != null) clear(source);
        }
    }

    public static boolean hasExclusiveInput() {
        return exclusiveSource != null;
    }

    public static boolean hasExclusiveInput(Source source) {
        return source != null && exclusiveSource == source;
    }

    public static boolean canUseMouseLook() {
        return MC != null
            && MC.player != null
            && MC.level != null
            && MC.options != null
            && MC.getWindow() != null
            && MC.mouseHandler != null
            && MC.mouseHandler.isMouseGrabbed()
            && MC.gui.screen() == null
            && MC.gui.overlay() == null
            && !PackHideState.isActive();
    }

    private static RawMouseAccumulator accumulator(Source source) {
        return ACCUMULATORS.get(source == null ? Source.GENERIC : source);
    }

    private static RawMouseAccumulator.Counts consumeSource(Source source, double elapsedSeconds) {
        if (source == Source.AIM_ASSIST) return AIM_ACCUMULATOR.consume(elapsedSeconds);
        if (source == Source.SCAFFOLD_TELLY) return accumulator(source).consume();
        return accumulator(source).consume();
    }

    private static void clearExcept(Source retained) {
        for (Map.Entry<Source, RawMouseAccumulator> entry : ACCUMULATORS.entrySet()) {
            if (entry.getKey() != retained) entry.getValue().clear();
        }
        if (retained != Source.AIM_ASSIST) AIM_ACCUMULATOR.clear();
    }
}
