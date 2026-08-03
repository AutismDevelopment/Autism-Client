package autismclient.modules;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import autismclient.AutismClientAddon;
import autismclient.util.AutismBufferSource;
import autismclient.util.AutismPerf;
import autismclient.util.AutismWaypoints;
import autismclient.util.AutismWorldGeometry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ModuleWorldRenderer {
    private static boolean initialized;
    private static StorageSnapshot storageSnapshot = StorageSnapshot.empty();
    private static StorageSnapshot blockSnapshot = StorageSnapshot.empty();
    private static StorageSnapshot barrierSnapshot = StorageSnapshot.empty();
    private static StorageSnapshot spawnerSnapshot = StorageSnapshot.empty();
    private static WaypointSnapshot waypointSnapshot = WaypointSnapshot.empty();
    private static ClientLevel tracerSelectionLevel;
    private static long tracerSelectionGameTime = Long.MIN_VALUE;
    private static int tracerSelectionRevision = Integer.MIN_VALUE;
    private static List<EntityTraceTarget> tracerSelection = List.of();

    private static List<TracerLine> pendingTracerLines = List.of();
    private static final ArrayList<TracerLine> TRACER_LINES_SCRATCH = new ArrayList<>();
    private static Vec3 pendingTracerCamera = Vec3.ZERO;

    private static WaypointSnapshot pendingWaypointFrame = WaypointSnapshot.empty();
    private static Vec3 pendingWaypointCamera = Vec3.ZERO;
    private static final Quaternionf pendingWaypointOrientation = new Quaternionf();

    private static StorageSnapshot pendingEspStorageFrame = StorageSnapshot.empty();
    private static StorageSnapshot pendingEspBlockFrame = StorageSnapshot.empty();
    private static StorageSnapshot pendingEspBarrierFrame = StorageSnapshot.empty();
    private static StorageSnapshot pendingEspSpawnerFrame = StorageSnapshot.empty();
    private static List<TrajectoriesModule.Path> pendingTrajectories = List.of();
    private static AABB pendingEspAirBox;
    private static int pendingEspAirColor;
    private static AABB pendingEspTpClickBox;
    private static int pendingEspTpClickColor;
    private static boolean pendingEspTpClickFill;
    private static boolean pendingEspTpClickWire;
    private static boolean pendingEspStorageFill;
    private static boolean pendingEspBlockFill;
    private static boolean pendingEspBarrierFill;
    private static boolean pendingEspSpawnerFill;
    private static boolean pendingEspAirFill;
    private static boolean pendingEspStorageWire;
    private static boolean pendingEspBlockWire;
    private static boolean pendingEspSpawnerWire;
    private static boolean pendingEspAirWire;
    private static Vec3 pendingEspCamera = Vec3.ZERO;

    private static Module cachedTracers;
    private static Module cachedStorageEsp;
    private static Module cachedBlockEsp;
    private static Module cachedSpawnerEsp;
    private static AirPlaceModule cachedAirPlace;
    private static TpClickModule cachedTpClick;
    private static Module cachedWaypoints;

    private static int cachedEspFlagsRevision = -1;
    private static boolean cachedStorageFill;
    private static boolean cachedStorageTrace;
    private static boolean cachedBlockFill;
    private static boolean cachedBlockTrace;
    private static boolean cachedBlockBarrier;
    private static boolean cachedSpawnerFill;
    private static boolean cachedSpawnerTrace;

    private static Module tracersModule() {
        Module m = cachedTracers;
        return m != null ? m : (cachedTracers = ModuleRegistry.get("tracers"));
    }

    private static Module storageEspModule() {
        Module m = cachedStorageEsp;
        return m != null ? m : (cachedStorageEsp = ModuleRegistry.get("storage-esp"));
    }

    private static Module spawnerEspModule() {
        Module m = cachedSpawnerEsp;
        return m != null ? m : (cachedSpawnerEsp = ModuleRegistry.get("spawner-esp"));
    }

    private static Module blockEspModule() {
        Module m = cachedBlockEsp;
        return m != null ? m : (cachedBlockEsp = ModuleRegistry.get("block-esp"));
    }

    private static AirPlaceModule airPlaceModule() {
        AirPlaceModule cached = cachedAirPlace;
        if (cached != null) return cached;
        Module module = ModuleRegistry.get("air-place");
        return module instanceof AirPlaceModule airPlace ? (cachedAirPlace = airPlace) : null;
    }

    private static TpClickModule tpClickModule() {
        TpClickModule cached = cachedTpClick;
        if (cached != null) return cached;
        Module module = ModuleRegistry.get("tp-click");
        return module instanceof TpClickModule tpClick ? (cachedTpClick = tpClick) : null;
    }

    private static Module waypointsModule() {
        Module m = cachedWaypoints;
        return m != null ? m : (cachedWaypoints = ModuleRegistry.get("waypoints"));
    }

    private static void refreshEspFlags(Module storage, Module blockEsp, Module spawnerEsp) {
        int rev = ModuleRegistry.revision();
        if (rev == cachedEspFlagsRevision) return;
        cachedEspFlagsRevision = rev;
        cachedStorageFill = storage != null && Boolean.parseBoolean(storage.value("fill"));
        cachedStorageTrace = storage != null && Boolean.parseBoolean(storage.value("tracers"));
        cachedBlockFill = blockEsp != null && Boolean.parseBoolean(blockEsp.value("fill"));
        cachedBlockTrace = blockEsp != null && Boolean.parseBoolean(blockEsp.value("tracers"));
        cachedBlockBarrier = blockEsp != null && Boolean.parseBoolean(blockEsp.value("barriers"));
        cachedSpawnerFill = spawnerEsp != null && Boolean.parseBoolean(spawnerEsp.value("fill"));
        cachedSpawnerTrace = spawnerEsp != null && Boolean.parseBoolean(spawnerEsp.value("tracers"));
    }

    private ModuleWorldRenderer() {
    }

    static void initialize() {
        if (initialized) return;
        initialized = true;
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {

            pendingTracerLines = List.of();
            pendingWaypointFrame = WaypointSnapshot.empty();
            pendingEspStorageFill = pendingEspBlockFill = pendingEspBarrierFill = pendingEspSpawnerFill = pendingEspAirFill = false;
            pendingEspStorageWire = pendingEspBlockWire = pendingEspSpawnerWire = pendingEspAirWire = false;
            pendingEspTpClickFill = pendingEspTpClickWire = false;
            pendingTrajectories = List.of();
            pendingEspAirBox = null;
            pendingEspTpClickBox = null;
            Minecraft mc = Minecraft.getInstance();
            if (PackHideState.isActive()) return;
            if (mc == null || mc.level == null || mc.player == null || mc.gui.hud.isHidden()) return;
            boolean drawTracers = ModuleRenderUtil.hasWorldTracerWork();
            Module storage = storageEspModule();
            Module blockEsp = blockEspModule();
            Module spawnerEsp = spawnerEspModule();
            AirPlaceModule airPlace = airPlaceModule();
            Module waypoints = waypointsModule();
            boolean storageEnabled = storage != null && storage.isEnabled();
            boolean blockEnabled = blockEsp != null && blockEsp.isEnabled();
            boolean spawnerEnabled = spawnerEsp != null && spawnerEsp.isEnabled();
            boolean waypointsEnabled = waypoints != null && waypoints.isEnabled();
            BlockPos airTarget = airPlace != null && airPlace.isEnabled() ? airPlace.renderTarget() : null;
            boolean airGuide = airTarget != null;
            TpClickModule tpClick = tpClickModule();
            AABB tpClickBox = tpClick != null && tpClick.isEnabled() ? tpClick.highlightBox() : null;
            WaypointSnapshot waypointFrame = waypointsEnabled
                ? waypointSnapshot(waypoints, mc, mc.level) : WaypointSnapshot.empty();
            List<WaypointDot> waypointBoxes = waypointFrame.dots();
            if (!drawTracers && !storageEnabled && !blockEnabled && !spawnerEnabled && !airGuide && tpClickBox == null && waypointBoxes.isEmpty()) return;
            if (ModuleRenderUtil.shouldSuppressEspForUi()) return;
            Module tracer = tracersModule();

            refreshEspFlags(storage, blockEsp, spawnerEsp);
            boolean storageFill = storageEnabled && cachedStorageFill;
            boolean storageWire = storageEnabled;
            boolean storageTrace = storageEnabled && cachedStorageTrace;
            boolean blockFill = blockEnabled && cachedBlockFill;
            boolean blockWire = blockEnabled;
            boolean blockTrace = blockEnabled && cachedBlockTrace;
            boolean barrierFill = blockEnabled && cachedBlockBarrier;
            boolean spawnerFill = spawnerEnabled && cachedSpawnerFill;
            boolean spawnerWire = spawnerEnabled;
            boolean spawnerTrace = spawnerEnabled && cachedSpawnerTrace;
            boolean airFill = airGuide && airPlace.renderFill();
            if (!drawTracers && !storageFill && !storageWire && !storageTrace
                && !blockFill && !blockWire && !blockTrace && !barrierFill
                && !spawnerFill && !spawnerWire && !spawnerTrace && !airGuide && tpClickBox == null && waypointBoxes.isEmpty()) return;
            CameraRenderState cameraState = context.levelState().cameraRenderState;
            Vec3 camera = cameraState.pos;
            float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

            Vec3 traceStart = camera.add(cameraForward(cameraState).scale(10.0));
            float tracerWidth = parseFloat(tracer == null ? null : tracer.value("line-width"), 2.0f, 2.0f, 6.0f);
            StorageSnapshot storageFrame = storageEnabled ? storageSnapshot(storage, mc.level, mc.player, tickDelta) : StorageSnapshot.empty();
            StorageSnapshot blockFrame = blockEnabled ? blockSnapshot(blockEsp, mc.level, mc.player) : StorageSnapshot.empty();
            StorageSnapshot barrierFrame = barrierFill ? barrierSnapshot(blockEsp, mc.level, mc.player) : StorageSnapshot.empty();
            StorageSnapshot spawnerFrame = spawnerEnabled ? spawnerSnapshot(spawnerEsp, mc.level, mc.player) : StorageSnapshot.empty();
            AABB airBox = airGuide ? new AABB(airTarget).inflate(0.002D) : null;
            int airColor = airGuide ? airPlace.guideColor() : 0;
            TRACER_LINES_SCRATCH.clear();
            List<TracerLine> tracerLines = TRACER_LINES_SCRATCH;
            if (drawTracers) {
                boolean heightLine = Boolean.parseBoolean(tracer == null ? null : tracer.value("height-line"));
                collectEntityTracers(mc.level, traceStart, tickDelta, tracerWidth, heightLine, tracerLines);
            }
            if (storageTrace && storageFrame != null) {
                final float traceW = 2.0f;
                storageFrame.forEachTrace((target, color) -> tracerLines.add(new TracerLine(traceStart, target, color, traceW)));
            }
            if (blockTrace && blockFrame != null) {
                final float traceW = 2.0f;
                blockFrame.forEachTrace((target, color) -> tracerLines.add(new TracerLine(traceStart, target, color, traceW)));
            }
            if (spawnerTrace && spawnerFrame != null) {
                final float traceW = 2.0f;
                spawnerFrame.forEachTrace((target, color) -> tracerLines.add(new TracerLine(traceStart, target, color, traceW)));
            }
            if (!tracerLines.isEmpty()) {

                pendingTracerLines = tracerLines;
                pendingTracerCamera = camera;
            }
            boolean waypointDraw = !waypointBoxes.isEmpty();

            pendingEspStorageFill = storageFill;
            pendingEspBlockFill = blockFill;
            pendingEspBarrierFill = barrierFill;
            pendingEspSpawnerFill = spawnerFill;
            pendingEspAirFill = airFill;
            pendingEspStorageWire = storageWire;
            pendingEspBlockWire = blockWire;
            pendingEspSpawnerWire = spawnerWire;
            pendingEspAirWire = airGuide;
            pendingEspStorageFrame = storageFrame;
            pendingEspBlockFrame = blockFrame;
            pendingEspBarrierFrame = barrierFrame;
            pendingEspSpawnerFrame = spawnerFrame;
            pendingEspAirBox = airBox;
            pendingEspAirColor = airColor;
            pendingEspTpClickFill = pendingEspTpClickWire = tpClickBox != null;
            pendingEspTpClickBox = tpClickBox;
            pendingEspTpClickColor = tpClickBox != null ? tpClick.highlightColor() : 0;
            pendingEspCamera = camera;

            try {
                TrajectoriesModule.collect(tickDelta);
            } catch (Throwable error) {
                setTrajectoryPaths(List.of());
                autismclient.AutismClientAddon.LOG.debug("Trajectory collect failed", error);
            }
            if (waypointDraw) {

                pendingWaypointFrame = waypointFrame;
                pendingWaypointCamera = camera;
                pendingWaypointOrientation.set(cameraState.orientation);
            }
        });
    }

    public static boolean hasPendingTracers() {
        return !pendingTracerLines.isEmpty();
    }

    public static void flushTracers(PoseStack matrices) {
        List<TracerLine> lines = pendingTracerLines;
        if (lines.isEmpty()) return;
        pendingTracerLines = List.of();
        Vec3 cam = pendingTracerCamera;
        AutismBufferSource bufferSource = new AutismBufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(AutismRenderTypes.tracerEspLines());
        PoseStack.Pose pose = matrices.last();
        for (TracerLine seg : lines) {
            drawTracerLine(pose, buffer,
                seg.from().x - cam.x, seg.from().y - cam.y, seg.from().z - cam.z,
                seg.to().x - cam.x, seg.to().y - cam.y, seg.to().z - cam.z,
                seg.color(), seg.width());
        }
        bufferSource.uploadAndDraw();
    }

    public static void setTrajectoryPaths(List<TrajectoriesModule.Path> paths) {
        pendingTrajectories = paths == null ? List.of() : paths;
    }

    public static boolean hasPendingEspWork() {
        return pendingEspStorageFill || pendingEspBlockFill || pendingEspBarrierFill || pendingEspSpawnerFill || pendingEspAirFill
            || pendingEspStorageWire || pendingEspBlockWire || pendingEspSpawnerWire || pendingEspAirWire
            || pendingEspTpClickFill || pendingEspTpClickWire || !pendingTrajectories.isEmpty();
    }

    public static void flushEsp(PoseStack matrices) {
        if (!hasPendingEspWork()) return;
        boolean storageFill = pendingEspStorageFill;
        boolean blockFill = pendingEspBlockFill;
        boolean barrierFill = pendingEspBarrierFill;
        boolean spawnerFill = pendingEspSpawnerFill;
        boolean airFill = pendingEspAirFill;
        boolean storageWire = pendingEspStorageWire;
        boolean blockWire = pendingEspBlockWire;
        boolean spawnerWire = pendingEspSpawnerWire;
        boolean airWire = pendingEspAirWire;
        boolean tpClickFill = pendingEspTpClickFill;
        boolean tpClickWire = pendingEspTpClickWire;
        List<TrajectoriesModule.Path> trajectories = pendingTrajectories;
        pendingTrajectories = List.of();
        pendingEspStorageFill = pendingEspBlockFill = pendingEspBarrierFill = pendingEspSpawnerFill = pendingEspAirFill = false;
        pendingEspStorageWire = pendingEspBlockWire = pendingEspSpawnerWire = pendingEspAirWire = false;
        pendingEspTpClickFill = pendingEspTpClickWire = false;
        StorageSnapshot storageFrame = pendingEspStorageFrame;
        StorageSnapshot blockFrame = pendingEspBlockFrame;
        StorageSnapshot barrierFrame = pendingEspBarrierFrame;
        StorageSnapshot spawnerFrame = pendingEspSpawnerFrame;
        AABB airBox = pendingEspAirBox;
        int airColor = pendingEspAirColor;
        AABB tpClickBox = pendingEspTpClickBox;
        int tpClickColor = pendingEspTpClickColor;
        Vec3 cam = pendingEspCamera;

        AutismBufferSource bufferSource = new AutismBufferSource();
        PoseStack.Pose pose = matrices.last();
        if (storageFill || blockFill || barrierFill || spawnerFill || airFill || tpClickFill) {
            VertexConsumer buffer = bufferSource.getBuffer(AutismRenderTypes.storageEspFillSeeThrough());
            if (storageFill && storageFrame != null) storageFrame.renderFill(pose, buffer, cam, 0.3f);
            if (blockFill && blockFrame != null) blockFrame.renderFill(pose, buffer, cam, 0.3f);
            if (barrierFill && barrierFrame != null) {

                barrierFrame.renderFill(pose, buffer, cam, 0.2f);
            }
            if (spawnerFill && spawnerFrame != null) spawnerFrame.renderFill(pose, buffer, cam, 0.3f);
            if (airFill && airBox != null) {
                fillBox(pose, buffer, airBox.move(-cam.x, -cam.y, -cam.z), withAlpha(airColor, 0.12F));
            }
            if (tpClickFill && tpClickBox != null) {
                fillBox(pose, buffer, tpClickBox.move(-cam.x, -cam.y, -cam.z), withAlpha(tpClickColor, 0.12F));
            }
        }
        if (!trajectories.isEmpty()) {

            try {

                VertexConsumer markerLines = null;
                boolean thin = trajectories.get(0).lineWidth() <= 1.0F;
                VertexConsumer arcs = bufferSource.getBuffer(thin
                    ? AutismRenderTypes.trajectoryThinLines()
                    : AutismRenderTypes.trajectoryLines());
                for (TrajectoriesModule.Path path : trajectories) {
                    if (thin) {
                        drawTrajectoryThinStrip(pose, arcs, path.points(), cam, path.color());
                    } else {
                        drawTrajectoryStrip(pose, arcs, path.points(), cam, path.color(), path.lineWidth());
                    }
                }
                boolean anyMarkers = false;

                for (TrajectoriesModule.Path path : trajectories) {
                    if (path.markers().isEmpty()) continue;
                    if (!anyMarkers) {
                        anyMarkers = true;
                        markerLines = bufferSource.getBuffer(AutismRenderTypes.trajectoryLines());
                    }
                    for (TrajectoriesModule.Marker marker : path.markers()) {
                        renderStorageBox(pose, markerLines, marker.box().move(-cam.x, -cam.y, -cam.z),
                            withAlpha(marker.color(), 0.85F), 1.5F);
                    }
                }
                if (anyMarkers) {
                    VertexConsumer markerFill = bufferSource.getBuffer(AutismRenderTypes.storageEspFillSeeThrough());
                    for (TrajectoriesModule.Path path : trajectories) {
                        for (TrajectoriesModule.Marker marker : path.markers()) {
                            fillBox(pose, markerFill, marker.box().move(-cam.x, -cam.y, -cam.z),
                                withAlpha(marker.color(), 0.15F));
                        }
                    }
                }
            } catch (Throwable error) {

                autismclient.AutismClientAddon.LOG.debug("Trajectory draw failed", error);
            }
        }
        if (storageWire || blockWire || spawnerWire || airWire || tpClickWire) {
            VertexConsumer buffer = bufferSource.getBuffer(AutismRenderTypes.tracerEspLines());
            if (storageWire && storageFrame != null) storageFrame.renderWire(pose, buffer, cam, 1.5f);
            if (blockWire && blockFrame != null) blockFrame.renderWire(pose, buffer, cam, 1.5f);
            if (spawnerWire && spawnerFrame != null) spawnerFrame.renderWire(pose, buffer, cam, 1.5f);
            if (airWire && airBox != null) {
                renderStorageBox(pose, buffer, airBox.move(-cam.x, -cam.y, -cam.z),
                    withAlpha(airColor, 0.80F), 1.5F);
            }
            if (tpClickWire && tpClickBox != null) {
                renderStorageBox(pose, buffer, tpClickBox.move(-cam.x, -cam.y, -cam.z),
                    withAlpha(tpClickColor, 0.80F), 1.5F);
            }
        }
        bufferSource.uploadAndDraw();
    }

    private static void drawTrajectoryThinStrip(PoseStack.Pose entry, VertexConsumer buffer, List<Vec3> points,
                                                Vec3 cam, int color) {
        if (points.size() < 2) return;
        Vec3 previous = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            Vec3 current = points.get(i);
            buffer.addVertex(entry, (float) (previous.x - cam.x), (float) (previous.y - cam.y),
                (float) (previous.z - cam.z)).setColor(color);
            buffer.addVertex(entry, (float) (current.x - cam.x), (float) (current.y - cam.y),
                (float) (current.z - cam.z)).setColor(color);
            previous = current;
        }
    }

    private static void drawTrajectoryStrip(PoseStack.Pose entry, VertexConsumer buffer, List<Vec3> points,
                                            Vec3 cam, int color, float width) {
        if (points.size() < 2) return;
        Vector3f normal = new Vector3f();
        Vec3 previous = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            Vec3 current = points.get(i);
            float dx = (float) (current.x - previous.x);
            float dy = (float) (current.y - previous.y);
            float dz = (float) (current.z - previous.z);
            if (dx * dx + dy * dy + dz * dz > 1.0E-10F) {
                normal.set(dx, dy, dz).normalize();
                buffer.addVertex(entry, (float) (previous.x - cam.x), (float) (previous.y - cam.y),
                        (float) (previous.z - cam.z))
                    .setColor(color).setNormal(entry, normal).setLineWidth(width);
                buffer.addVertex(entry, (float) (current.x - cam.x), (float) (current.y - cam.y),
                        (float) (current.z - cam.z))
                    .setColor(color).setNormal(entry, normal).setLineWidth(width);
            }
            previous = current;
        }
    }

    private static void drawTracerLine(PoseStack.Pose entry, VertexConsumer buffer,
                                       double x1, double y1, double z1,
                                       double x2, double y2, double z2, int color, float width) {
        Vector3f normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1));
        if (normal.lengthSquared() <= 1.0E-8f) return;
        normal.normalize();
        float fx1 = (float) x1, fy1 = (float) y1, fz1 = (float) z1;
        float fx2 = (float) x2, fy2 = (float) y2, fz2 = (float) z2;
        buffer.addVertex(entry, fx1, fy1, fz1).setColor(color).setNormal(entry, normal).setLineWidth(width);

        float t = new Vector3f(fx1, fy1, fz1).negate().dot(normal);
        float length = new Vector3f(fx2, fy2, fz2).sub(fx1, fy1, fz1).length();
        if (t > 0 && t < length) {
            Vector3f closeToCam = new Vector3f(normal).mul(t).add(fx1, fy1, fz1);
            buffer.addVertex(entry, closeToCam.x, closeToCam.y, closeToCam.z).setColor(color).setNormal(entry, normal).setLineWidth(width);
            buffer.addVertex(entry, closeToCam.x, closeToCam.y, closeToCam.z).setColor(color).setNormal(entry, normal).setLineWidth(width);
        }

        buffer.addVertex(entry, fx2, fy2, fz2).setColor(color).setNormal(entry, normal).setLineWidth(width);
    }

    private static Vec3 cameraForward(CameraRenderState camera) {
        if (camera == null) return new Vec3(0.0, 0.0, -1.0);
        Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
        camera.orientation.transform(forward);
        Vec3 result = new Vec3(forward.x, forward.y, forward.z);
        return result.lengthSqr() <= 1.0E-8 ? Vec3.directionFromRotation(camera.xRot, camera.yRot) : result.normalize();
    }

    private static void collectEntityTracers(ClientLevel level, Vec3 from, float tickDelta, float width, boolean heightLine, List<TracerLine> out) {
        if (level == null) return;
        long gameTime = level.getGameTime();
        int revision = ModuleRegistry.revision();
        if (tracerSelectionLevel != level || tracerSelectionGameTime != gameTime
            || tracerSelectionRevision != revision) {
            List<EntityTraceTarget> selected = new ArrayList<>();
            for (Entity entity : level.entitiesForRendering()) {
                if (ModuleRenderUtil.shouldTrace(entity)) {
                    selected.add(new EntityTraceTarget(entity, ModuleRenderUtil.tracerColor(entity)));
                }
            }
            tracerSelectionLevel = level;
            tracerSelectionGameTime = gameTime;
            tracerSelectionRevision = revision;
            tracerSelection = List.copyOf(selected);
        }
        for (EntityTraceTarget target : tracerSelection) {
            Entity entity = target.entity();
            if (entity == null || entity.isRemoved()) continue;
            Vec3 base = stableInterpolatedPosition(entity, tickDelta);
            Vec3 top = base.add(0.0, entity.getBbHeight(), 0.0);
            int color = target.color();
            if (from.distanceToSqr(base) > 1.0E-8) out.add(new TracerLine(from, base, color, width));
            if (heightLine && base.distanceToSqr(top) > 1.0E-8) out.add(new TracerLine(base, top, color, width));
        }
    }

    private record EntityTraceTarget(Entity entity, int color) {
    }

    private static Vec3 stableInterpolatedPosition(Entity entity, float tickDelta) {
        double dx = entity.getX() - entity.xOld;
        double dy = entity.getY() - entity.yOld;
        double dz = entity.getZ() - entity.zOld;
        if (entity.tickCount <= 1 || dx * dx + dy * dy + dz * dz > TELEPORT_SNAP_DISTANCE_SQ) {
            return entity.position();
        }
        return new Vec3(
            Mth.lerp(tickDelta, entity.xOld, entity.getX()),
            Mth.lerp(tickDelta, entity.yOld, entity.getY()),
            Mth.lerp(tickDelta, entity.zOld, entity.getZ())
        );
    }

    private static void renderEntityBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        renderStorageBox(pose, buffer, box, color, 1.5f);
    }

    private static void renderStorageBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width) {
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;
        line(pose, buffer, x1, y1, z1, x2, y1, z1, color, width);
        line(pose, buffer, x2, y1, z1, x2, y1, z2, color, width);
        line(pose, buffer, x2, y1, z2, x1, y1, z2, color, width);
        line(pose, buffer, x1, y1, z2, x1, y1, z1, color, width);
        line(pose, buffer, x1, y2, z1, x2, y2, z1, color, width);
        line(pose, buffer, x2, y2, z1, x2, y2, z2, color, width);
        line(pose, buffer, x2, y2, z2, x1, y2, z2, color, width);
        line(pose, buffer, x1, y2, z2, x1, y2, z1, color, width);
        line(pose, buffer, x1, y1, z1, x1, y2, z1, color, width);
        line(pose, buffer, x2, y1, z1, x2, y2, z1, color, width);
        line(pose, buffer, x2, y1, z2, x2, y2, z2, color, width);
        line(pose, buffer, x1, y1, z2, x1, y2, z2, color, width);
    }

    private static final double TELEPORT_SNAP_DISTANCE_SQ = 16.0 * 16.0;

    private static void clippedLine(PoseStack.Pose pose, VertexConsumer buffer,
                                    Vec3 a, Vec3 b, Vec3 planePoint, Vec3 forward,
                                    int color, float width) {
        double da = signedDistanceFromPlane(a, planePoint, forward);
        double db = signedDistanceFromPlane(b, planePoint, forward);
        if (da < 0.0 && db < 0.0) return;
        double ax = a.x, ay = a.y, az = a.z;
        double bx = b.x, by = b.y, bz = b.z;
        if (da < 0.0) {
            double t = -da / (db - da);
            ax = a.x + (b.x - a.x) * t;
            ay = a.y + (b.y - a.y) * t;
            az = a.z + (b.z - a.z) * t;
        } else if (db < 0.0) {
            double t = -db / (da - db);
            bx = b.x + (a.x - b.x) * t;
            by = b.y + (a.y - b.y) * t;
            bz = b.z + (a.z - b.z) * t;
        }
        AutismWorldGeometry.line(pose, buffer, ax, ay, az, bx, by, bz, color, width);
    }

    private static double signedDistanceFromPlane(Vec3 point, Vec3 planePoint, Vec3 forward) {
        return (point.x - planePoint.x) * forward.x
            + (point.y - planePoint.y) * forward.y
            + (point.z - planePoint.z) * forward.z;
    }

    private static void line(PoseStack.Pose pose, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2, double z2, int color, float width) {
        AutismWorldGeometry.line(pose, buffer, x1, y1, z1, x2, y2, z2, color, width);
    }

    private static void fillBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        if (((color >>> 24) & 0xFF) <= 0) return;
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

    private static int withAlpha(int color, float alphaMultiplier) {
        int alpha = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * alphaMultiplier)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int lighten(int color, float factor) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        r += (int) ((255 - r) * factor);
        g += (int) ((255 - g) * factor);
        b += (int) ((255 - b) * factor);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static float parseFloat(String value, float fallback, float min, float max) {
        try {
            return Mth.clamp(Float.parseFloat(value), min, max);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static StorageSnapshot storageSnapshot(Module module, ClientLevel level, Player player, float tickDelta) {
        if (module == null || level == null || player == null) return StorageSnapshot.empty();
        long gameTime = level.getGameTime();
        int revision = ModuleRegistry.revision();
        int chunkX = player.chunkPosition().x();
        int chunkZ = player.chunkPosition().z();
        StorageSnapshot cached = storageSnapshot;
        if (cached.matches(gameTime, revision, chunkX, chunkZ)) return cached;

        long perf = AutismPerf.beginJoin();
        List<StorageBox> boxes = new ArrayList<>();
        List<StorageTrace> traces = new ArrayList<>();
        ModuleStorageEsp.collectBothDetailed(
            module,
            level,
            player,
            tickDelta,
            (box, color, meshable) -> boxes.add(new StorageBox(box, color, meshable)),
            (target, color) -> traces.add(new StorageTrace(target, color))
        );
        StorageSnapshot next = StorageSnapshot.create(gameTime, revision, chunkX, chunkZ,
            boxes, traces, Boolean.parseBoolean(module.value("meshing")), cached);
        storageSnapshot = next;
        AutismPerf.endJoinSpike("join.storageEsp.scan", perf, 6_000_000L);
        return next;
    }

    private static StorageSnapshot blockSnapshot(Module module, ClientLevel level, Player player) {
        if (module == null || level == null || player == null) return StorageSnapshot.empty();
        long gameTime = level.getGameTime();
        int revision = ModuleRegistry.revision();
        int chunkX = player.chunkPosition().x();
        int chunkZ = player.chunkPosition().z();
        StorageSnapshot cached = blockSnapshot;
        if (cached.matches(gameTime, revision, chunkX, chunkZ)) return cached;

        long perf = AutismPerf.beginJoin();
        List<StorageBox> boxes = new ArrayList<>();
        List<StorageTrace> traces = new ArrayList<>();
        ModuleBlockEsp.collectBoth(
            module,
            level,
            player,
            (box, color) -> boxes.add(new StorageBox(box, color, true)),
            (target, color) -> traces.add(new StorageTrace(target, color))
        );
        StorageSnapshot next = StorageSnapshot.create(gameTime, revision, chunkX, chunkZ,
            boxes, traces, Boolean.parseBoolean(module.value("meshing")), cached);
        blockSnapshot = next;
        AutismPerf.endJoinSpike("join.blockEsp.scan", perf, 6_000_000L);
        return next;
    }

    private static StorageSnapshot spawnerSnapshot(Module module, ClientLevel level, Player player) {
        if (module == null || level == null || player == null) return StorageSnapshot.empty();
        long gameTime = level.getGameTime();
        int revision = ModuleRegistry.revision();
        int chunkX = player.chunkPosition().x();
        int chunkZ = player.chunkPosition().z();
        StorageSnapshot cached = spawnerSnapshot;
        if (cached.matches(gameTime, revision, chunkX, chunkZ)) return cached;

        long perf = AutismPerf.beginJoin();
        List<StorageBox> boxes = new ArrayList<>();
        List<StorageTrace> traces = new ArrayList<>();
        ModuleSpawnerEsp.collectBoth(
            module,
            level,
            player,
            (box, color) -> boxes.add(new StorageBox(box, color, true)),
            (target, color) -> traces.add(new StorageTrace(target, color))
        );
        StorageSnapshot next = StorageSnapshot.create(gameTime, revision, chunkX, chunkZ,
            boxes, traces, Boolean.parseBoolean(module.value("meshing")), cached);
        spawnerSnapshot = next;
        AutismPerf.endJoinSpike("join.spawnerEsp.scan", perf, 6_000_000L);
        return next;
    }

    private static StorageSnapshot barrierSnapshot(Module module, ClientLevel level, Player player) {
        if (module == null || level == null || player == null) return StorageSnapshot.empty();
        long gameTime = level.getGameTime();
        int revision = ModuleRegistry.revision();
        int chunkX = player.chunkPosition().x();
        int chunkZ = player.chunkPosition().z();
        StorageSnapshot cached = barrierSnapshot;
        if (cached.matches(gameTime, revision, chunkX, chunkZ)) return cached;

        long perf = AutismPerf.beginJoin();
        List<StorageBox> boxes = new ArrayList<>();
        List<StorageTrace> traces = new ArrayList<>();
        ModuleBlockEsp.collectBarriers(
            module,
            level,
            player,
            (box, color) -> boxes.add(new StorageBox(box, color, true)),
            (target, color) -> traces.add(new StorageTrace(target, color))
        );
        StorageSnapshot next = StorageSnapshot.create(gameTime, revision, chunkX, chunkZ,
            boxes, traces, Boolean.parseBoolean(module.value("meshing")), cached);
        barrierSnapshot = next;
        AutismPerf.endJoinSpike("join.barrierEsp.scan", perf, 6_000_000L);
        return next;
    }

    private static WaypointSnapshot waypointSnapshot(Module module, Minecraft mc, ClientLevel level) {
        if (module == null || mc == null || level == null) return WaypointSnapshot.empty();
        long gameTime = level.getGameTime();
        AutismWaypoints store = AutismWaypoints.get();
        long revision = store.revision();
        String scope = AutismWaypoints.scopeKey(mc);
        float dotSize = parseFloat(module.value("dot-size"), 3.0f, 1.0f, 10.0f);
        WaypointSnapshot cached = waypointSnapshot;
        if (cached.matches(gameTime, revision, scope, dotSize)) return cached;

        List<WaypointDot> previous = cached.revision() == revision && cached.scope().equals(scope)
            ? cached.dots() : null;
        List<WaypointDot> dots = new ArrayList<>();
        double half = 0.15D * dotSize * 0.5D;
        double discRadius = WAYPOINT_DISC_RADIUS * dotSize;
        int index = 0;
        for (AutismWaypoints.Waypoint waypoint : store.list(scope)) {
            double cx = waypoint.x() + 0.5D;
            double cy = waypoint.y() + 0.5D;
            double cz = waypoint.z() + 0.5D;
            FormattedCharSequence name;
            float nameWidth;
            WaypointDot prior = previous != null && index < previous.size() ? previous.get(index) : null;
            if (prior != null) {
                name = prior.name();
                nameWidth = prior.nameWidth();
            } else {
                name = Component.literal(waypoint.name()).getVisualOrderText();
                nameWidth = mc.font.width(name);
            }
            dots.add(new WaypointDot(
                new AABB(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half),
                waypoint.color(), name, nameWidth));
            index++;
        }
        WaypointSnapshot next = new WaypointSnapshot(gameTime, revision, scope, dotSize, discRadius,
            List.copyOf(dots));
        waypointSnapshot = next;
        return next;
    }

    private static final double WAYPOINT_FULL_SCALE_DIST = 14.0;
    private static final double WAYPOINT_FADE_START_DIST = 6.0;
    private static final double WAYPOINT_HIDE_DIST = 2.5;
    private static final double WAYPOINT_FAR_SCALE_DIVISOR = 12.0;
    private static final double WAYPOINT_MAX_FAR_SCALE = 12.0;
    private static final float WAYPOINT_MIN_SCALE = 0.6f;

    private static final double WAYPOINT_DISC_RADIUS = 0.046;
    private static final double WAYPOINT_DISC_MARGIN = 0.06;
    private static final double WAYPOINT_TEXT_HALF_HEIGHT = 0.115;
    private static final float NAMEPLATE_TEXT_SCALE = 0.025f * 1.25f;
    private static final float NAMEPLATE_LINE_CENTER_PX = -4.5f;
    private static final int NAMEPLATE_OUTLINE_COLOR = 0xFF1A1A1A;
    private static final float WAYPOINT_DISTANCE_LIGHTEN = 0.375f;
    private static final float[][] NAMEPLATE_OUTLINE_OFFSETS = {
        {-1.0f, -1.0f}, {0.0f, -1.0f}, {1.0f, -1.0f},
        {-1.0f, 0.0f}, {1.0f, 0.0f},
        {-1.0f, 1.0f}, {0.0f, 1.0f}, {1.0f, 1.0f}
    };

    private static float waypointScale(double dist) {
        if (dist >= WAYPOINT_FULL_SCALE_DIST) {

            return (float) Mth.clamp(dist / WAYPOINT_FAR_SCALE_DIVISOR, 1.0, WAYPOINT_MAX_FAR_SCALE);
        }
        if (dist >= WAYPOINT_FADE_START_DIST) return 1.0f;
        return WAYPOINT_MIN_SCALE + (1.0f - WAYPOINT_MIN_SCALE)
            * (float) ((dist - WAYPOINT_HIDE_DIST) / (WAYPOINT_FADE_START_DIST - WAYPOINT_HIDE_DIST));
    }

    private static float waypointAlpha(double dist) {
        if (dist >= WAYPOINT_FADE_START_DIST) return 1.0f;
        return (float) Mth.clamp((dist - WAYPOINT_HIDE_DIST) / (WAYPOINT_FADE_START_DIST - WAYPOINT_HIDE_DIST), 0.0, 1.0);
    }

    public static boolean hasPendingWaypointArt() {
        return !pendingWaypointFrame.dots().isEmpty();
    }

    public static void flushWaypointArt(PoseStack matrices) {
        WaypointSnapshot snapshot = pendingWaypointFrame;
        if (snapshot.dots().isEmpty()) return;
        pendingWaypointFrame = WaypointSnapshot.empty();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        Vec3 camera = pendingWaypointCamera;
        Quaternionf orientation = pendingWaypointOrientation;
        Vector3f right = orientation.transform(DISC_AXIS_SCRATCH.set(1.0f, 0.0f, 0.0f));
        float rx = right.x, ry = right.y, rz = right.z;
        Vector3f up = orientation.transform(DISC_AXIS_SCRATCH.set(0.0f, 1.0f, 0.0f));
        float ux = up.x, uy = up.y, uz = up.z;
        PoseStack.Pose pose = matrices.last();
        AutismBufferSource bufferSource = new AutismBufferSource();
        Matrix4f textPose = new Matrix4f();
        Font.GlyphVisitor waypointGlyphs = new Font.GlyphVisitor() {
            @Override
            public void acceptRenderable(TextRenderable renderable) {
                renderable.render(textPose,
                    bufferSource.getBuffer(renderable.renderType(Font.DisplayMode.SEE_THROUGH)),
                    LightCoordsUtil.FULL_BRIGHT, false);
            }
        };
        Identifier discTexture = waypointDiscTexture(mc);
        double baseRadius = snapshot.discRadius();

        if (discTexture != null) {
            VertexConsumer discBuffer = bufferSource.getBuffer(AutismRenderTypes.waypointDiscSeeThrough(discTexture));
            for (WaypointDot dot : snapshot.dots()) {
                double cx = (dot.box().minX + dot.box().maxX) * 0.5D;
                double centerY = (dot.box().minY + dot.box().maxY) * 0.5D;
                double cz = (dot.box().minZ + dot.box().maxZ) * 0.5D;
                double dx = cx - camera.x;
                double dy = centerY - camera.y;
                double dz = cz - camera.z;
                double distSq = dx * dx + dy * dy + dz * dz;

                if (distSq < WAYPOINT_HIDE_DIST * WAYPOINT_HIDE_DIST) continue;
                double dist = Math.sqrt(distSq);
                float scale = waypointScale(dist);
                float alpha = waypointAlpha(dist);
                if (alpha <= 0.0f) continue;
                float radius = (float) (baseRadius * scale);
                int color = withAlpha(dot.color(), alpha);
                discVertex(discBuffer, pose, dx + (-rx - ux) * radius, dy + (-ry - uy) * radius, dz + (-rz - uz) * radius, 0.0f, 1.0f, color);
                discVertex(discBuffer, pose, dx + (-rx + ux) * radius, dy + (-ry + uy) * radius, dz + (-rz + uz) * radius, 0.0f, 0.0f, color);
                discVertex(discBuffer, pose, dx + (rx + ux) * radius, dy + (ry + uy) * radius, dz + (rz + uz) * radius, 1.0f, 0.0f, color);
                discVertex(discBuffer, pose, dx + (rx - ux) * radius, dy + (ry - uy) * radius, dz + (rz - uz) * radius, 1.0f, 1.0f, color);
            }
        }

        Module waypoints = waypointsModule();
        boolean showDistance = waypoints == null || Boolean.parseBoolean(waypoints.value("show-distance"));
        for (WaypointDot dot : snapshot.dots()) {
            double cx = (dot.box().minX + dot.box().maxX) * 0.5D;
            double centerY = (dot.box().minY + dot.box().maxY) * 0.5D;
            double cz = (dot.box().minZ + dot.box().maxZ) * 0.5D;
            double dx = cx - camera.x;
            double dy = centerY - camera.y;
            double dz = cz - camera.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq < WAYPOINT_HIDE_DIST * WAYPOINT_HIDE_DIST) continue;
            double dist = Math.sqrt(distSq);
            float scale = waypointScale(dist);
            float alpha = waypointAlpha(dist);
            if (alpha <= 0.0f) continue;
            double cy = centerY + scale * (baseRadius + WAYPOINT_DISC_MARGIN + WAYPOINT_TEXT_HALF_HEIGHT);
            int color = withAlpha(dot.color(), alpha);
            int outline = withAlpha(NAMEPLATE_OUTLINE_COLOR, alpha);
            float poseScale = NAMEPLATE_TEXT_SCALE * scale;

            String distanceText = showDistance ? " | " + (int) dist + "m" : "";
            float distanceWidth = distanceText.isEmpty() ? 0.0f : mc.font.width(distanceText);
            float nameX = -(dot.nameWidth() + distanceWidth) * 0.5f;
            textPose.set(pose.pose())
                .translate((float) dx, (float) (cy - camera.y), (float) dz)
                .rotate(orientation)
                .scale(poseScale, -poseScale, poseScale);
            for (float[] offset : NAMEPLATE_OUTLINE_OFFSETS) {
                mc.font.prepareText(dot.name(), nameX + offset[0], NAMEPLATE_LINE_CENTER_PX + offset[1],
                    outline, false, false, 0).visit(waypointGlyphs);
            }
            mc.font.prepareText(dot.name(), nameX, NAMEPLATE_LINE_CENTER_PX, color, false, false, 0)
                .visit(waypointGlyphs);
            if (showDistance) {
                int distanceColor = withAlpha(lighten(dot.color(), WAYPOINT_DISTANCE_LIGHTEN), alpha);
                float distanceX = nameX + dot.nameWidth();
                for (float[] offset : NAMEPLATE_OUTLINE_OFFSETS) {
                    mc.font.prepareText(distanceText, distanceX + offset[0], NAMEPLATE_LINE_CENTER_PX + offset[1],
                        outline, false, 0).visit(waypointGlyphs);
                }
                mc.font.prepareText(distanceText, distanceX, NAMEPLATE_LINE_CENTER_PX, distanceColor, false, 0)
                    .visit(waypointGlyphs);
            }
        }
        bufferSource.uploadAndDraw();
    }

    private static final Identifier WAYPOINT_DISC_TEXTURE =
        Identifier.fromNamespaceAndPath("autismclient", "dynamic/waypoint_disc");
    private static final int DISC_TEXTURE_SIZE = 32;
    private static final double DISC_INNER_RADIUS = 12.0;
    private static final double DISC_OUTER_RADIUS = 14.0;
    private static final Vector3f DISC_AXIS_SCRATCH = new Vector3f();
    private static boolean waypointDiscRegistered;
    private static boolean waypointDiscFailed;

    private static void discVertex(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z,
                                   float u, float v, int color) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z).setUv(u, v).setColor(color);
    }

    private static Identifier waypointDiscTexture(Minecraft mc) {
        if (waypointDiscRegistered) return waypointDiscFailed ? null : WAYPOINT_DISC_TEXTURE;
        waypointDiscRegistered = true;
        try {
            mc.getTextureManager().register(WAYPOINT_DISC_TEXTURE, new WaypointDiscTexture(buildWaypointDiscImage()));
        } catch (Throwable t) {
            waypointDiscFailed = true;
            AutismClientAddon.LOG.warn("Failed to create the waypoint disc texture", t);
        }
        return waypointDiscFailed ? null : WAYPOINT_DISC_TEXTURE;
    }

    private static NativeImage buildWaypointDiscImage() {
        NativeImage image = new NativeImage(DISC_TEXTURE_SIZE, DISC_TEXTURE_SIZE, false);
        double center = (DISC_TEXTURE_SIZE - 1) / 2.0;
        for (int y = 0; y < DISC_TEXTURE_SIZE; y++) {
            for (int x = 0; x < DISC_TEXTURE_SIZE; x++) {
                double dx = x - center;
                double dy = y - center;
                double dist = Math.sqrt(dx * dx + dy * dy);
                image.setPixel(x, y, dist <= DISC_INNER_RADIUS ? 0xFFFFFFFF
                    : dist <= DISC_OUTER_RADIUS ? 0xFF101010 : 0x00000000);
            }
        }
        return image;
    }

    private static final class WaypointDiscTexture extends AbstractTexture {
        private WaypointDiscTexture(NativeImage pixels) {
            this.texture = RenderSystem.getDevice().createTexture("waypoint_disc", 5, GpuFormat.RGBA8_UNORM,
                pixels.getWidth(), pixels.getHeight(), 1, 1);
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
            this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, pixels);
            pixels.close();
        }
    }

    private record WaypointSnapshot(long gameTime, long revision, String scope, float dotSize,
                                    double discRadius, List<WaypointDot> dots) {
        private static final WaypointSnapshot EMPTY = new WaypointSnapshot(Long.MIN_VALUE, -1L, "", -1.0f, 0.0D, List.of());

        private static WaypointSnapshot empty() {
            return EMPTY;
        }

        private boolean matches(long gameTime, long revision, String scope, float dotSize) {
            return this.gameTime == gameTime && this.revision == revision
                && this.dotSize == dotSize && this.scope.equals(scope);
        }
    }

    private record WaypointDot(AABB box, int color, FormattedCharSequence name, float nameWidth) {
    }

    private record StorageSnapshot(long gameTime, int revision, int chunkX, int chunkZ,
                                   List<StorageBox> boxes, List<StorageTrace> traces,
                                   boolean meshing, List<StorageBox> unmeshedBoxes,
                                   List<ModuleEspMesh.Box> meshSource, ModuleEspMesh.Geometry mesh) {
        private static final StorageSnapshot EMPTY = new StorageSnapshot(Long.MIN_VALUE, -1,
            Integer.MIN_VALUE, Integer.MIN_VALUE, List.of(), List.of(), false,
            List.of(), List.of(), ModuleEspMesh.build(List.of()));

        private static StorageSnapshot empty() {
            return EMPTY;
        }

        private static StorageSnapshot create(long gameTime, int revision, int chunkX, int chunkZ,
                                              List<StorageBox> mutableBoxes, List<StorageTrace> mutableTraces,
                                              boolean meshing, StorageSnapshot previous) {
            List<StorageBox> boxes = List.copyOf(mutableBoxes);
            List<StorageTrace> traces = List.copyOf(mutableTraces);
            if (!meshing) {
                return new StorageSnapshot(gameTime, revision, chunkX, chunkZ, boxes, traces,
                    false, List.of(), List.of(), ModuleEspMesh.build(List.of()));
            }
            List<StorageBox> unmeshed = new ArrayList<>();
            List<ModuleEspMesh.Box> source = new ArrayList<>();
            for (StorageBox target : boxes) {
                if (target.meshable()) source.add(new ModuleEspMesh.Box(target.box(), target.color()));
                else unmeshed.add(target);
            }

            source.sort(Comparator
                .comparingInt(ModuleEspMesh.Box::color)
                .thenComparingDouble(b -> b.box().minX)
                .thenComparingDouble(b -> b.box().minY)
                .thenComparingDouble(b -> b.box().minZ));
            List<ModuleEspMesh.Box> frozenSource = List.copyOf(source);
            ModuleEspMesh.Geometry geometry = previous != null && previous.meshing()
                && previous.meshSource().equals(frozenSource)
                ? previous.mesh()
                : ModuleEspMesh.build(frozenSource);
            return new StorageSnapshot(gameTime, revision, chunkX, chunkZ, boxes, traces,
                true, List.copyOf(unmeshed), frozenSource, geometry);
        }

        private boolean matches(long gameTime, int revision, int chunkX, int chunkZ) {

            return this.gameTime == gameTime && this.revision == revision && this.chunkX == chunkX && this.chunkZ == chunkZ;
        }

        private void forEachTrace(java.util.function.BiConsumer<Vec3, Integer> consumer) {
            for (StorageTrace trace : traces) consumer.accept(trace.target(), trace.color());
        }

        private void renderFill(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, float opacity) {
            if (!meshing) {
                for (StorageBox target : boxes) {
                    fillBox(pose, buffer, target.box().move(-camera.x, -camera.y, -camera.z),
                        withAlpha(target.color(), opacity));
                }
                return;
            }
            for (ModuleEspMesh.Quad face : mesh.quads()) {
                quad(pose, buffer,
                    face.x1() - camera.x, face.y1() - camera.y, face.z1() - camera.z,
                    face.x2() - camera.x, face.y2() - camera.y, face.z2() - camera.z,
                    face.x3() - camera.x, face.y3() - camera.y, face.z3() - camera.z,
                    face.x4() - camera.x, face.y4() - camera.y, face.z4() - camera.z,
                    withAlpha(face.color(), opacity));
            }
            for (StorageBox target : unmeshedBoxes) {
                fillBox(pose, buffer, target.box().move(-camera.x, -camera.y, -camera.z),
                    withAlpha(target.color(), opacity));
            }
        }

        private void renderWire(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, float width) {
            if (!meshing) {
                for (StorageBox target : boxes) {
                    renderStorageBox(pose, buffer, target.box().move(-camera.x, -camera.y, -camera.z),
                        target.color(), width);
                }
                return;
            }
            for (ModuleEspMesh.Edge edge : mesh.edges()) {
                line(pose, buffer,
                    edge.x1() - camera.x, edge.y1() - camera.y, edge.z1() - camera.z,
                    edge.x2() - camera.x, edge.y2() - camera.y, edge.z2() - camera.z,
                    edge.color(), width);
            }
            for (StorageBox target : unmeshedBoxes) {
                renderStorageBox(pose, buffer, target.box().move(-camera.x, -camera.y, -camera.z),
                    target.color(), width);
            }
        }
    }

    private record StorageBox(AABB box, int color, boolean meshable) {
    }

    private record StorageTrace(Vec3 target, int color) {
    }

    private record TracerLine(Vec3 from, Vec3 to, int color, float width) {
    }

}
