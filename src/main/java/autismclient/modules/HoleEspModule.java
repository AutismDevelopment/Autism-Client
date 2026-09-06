package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.util.AutismHoleScanner;
import autismclient.util.AutismPerf;
import autismclient.util.AutismWorldGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class HoleEspModule extends Module implements AutismHoleScanner.Subscriber {

    private static final String MODE_BOX = "Box";
    private static final String MODE_GLOWING_PLANE = "GlowingPlane";

    private static final double FACE_ALPHA = 50.0 / 255.0;
    private static final double OUTLINE_ALPHA = 100.0 / 255.0;

    private static final float LINE_WIDTH = 1.5f;

    private static final double INFLATE = 0.002;

    private static final double LIFT = 0.002;

    private static volatile boolean renderHookInstalled;
    private static HoleEspModule cachedInstance;

    private int cachedRevision = -1;
    private boolean glowingPlane = true;
    private boolean fill = true;
    private boolean outline = true;
    private int horizontal = 32;
    private int vertical = 8;
    private double distanceFade = 0.3;
    private double glowHeight = 0.7;
    private int maxHoles = 512;
    private int colorBedrock = 0xFF19C15C;
    private int colorOneByOne = 0xFFF7381B;
    private int colorOneByTwo = 0xFF35BACC;
    private int colorTwoByTwo = 0xFFF7CF1B;

    private boolean subscribed;

    public HoleEspModule() {
        super("hole-esp", "HoleESP", ModuleCategory.RENDER, "Highlights safe holes.");
        add(new ChoiceSetting("mode", "Mode", MODE_GLOWING_PLANE, MODE_BOX, MODE_GLOWING_PLANE)
            .description("Full box or glowing floor.").build());

        add(new IntSetting("horizontal-distance", "Horizontal Distance", 32, 4, 128, 1)
            .description("Scan radius on X/Z.").build());
        add(new IntSetting("vertical-distance", "Vertical Distance", 8, 4, 64, 1)
            .description("Scan radius on Y.").build());
        add(new DoubleSetting("distance-fade", "Distance Fade", 0.3, 0.0, 1.0, 0.05)
            .description("Fraction of radius that fades.").build());
        add(new BoolSetting("fill", "Fill", true)
            .description("Draw the translucent face.").build());
        add(new BoolSetting("outline", "Outline", true)
            .description("Draw the edges.").build());
        add(new DoubleSetting("glow-height", "Glow Height", 0.7, 0.0, 1.0, 0.05)
            .description("Height of the fading walls.")
            .visibleWhen(() -> !MODE_BOX.equals(choice("mode"))).build());

        add(new IntSetting("max-holes", "Max Holes", 512, 16, 4096, 16)
            .description("Most holes drawn at once.").build());
        add(new ColorSetting("color-1x1-bedrock", "1x1 Bedrock", 0xFF19C15C)
            .group("Colors").description("A 1x1 walled in bedrock.").build());
        add(new ColorSetting("color-1x1", "1x1", 0xFFF7381B)
            .group("Colors").description("A one-block hole.").build());
        add(new ColorSetting("color-1x2", "1x2", 0xFF35BACC)
            .group("Colors").description("A two-block hole.").build());
        add(new ColorSetting("color-2x2", "2x2", 0xFFF7CF1B)
            .group("Colors").description("A four-block hole.").build());
    }

    public static void initialize() {
        installRenderHook();
    }

    @Override
    public void onEnable() {
        cachedRevision = -1;
        installRenderHook();
        ensureSubscribed();
    }

    @Override
    public void onDisable() {
        cachedRevision = -1;
        release();
    }

    @Override
    public void onGameLeft() {

        cachedRevision = -1;
        release();
    }

    @Override
    public void tick() {
        ensureSubscribed();
        refreshSettings();

        AutismHoleScanner.tick();
    }

    @Override
    public String info() {
        return choice("mode");
    }

    @Override
    public int horizontalDistance() {
        refreshSettings();
        return horizontal;
    }

    @Override
    public int verticalDistance() {
        refreshSettings();
        return vertical;
    }

    private void ensureSubscribed() {
        if (subscribed) return;
        AutismHoleScanner.subscribe(this);
        subscribed = true;
    }

    private void release() {
        if (!subscribed) return;
        subscribed = false;
        AutismHoleScanner.unsubscribe(this);
    }

    private void refreshSettings() {
        int revision = ModuleRegistry.revision();
        if (revision == cachedRevision) return;
        cachedRevision = revision;
        glowingPlane = !MODE_BOX.equals(choice("mode"));
        fill = bool("fill");
        outline = bool("outline");
        horizontal = integer("horizontal-distance");
        vertical = integer("vertical-distance");
        distanceFade = decimal("distance-fade");
        glowHeight = decimal("glow-height");
        maxHoles = integer("max-holes");
        colorBedrock = ModuleRenderUtil.color(this, "color-1x1-bedrock", 0xFF19C15C);
        colorOneByOne = ModuleRenderUtil.color(this, "color-1x1", 0xFFF7381B);
        colorOneByTwo = ModuleRenderUtil.color(this, "color-1x2", 0xFF35BACC);
        colorTwoByTwo = ModuleRenderUtil.color(this, "color-2x2", 0xFFF7CF1B);
    }

    private static HoleEspModule instance() {
        HoleEspModule cached = cachedInstance;
        if (cached != null) return cached;
        Module module = ModuleRegistry.get("hole-esp");
        return module instanceof HoleEspModule hole ? (cachedInstance = hole) : null;
    }

    private static synchronized void installRenderHook() {
        if (renderHookInstalled) return;
        renderHookInstalled = true;
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            try {

                HoleEspModule module = instance();
                if (module == null || !module.isEnabled()) return;
                if (PackHideState.isActive()) return;
                if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden()) return;
                if (ModuleRenderUtil.shouldSuppressEspForUi()) return;
                module.refreshSettings();
                if (!module.fill && !module.outline) return;
                List<AutismHoleScanner.Hole> holes = AutismHoleScanner.holes();
                if (holes.isEmpty()) return;
                Vec3 camera = context.levelState().cameraRenderState.pos;
                Vec3 player = MC.player.position();
                if (module.fill) {
                    context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                        AutismRenderTypes.storageEspFillSeeThrough(),
                        (pose, buffer) -> module.emitFill(pose, buffer, holes, camera, player));
                }
                if (module.outline) {
                    context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                        AutismRenderTypes.storageEspLinesSeeThrough(),
                        (pose, buffer) -> module.emitOutline(pose, buffer, holes, camera, player));
                }
            } catch (Throwable ignored) {

            }
        });
    }

    private void emitFill(PoseStack.Pose pose, VertexConsumer buffer, List<AutismHoleScanner.Hole> holes,
                          Vec3 camera, Vec3 player) {
        long perf = AutismPerf.beginSampled();
        int drawn = 0;
        for (AutismHoleScanner.Hole hole : holes) {
            if (drawn >= maxHoles) break;
            double fade = fade(hole.pos(), player);
            if (fade <= 0.0) continue;
            drawn++;
            int color = withAlpha(colorFor(hole.type()), FACE_ALPHA * fade);
            if (color == 0) continue;
            AABB box = hole.box().move(-camera.x, -camera.y, -camera.z);
            if (glowingPlane) {
                double floorY = box.minY + LIFT;
                sheet(pose, buffer, box.minX, box.maxX, box.minZ, box.maxZ, floorY, color);
                if (glowHeight > 0.0) glow(pose, buffer, box, floorY, color);
            } else {
                fillBox(pose, buffer, box.inflate(INFLATE), color);
            }
        }
        AutismPerf.end("frame.holeEspFill", perf);
    }

    private void emitOutline(PoseStack.Pose pose, VertexConsumer buffer, List<AutismHoleScanner.Hole> holes,
                             Vec3 camera, Vec3 player) {
        long perf = AutismPerf.beginSampled();
        int drawn = 0;
        for (AutismHoleScanner.Hole hole : holes) {
            if (drawn >= maxHoles) break;
            double fade = fade(hole.pos(), player);
            if (fade <= 0.0) continue;
            drawn++;
            int color = withAlpha(colorFor(hole.type()), OUTLINE_ALPHA * fade);
            if (color == 0) continue;
            AABB box = hole.box().move(-camera.x, -camera.y, -camera.z);
            if (glowingPlane) {
                footprint(pose, buffer, box, box.minY + LIFT, color);
            } else {
                edges(pose, buffer, box.inflate(INFLATE), color);
            }
        }
        AutismPerf.end("frame.holeEspOutline", perf);
    }

    private int colorFor(AutismHoleScanner.HoleType type) {
        return switch (type) {
            case ONE_BY_ONE_BEDROCK -> colorBedrock;
            case ONE_BY_ONE -> colorOneByOne;
            case ONE_BY_TWO -> colorOneByTwo;
            case TWO_BY_TWO -> colorTwoByTwo;
        };
    }

    private double fade(BlockPos pos, Vec3 player) {
        double dx = player.x - pos.getX();
        double dy = player.y - pos.getY();
        double dz = player.z - pos.getZ();
        if (Math.abs(dy) > vertical || Math.abs(dx) > horizontal || Math.abs(dz) > horizontal) return 0.0;
        if (distanceFade <= 0.0) return 1.0;
        double verticalFraction = dy / vertical;
        double horizontalFraction = Math.sqrt(dx * dx + dz * dz) / horizontal;
        double fade = (1.0 - Math.max(verticalFraction, horizontalFraction)) / distanceFade;
        return fade <= 0.0 ? 0.0 : Math.min(1.0, fade);
    }

    private static int withAlpha(int argb, double multiplier) {
        int alpha = (int) Math.round(((argb >>> 24) & 0xFF) * multiplier);
        if (alpha <= 0) return 0;
        return (Math.min(255, alpha) << 24) | (argb & 0x00FFFFFF);
    }

    private static void sheet(PoseStack.Pose pose, VertexConsumer buffer,
                              double minX, double maxX, double minZ, double maxZ, double y, int color) {
        quad(pose, buffer, minX, y, minZ, maxX, y, minZ, maxX, y, maxZ, minX, y, maxZ, color);
        quad(pose, buffer, minX, y, maxZ, maxX, y, maxZ, maxX, y, minZ, minX, y, minZ, color);
    }

    private void glow(PoseStack.Pose pose, VertexConsumer buffer, AABB box, double baseY, int color) {
        int top = color & 0x00FFFFFF;
        double topY = baseY + glowHeight;
        gradientSide(pose, buffer, box.minX, box.minZ, box.maxX, box.minZ, baseY, topY, color, top);
        gradientSide(pose, buffer, box.maxX, box.minZ, box.maxX, box.maxZ, baseY, topY, color, top);
        gradientSide(pose, buffer, box.maxX, box.maxZ, box.minX, box.maxZ, baseY, topY, color, top);
        gradientSide(pose, buffer, box.minX, box.maxZ, box.minX, box.minZ, baseY, topY, color, top);
    }

    private static void gradientSide(PoseStack.Pose pose, VertexConsumer buffer,
                                     double x1, double z1, double x2, double z2,
                                     double bottomY, double topY, int bottom, int top) {
        quad(pose, buffer, x1, bottomY, z1, bottom, x2, bottomY, z2, bottom,
            x2, topY, z2, top, x1, topY, z1, top);
        quad(pose, buffer, x1, topY, z1, top, x2, topY, z2, top,
            x2, bottomY, z2, bottom, x1, bottomY, z1, bottom);
    }

    private static void footprint(PoseStack.Pose pose, VertexConsumer buffer, AABB box, double y, int color) {
        AutismWorldGeometry.line(pose, buffer, box.minX, y, box.minZ, box.maxX, y, box.minZ, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, box.maxX, y, box.minZ, box.maxX, y, box.maxZ, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, box.maxX, y, box.maxZ, box.minX, y, box.maxZ, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, box.minX, y, box.maxZ, box.minX, y, box.minZ, color, LINE_WIDTH);
    }

    private static void edges(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        AutismWorldGeometry.line(pose, buffer, x1, y1, z1, x2, y1, z1, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, x2, y1, z1, x2, y1, z2, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, x2, y1, z2, x1, y1, z2, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, x1, y1, z2, x1, y1, z1, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, x1, y2, z1, x2, y2, z1, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, x2, y2, z1, x2, y2, z2, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, x2, y2, z2, x1, y2, z2, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, x1, y2, z2, x1, y2, z1, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, x1, y1, z1, x1, y2, z1, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, x2, y1, z1, x2, y2, z1, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, x2, y1, z2, x2, y2, z2, color, LINE_WIDTH);
        AutismWorldGeometry.line(pose, buffer, x1, y1, z2, x1, y2, z2, color, LINE_WIDTH);
    }

    private static void fillBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ,
            box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color);
        quad(pose, buffer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ,
            box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
            box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ,
            box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ,
            box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ,
            box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4, int color) {
        quad(pose, buffer, x1, y1, z1, color, x2, y2, z2, color, x3, y3, z3, color, x4, y4, z4, color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             double x1, double y1, double z1, int c1,
                             double x2, double y2, double z2, int c2,
                             double x3, double y3, double z3, int c3,
                             double x4, double y4, double z4, int c4) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(c1);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(c2);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(c3);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(c4);
    }
}
