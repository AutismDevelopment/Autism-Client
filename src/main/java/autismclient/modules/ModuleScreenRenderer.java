package autismclient.modules;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismPerf;
import autismclient.util.AutismUiScale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ModuleScreenRenderer {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Map<Integer, SmoothedScreenBox> SMOOTHED_BOXES = new HashMap<>();

    private static final ScreenBox SCRATCH_BOX = new ScreenBox();
    private static Object lastLevel;
    private static long frameIndex;

    private ModuleScreenRenderer() {
    }

    public static void render(GuiGraphicsExtractor context) {
        if (PackHideState.isActive()) return;
        if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden()) return;
        if (!ModuleRenderUtil.has2dEspWork()) return;
        if (ModuleRenderUtil.shouldSuppressEspForUi()) return;
        long perf = AutismPerf.begin();
        try {
            renderEsp2d(context);
        } finally {
            AutismPerf.end("modules.esp2d", perf);
        }
    }

    private static void renderEsp2d(GuiGraphicsExtractor context) {
        Camera camera = MC.gameRenderer.mainCamera();
        if (camera == null || MC.getWindow() == null) return;
        if (lastLevel != MC.level) {
            SMOOTHED_BOXES.clear();
            lastLevel = MC.level;
        }
        long frame = ++frameIndex;
        Projection projection = new Projection(
            camera.position(),
            camera.getViewRotationProjectionMatrix(new Matrix4f()),
            AutismUiScale.getVirtualScreenWidth(),
            AutismUiScale.getVirtualScreenHeight()
        );
        float tickDelta = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Module espModule = ModuleRegistry.get("esp");
        boolean fill = espModule != null && espModule.bool("fill");

        for (Entity entity : MC.level.entitiesForRendering()) {
            try {
                if (!ModuleRenderUtil.shouldEsp(entity)) continue;
                ScreenBox box = projectBox(entity, tickDelta, projection);
                if (box == null) continue;
                box = smoothBox(entity, box, frame);
                int color = ModuleRenderUtil.espColor(entity);
                drawLiquidBounce2dBox(context, box, color, entity, fill);
            } catch (Throwable t) {

            }
        }
        pruneSmoothingCache(frame);
    }

    private static ScreenBox projectBox(Entity entity, float tickDelta, Projection projection) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        double halfWidth = dimensions.width() / 2.0;
        double minX = x - halfWidth - 0.05;
        double minY = y - 0.05;
        double minZ = z - halfWidth - 0.05;
        double maxX = x + halfWidth + 0.05;
        double maxY = y + dimensions.height() + 0.05;
        double maxZ = z + halfWidth + 0.05;
        ScreenBox out = SCRATCH_BOX;
        out.reset();
        out.include(minX, minY, minZ, projection);
        out.include(maxX, minY, minZ, projection);
        out.include(minX, minY, maxZ, projection);
        out.include(maxX, minY, maxZ, projection);
        out.include(minX, maxY, minZ, projection);
        out.include(maxX, maxY, minZ, projection);
        out.include(minX, maxY, maxZ, projection);
        out.include(maxX, maxY, maxZ, projection);
        return out.isValid() ? out : null;
    }

    private static ScreenBox smoothBox(Entity entity, ScreenBox raw, long frame) {
        int id = entity.getId();
        SmoothedScreenBox cached = SMOOTHED_BOXES.get(id);
        if (cached == null || cached.shouldReset(raw)) {
            cached = new SmoothedScreenBox(raw);
            SMOOTHED_BOXES.put(id, cached);
        } else {
            cached.lerpTo(raw);
        }
        cached.lastFrame = frame;
        cached.applyTo(raw);
        return raw;
    }

    private static void pruneSmoothingCache(long frame) {
        if ((frame & 31L) != 0L) return;
        Iterator<Map.Entry<Integer, SmoothedScreenBox>> iterator = SMOOTHED_BOXES.entrySet().iterator();
        while (iterator.hasNext()) {
            if (frame - iterator.next().getValue().lastFrame > 10L) iterator.remove();
        }
    }

    private static void drawLiquidBounce2dBox(GuiGraphicsExtractor context, ScreenBox box, int color, Entity entity, boolean fill) {
        int x = Mth.floor(box.minX);
        int y = Mth.floor(box.minY);
        int w = Mth.ceil(box.maxX) - x;
        int h = Mth.ceil(box.maxY) - y;
        if (w <= 0 || h <= 0) return;
        int rgb = color & 0x00FFFFFF;
        int outline = 0xFF000000 | rgb;
        if (fill) {
            int fillColor = (50 << 24) | rgb;
            UiRenderer.rect(context, UiBounds.of(x, y, w, h), fillColor);
        }
        drawBorderedRectOutline(context, x, y, w, h, outline);
        if (entity instanceof LivingEntity living) {
            drawHealthBar(context, x, y, h, living);
        }
    }

    private static void drawBorderedRectOutline(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        int black = 0xFF000000;
        UiRenderer.rect(context, UiBounds.of(x - 1, y - 1, w + 2, 3), black);
        UiRenderer.rect(context, UiBounds.of(x - 1, y - 1, 3, h + 2), black);
        UiRenderer.rect(context, UiBounds.of(x - 1, y + h - 2, w + 2, 3), black);
        UiRenderer.rect(context, UiBounds.of(x + w - 2, y - 1, 3, h + 2), black);
        UiRenderer.outline(context, UiBounds.of(x, y, w, h), color);
    }

    private static void drawHealthBar(GuiGraphicsExtractor context, int x, int y, int h, LivingEntity entity) {
        float maxHealth = Math.max(1.0f, entity.getMaxHealth());
        float health = Mth.clamp(entity.getHealth() / maxHealth, 0.0f, 1.0f);
        int filled = Math.max(0, Math.min(h, Math.round(h * health)));
        int barX = x - 5;
        UiRenderer.rect(context, UiBounds.of(barX - 1, y - 1, 3, h + 2), 0xFF000000);
        if (filled <= 0) return;
        int red = Math.round(255.0f * (1.0f - health));
        int green = Math.round(255.0f * health);
        int healthColor = 0xFF000000 | (red << 16) | (green << 8);
        UiRenderer.rect(context, UiBounds.of(barX, y + h - filled, 1, filled), healthColor);
    }

    private static final class ScreenBox {
        private final Vector4f vector = new Vector4f();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        void reset() {
            minX = Float.MAX_VALUE;
            minY = Float.MAX_VALUE;
            maxX = -Float.MAX_VALUE;
            maxY = -Float.MAX_VALUE;
        }

        boolean include(double worldX, double worldY, double worldZ, Projection projection) {
            Vec3 cam = projection.cameraPosition();
            vector.set((float) (worldX - cam.x), (float) (worldY - cam.y), (float) (worldZ - cam.z), 1.0f);
            projection.matrix().transform(vector);
            if (vector.w <= 0.001f) return false;
            float ndcX = vector.x / vector.w;
            float ndcY = vector.y / vector.w;
            if (Float.isNaN(ndcX) || Float.isNaN(ndcY) || Float.isInfinite(ndcX) || Float.isInfinite(ndcY)) return false;
            float x = (ndcX * 0.5f + 0.5f) * projection.screenWidth();
            float y = (0.5f - ndcY * 0.5f) * projection.screenHeight();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            return true;
        }

        boolean isValid() {
            return maxX > minX && maxY > minY;
        }
    }

    private static final class SmoothedScreenBox {
        private static final float SMOOTHING = 0.42f;
        private static final float RESET_DISTANCE = 42.0f;
        private static final float RESET_SIZE_DELTA = 24.0f;

        float minX;
        float minY;
        float maxX;
        float maxY;
        long lastFrame;

        SmoothedScreenBox(ScreenBox raw) {
            set(raw);
        }

        void set(ScreenBox raw) {
            minX = raw.minX;
            minY = raw.minY;
            maxX = raw.maxX;
            maxY = raw.maxY;
        }

        void lerpTo(ScreenBox raw) {
            minX += (raw.minX - minX) * SMOOTHING;
            minY += (raw.minY - minY) * SMOOTHING;
            maxX += (raw.maxX - maxX) * SMOOTHING;
            maxY += (raw.maxY - maxY) * SMOOTHING;
        }

        boolean shouldReset(ScreenBox raw) {
            float centerDx = ((raw.minX + raw.maxX) - (minX + maxX)) * 0.5f;
            float centerDy = ((raw.minY + raw.maxY) - (minY + maxY)) * 0.5f;
            float widthDelta = Math.abs((raw.maxX - raw.minX) - (maxX - minX));
            float heightDelta = Math.abs((raw.maxY - raw.minY) - (maxY - minY));
            return (centerDx * centerDx) + (centerDy * centerDy) > RESET_DISTANCE * RESET_DISTANCE
                || widthDelta > RESET_SIZE_DELTA
                || heightDelta > RESET_SIZE_DELTA;
        }

        void applyTo(ScreenBox box) {
            box.minX = minX;
            box.minY = minY;
            box.maxX = maxX;
            box.maxY = maxY;
        }
    }

    private record Projection(Vec3 cameraPosition, Matrix4f matrix, int screenWidth, int screenHeight) {
    }
}
