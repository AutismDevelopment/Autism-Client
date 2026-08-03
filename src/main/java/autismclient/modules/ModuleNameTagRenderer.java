package autismclient.modules;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismColors;
import autismclient.util.AutismUiScale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleNameTagRenderer {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final int LINE_H = 10;
    private static final int BG_COLOR = 0x90000000;

    private ModuleNameTagRenderer() {
    }

    public static void render(GuiGraphicsExtractor context) {
        if (PackHideState.isActive()) return;
        if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden()) return;
        Module module = ModuleRegistry.get("nametags");
        if (module == null || !module.isEnabled()) return;
        Camera camera = MC.gameRenderer.mainCamera();
        if (camera == null) return;

        boolean players = module.bool("players");
        boolean mobs = module.bool("mobs");
        boolean items = module.bool("items");
        if (!players && !mobs && !items) return;
        boolean showHealth = module.bool("show-health");
        boolean showDistance = module.bool("show-distance");
        boolean distanceScale = module.bool("distance-scale");
        boolean groupItems = module.bool("group-items");
        double baseScale = module.decimal("scale");
        double maxDist = module.decimal("max-distance");
        double groupRadius = module.decimal("group-radius");

        Projection projection = new Projection(
            camera.position(),
            camera.getViewRotationProjectionMatrix(new Matrix4f()),
            AutismUiScale.getVirtualScreenWidth(),
            AutismUiScale.getVirtualScreenHeight()
        );
        float tickDelta = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camPos = camera.position();
        Entity self = MC.player;

        List<ItemEntity> itemEntities = items ? new ArrayList<>() : null;

        for (Entity entity : MC.level.entitiesForRendering()) {
            try {
                if (entity == self) continue;
                if (AutismAntiBot.suppress(entity)) continue;

                boolean friend = TeamsModule.isFriendOrTeam(entity);
                if (friend && !TeamsModule.visualTargetsFriends("nametags")) continue;
                double dist = Math.sqrt(entity.distanceToSqr(camPos));
                if (maxDist > 0 && dist > maxDist) continue;

                if (entity instanceof ItemEntity item) {
                    if (items) itemEntities.add(item);
                    continue;
                }
                boolean isPlayer = entity instanceof Player;
                if (isPlayer) {
                    if (!players) continue;
                } else if (!mobs || !(entity instanceof LivingEntity)) {
                    continue;
                }

                float[] screen = projectHead(entity, tickDelta, projection);
                if (screen == null) continue;
                Component label = buildEntityLabel(entity, showHealth, showDistance, dist);
                if (friend) {

                    label = label.copy().withStyle(style ->
                        style.withColor(TextColor.fromRgb(TeamsModule.friendsColor() & 0xFFFFFF)));
                }
                drawLabel(context, screen[0], screen[1], List.of(label.getVisualOrderText()), 0xFFFFFFFF, scaleFor(baseScale, distanceScale, dist));
            } catch (Throwable t) {

            }
        }

        if (items && !itemEntities.isEmpty()) {
            renderItems(context, itemEntities, groupItems, groupRadius, baseScale, distanceScale, camPos, tickDelta, projection);
        }
    }

    private static int tagSnapshotRevision = -1;
    private static boolean tagEnabled;
    private static boolean tagPlayers;
    private static boolean tagMobs;
    private static boolean tagItems;
    private static double tagMaxDistSq;

    private static void refreshTagSnapshot() {
        int revision = ModuleRegistry.revision();
        if (revision == tagSnapshotRevision) return;
        tagSnapshotRevision = revision;
        Module module = ModuleRegistry.get("nametags");
        tagEnabled = module != null && module.isEnabled();
        tagPlayers = tagEnabled && module.bool("players");
        tagMobs = tagEnabled && module.bool("mobs");
        tagItems = tagEnabled && module.bool("items");
        double maxDist = tagEnabled ? module.decimal("max-distance") : 0.0;
        tagMaxDistSq = maxDist > 0 ? maxDist * maxDist : 0.0;
    }

    public static boolean tags(Entity entity) {
        refreshTagSnapshot();
        if (!tagEnabled) return false;
        if (PackHideState.isActive()) return false;
        if (MC == null || MC.player == null || entity == null || entity == MC.player) return false;
        if (AutismAntiBot.isBot(entity)) return false;

        if (TeamsModule.isFriendOrTeam(entity) && !TeamsModule.visualTargetsFriends("nametags")) return false;
        if (entity instanceof ItemEntity) {
            if (!tagItems) return false;
        } else if (entity instanceof Player) {
            if (!tagPlayers) return false;
        } else if (!(entity instanceof LivingEntity) || !tagMobs) {
            return false;
        }
        if (tagMaxDistSq > 0) {
            Camera cam = MC.gameRenderer.mainCamera();
            if (cam != null && entity.distanceToSqr(cam.position()) > tagMaxDistSq) return false;
        }
        return true;
    }

    private static Component buildEntityLabel(Entity entity, boolean showHealth, boolean showDistance, double dist) {
        MutableComponent label = entity.getDisplayName().copy();
        if (showHealth && entity instanceof LivingEntity living) {
            label.append(Component.literal("  " + (int) Math.ceil(living.getHealth()) + "HP"));
        }
        if (showDistance) label.append(Component.literal("  " + (int) dist + "m"));
        return label;
    }

    private static void renderItems(GuiGraphicsExtractor context, List<ItemEntity> itemEntities, boolean group,
                                    double groupRadius, double baseScale, boolean distanceScale, Vec3 camPos,
                                    float tickDelta, Projection projection) {
        boolean[] visited = new boolean[itemEntities.size()];
        double r2 = groupRadius * groupRadius;
        Style countStyle = Style.EMPTY.withColor(TextColor.fromRgb(AutismColors.accent() & 0xFFFFFF));
        for (int i = 0; i < itemEntities.size(); i++) {
          try {
            if (visited[i]) continue;
            ItemEntity base = itemEntities.get(i);
            visited[i] = true;
            List<ItemEntity> cluster = new ArrayList<>();
            cluster.add(base);
            if (group) {
                for (int j = i + 1; j < itemEntities.size(); j++) {
                    if (!visited[j] && base.distanceToSqr(itemEntities.get(j)) <= r2) {
                        visited[j] = true;
                        cluster.add(itemEntities.get(j));
                    }
                }
            }

            Map<String, Agg> agg = new LinkedHashMap<>();
            double cx = 0, cy = 0, cz = 0;
            for (ItemEntity ie : cluster) {
                ItemStack st = ie.getItem();
                Component name = st.getHoverName();
                agg.computeIfAbsent(name.getString(), k -> new Agg(name)).count += st.getCount();
                cx += Mth.lerp(tickDelta, ie.xOld, ie.getX());
                cy += Mth.lerp(tickDelta, ie.yOld, ie.getY());
                cz += Mth.lerp(tickDelta, ie.zOld, ie.getZ());
            }
            int n = cluster.size();
            cx /= n;
            cy = cy / n + 0.5;
            cz /= n;
            float[] screen = project(cx, cy, cz, projection);
            if (screen == null) continue;

            List<Agg> aggs = new ArrayList<>(agg.values());
            aggs.sort((a, b) -> Integer.compare(b.count, a.count));
            List<FormattedCharSequence> lines = new ArrayList<>();
            int max = Math.min(aggs.size(), 6);
            for (int k = 0; k < max; k++) {
                Agg a = aggs.get(k);

                lines.add(a.name.copy().append(Component.literal(" ×" + a.count).withStyle(countStyle)).getVisualOrderText());
            }
            if (aggs.size() > max) lines.add(Component.literal("+" + (aggs.size() - max) + " more").getVisualOrderText());

            double dist = Math.sqrt(distSq(camPos, cx, cy, cz));
            drawLabel(context, screen[0], screen[1], lines, 0xFFFFFFFF, scaleFor(baseScale, distanceScale, dist));
          } catch (Throwable t) {

          }
        }
    }

    private static float scaleFor(double baseScale, boolean distanceScale, double dist) {
        float s = (float) baseScale;
        if (distanceScale) s *= (float) Mth.clamp(12.0 / Math.max(1.0, dist), 0.4, 1.2);
        return Math.max(0.05f, s);
    }

    private static void drawLabel(GuiGraphicsExtractor context, float screenX, float screenY, List<FormattedCharSequence> lines, int color, float scale) {
        if (lines.isEmpty()) return;
        context.pose().pushMatrix();
        context.pose().scale(scale, scale);
        int ox = Math.round(screenX / scale);
        int oy = Math.round(screenY / scale);
        int w = 0;
        for (FormattedCharSequence line : lines) w = Math.max(w, MC.font.width(line));
        int totalH = lines.size() * LINE_H;
        int top = oy - totalH - 2;
        UiRenderer.rect(context, UiBounds.of(ox - w / 2 - 2, top, w + 4, totalH + 2), BG_COLOR);
        int ty = top + 2;
        for (FormattedCharSequence line : lines) {
            context.text(MC.font, line, ox - MC.font.width(line) / 2, ty, color, true);
            ty += LINE_H;
        }
        context.pose().popMatrix();
    }

    private static final class Agg {
        final Component name;
        int count;
        Agg(Component name) { this.name = name; }
    }

    private static float[] projectHead(Entity entity, float tickDelta, Projection p) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY()) + entity.getDimensions(entity.getPose()).height() + 0.45;
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        return project(x, y, z, p);
    }

    private static float[] project(double worldX, double worldY, double worldZ, Projection p) {
        Vec3 cam = p.cameraPosition();
        Vector4f v = new Vector4f((float) (worldX - cam.x), (float) (worldY - cam.y), (float) (worldZ - cam.z), 1.0f);
        p.matrix().transform(v);
        if (v.w <= 0.001f) return null;
        float ndcX = v.x / v.w;
        float ndcY = v.y / v.w;
        if (Float.isNaN(ndcX) || Float.isNaN(ndcY) || Float.isInfinite(ndcX) || Float.isInfinite(ndcY)) return null;
        float sx = (ndcX * 0.5f + 0.5f) * p.screenWidth();
        float sy = (0.5f - ndcY * 0.5f) * p.screenHeight();
        return new float[]{sx, sy};
    }

    private static double distSq(Vec3 cam, double x, double y, double z) {
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private record Projection(Vec3 cameraPosition, Matrix4f matrix, int screenWidth, int screenHeight) {
    }
}
