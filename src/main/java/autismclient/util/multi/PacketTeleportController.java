package autismclient.util.multi;

import autismclient.util.AutismConfig;
import autismclient.modules.TpClickModule;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.macro.PacketClipSafety;
import autismclient.util.macro.PacketRoutePlanner;
import autismclient.util.macro.PacedTpAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class PacketTeleportController {
    public static final int DEFAULT_MAX_PACKETS = 20;
    public static final int DEFAULT_PAUSE_MS = 500;
    public static final double DEFAULT_STEP = 10.0D;

    private static final double VEHICLE_STEP = 4.0D;
    private static final double MIN_STEP = 0.0625D;
    private static final double CORRECTION_MATCH_EPSILON = 0.05D;
    private static final long RECOVERY_PROBE_DELAY_MS = 100L;
    private static final long RECOVERY_STABLE_MS = 600L;
    private static final long ARRIVAL_SETTLE_MS = 250L;
    private static final double ARRIVAL_EPSILON = 0.5D;

    private static final long JOB_STALL_TIMEOUT_MS = 3_500L;
    private static final long RESCUE_EXHAUSTED_MS = 12_000L;
    private static final int MAX_RESCUE_LEVEL = 3;
    private static final long JOB_TOTAL_TIMEOUT_MS = 300_000L;
    private static final int HYBRID_FRONTIER_BLOCKS = 80;
    private static final int BLIND_POV_FRONTIER_BLOCKS = 32;
    private static final ThreadLocal<Boolean> OWNED_SEND = ThreadLocal.withInitial(() -> false);
    private static final AtomicInteger OWNED_SEND_SCOPES = new AtomicInteger();
    private static final Object LOCK = new Object();
    private static final AtomicLong MACRO_IDS = new AtomicLong();
    private static volatile Job active;

    private PacketTeleportController() {
    }

    public enum CommandKind { START, STOP, STATUS, CONFIG, RESET, HELP, ERROR }

    public record CommandRequest(CommandKind kind, Vec3 destination, int maxPackets, int pauseMs,
                                 String error) {
        static CommandRequest error(String message) {
            return new CommandRequest(CommandKind.ERROR, null, 0, 0, message);
        }
    }

    public record MacroResult(boolean success, String detail) {}
    public record MacroHandle(long id, CompletableFuture<MacroResult> completion) {}

    public static CommandRequest parse(String arguments, Vec3 origin, int configuredPackets, int configuredPause) {
        String trimmed = arguments == null ? "" : arguments.trim();
        if (trimmed.isEmpty()) return new CommandRequest(CommandKind.HELP, null, 0, 0, "");
        String[] parts = trimmed.split("\\s+");
        String first = parts[0].toLowerCase(Locale.ROOT);
        if ("stop".equals(first)) {
            return parts.length == 1 ? new CommandRequest(CommandKind.STOP, null, 0, 0, "")
                : CommandRequest.error("Usage: tp stop");
        }
        if ("status".equals(first)) {
            return parts.length == 1 ? new CommandRequest(CommandKind.STATUS, null, 0, 0, "")
                : CommandRequest.error("Usage: tp status");
        }
        if ("reset".equals(first)) {
            return parts.length == 1
                ? new CommandRequest(CommandKind.RESET, null, DEFAULT_MAX_PACKETS, DEFAULT_PAUSE_MS, "")
                : CommandRequest.error("Usage: tp reset");
        }
        if ("config".equals(first)) {
            if (parts.length != 3) return CommandRequest.error("Usage: tp config <maxPackets> <pauseMs>");
            Integer packets = boundedInteger(parts[1], 1, 100);
            Integer pause = boundedInteger(parts[2], 50, 10_000);
            if (packets == null) return CommandRequest.error("maxPackets must be 1-100");
            if (pause == null) return CommandRequest.error("pauseMs must be 50-10000");
            return new CommandRequest(CommandKind.CONFIG, null, packets, pause, "");
        }
        int offset = 0;
        if ("fast".equals(first)) offset = 1;
        if (parts.length - offset < 3 || parts.length - offset > 6 || origin == null) {
            return CommandRequest.error("Usage: tp <x> <y> <z> [maxPackets] [pauseMs]");
        }
        Double x = coordinate(parts[offset], origin.x);
        Double y = coordinate(parts[offset + 1], origin.y);
        Double z = coordinate(parts[offset + 2], origin.z);
        if (x == null || y == null || z == null) return CommandRequest.error("Coordinates must be numbers or ~ offsets");
        int packets = clamp(configuredPackets, 1, 100);
        int pause = clamp(configuredPause, 50, 10_000);
        int option = offset + 3;
        if (option < parts.length && "fast".equalsIgnoreCase(parts[option])) option++;
        if (option < parts.length) {
            Integer parsed = boundedInteger(parts[option], 1, 100);
            if (parsed == null) return CommandRequest.error("maxPackets must be 1-100");
            packets = parsed;
            option++;
        }
        if (option < parts.length) {
            Integer parsed = boundedInteger(parts[option], 50, 10_000);
            if (parsed == null) return CommandRequest.error("pauseMs must be 50-10000");
            pause = parsed;
            option++;
        }
        if (option != parts.length) {
            return CommandRequest.error("Usage: tp <x> <y> <z> [maxPackets] [pauseMs]");
        }
        return new CommandRequest(CommandKind.START, new Vec3(x, y, z), packets, pause, "");
    }

    public static String executeMain(String arguments) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc == null ? null : mc.player;
        AutismConfig config = AutismConfig.getGlobal();
        CommandRequest request = parse(arguments, player == null ? null : controlledMainPosition(player),
            config.tpMaxPackets, config.tpPauseMs);
        String common = applyCommon(request, null);
        if (common != null) return common;
        if (player == null || mc.getConnection() == null || player.level() == null) return "Join a world first";
        if (MultiPilot.isActive()) return "POV owns player commands";
        if (MacroExecutor.isRunning()) return "Stop the running macro first";
        Entity vehicle = player.getVehicle();
        synchronized (LOCK) {
            active = Job.main(player, mc.getConnection(), player.level(), vehicle, request);
        }
        neutralizeMain(player);
        return startedMessage(request.destination(), request.maxPackets(), request.pauseMs(), vehicle != null);
    }

    public static MacroHandle startMacro(PacedTpAction action) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc == null ? null : mc.player;
        if (action == null || player == null || mc.getConnection() == null || player.level() == null) {
            return completedMacro(false, "No world or connection");
        }
        if (MultiPilot.isActive()) return completedMacro(false, "POV owns player movement");
        Vec3 origin = controlledMainPosition(player);
        CommandRequest request = parse(action.commandArguments(), origin,
            action.maxPackets, action.pauseMs);
        if (request.kind() != CommandKind.START) {
            return completedMacro(false, request.error().isBlank() ? "Invalid TP action" : request.error());
        }
        cancelAll("replaced by macro TP");
        long id = MACRO_IDS.incrementAndGet();
        CompletableFuture<MacroResult> completion = new CompletableFuture<>();
        Entity vehicle = player.getVehicle();
        Job job = Job.mainMacro(player, mc.getConnection(), player.level(), vehicle, request, id, completion);
        synchronized (LOCK) {
            active = job;
        }
        neutralizeMain(player);
        return new MacroHandle(id, completion);
    }

    public static void cancelMacro(MacroHandle handle, String reason) {
        if (handle == null) return;
        Job job;
        synchronized (LOCK) {
            job = active != null && active.macroId == handle.id() ? active : null;
        }
        if (job != null) finish(job, false, reason == null ? "macro stopped" : reason);
    }

    private static MacroHandle completedMacro(boolean success, String detail) {
        CompletableFuture<MacroResult> future = CompletableFuture.completedFuture(new MacroResult(success, detail));
        return new MacroHandle(0L, future);
    }

    static String executePov(String arguments) {
        MultiSession session = MultiPilot.activeCommandSession();
        RemotePlayer bot = MultiPilot.activeBotEntity();
        AutismConfig config = AutismConfig.getGlobal();
        Vec3 origin = session == null ? null : session.takeoverPosition().position();
        CommandRequest request = parse(arguments, origin, config.tpMaxPackets, config.tpPauseMs);
        String common = applyCommon(request, session);
        if (common != null) return common;
        if (session == null || bot == null || !session.pilotPacketsReady()) return "POV session is not ready";
        if (session.macroOwnsPilot()) return "Macro owns the POV bot";
        if (bot.isPassenger()) return "Vehicle POV is unsupported";
        synchronized (LOCK) {
            active = Job.pov(session, bot, request);
        }
        MultiPilot.preparePacedTeleport(session, bot);
        return startedMessage(request.destination(), request.maxPackets(), request.pauseMs(), false);
    }

    private static String applyCommon(CommandRequest request, MultiSession owner) {
        if (request.kind() == CommandKind.ERROR) return request.error();
        if (request.kind() == CommandKind.HELP) {
            return "Usage: tp <x> <y> <z> [maxPackets] [pauseMs]";
        }
        if (request.kind() == CommandKind.CONFIG || request.kind() == CommandKind.RESET) {
            AutismConfig config = AutismConfig.getGlobal();
            config.tpMaxPackets = request.maxPackets();
            config.tpPauseMs = request.pauseMs();
            config.save();
            return "TP defaults: " + request.maxPackets() + " packets, " + request.pauseMs() + " ms";
        }
        if (request.kind() == CommandKind.STATUS) return status(owner);
        if (request.kind() == CommandKind.STOP) return stop(owner, "stopped by command");
        return null;
    }

    public static void tick(Minecraft mc) {
        Job job = active;

        boolean settling = job != null && job.arrivalCandidateAt != 0L;
        boolean blockInput = job != null && job.owner == Owner.MAIN && mc != null
            && !settling && TpClickModule.blocksMovement();
        if (blockInput) {

            releaseMovementInput(mc);
            inputWasBlocked = true;
        } else if (inputWasBlocked) {

            inputWasBlocked = false;
            restoreMovementInput(mc);
        }
        if (job == null || mc == null) return;
        job.tickCounter++;
        long now = System.currentTimeMillis();
        String invalid = validateOwner(job, mc);
        if (invalid != null) {
            finish(job, false, invalid);
            return;
        }

        if (now - job.startedAt > JOB_TOTAL_TIMEOUT_MS) {
            finish(job, false, "timeout");
            return;
        }
        if (now - job.lastActivityAt > JOB_STALL_TIMEOUT_MS) {
            if (job.rescueLevel < MAX_RESCUE_LEVEL) {
                job.rescueLevel++;
                job.lastActivityAt = now;
                job.frames.clear();
                job.waypoints.clear();
                job.activeWaypoint = null;
                job.lastPlanTick = Long.MIN_VALUE;
            } else if (now - job.lastActivityAt > JOB_STALL_TIMEOUT_MS + RESCUE_EXHAUSTED_MS) {
                finish(job, false, "timeout");
                return;
            }
        }
        if (!settling && physicalMovementDown(mc)) {
            applyRendered(job, job.current, protocolGroundAt(job, job.current));
            finish(job, false, "cancelled by movement input");
            return;
        }
        Correction correction;
        synchronized (LOCK) {
            correction = job.pendingCorrection;
            job.pendingCorrection = null;
        }
        if (correction != null) {
            double predictedDistance = job.current.distanceTo(job.requestedTarget);
            job.current = correction.position();
            if (job.current.distanceTo(job.requestedTarget) <= 2.5D) {

                job.frames.clear();
                job.waypoints.clear();
                job.activeWaypoint = null;
                job.arrivalCandidateAt = now;
                job.lastProgressAt = now;
                applyRendered(job, job.current, protocolGroundAt(job, job.current));
                return;
            }
            job.frames.clear();
            job.waypoints.clear();
            job.activeWaypoint = null;
            job.arrivalCandidateAt = 0L;
            double distance = job.current.distanceTo(job.requestedTarget);
            double predictionError = correction.position().distanceTo(job.lastSentPosition);
            if (correctionAccepted(job.bestDistance, predictedDistance, distance, predictionError)) {
                job.bestDistance = Math.min(job.bestDistance, distance);
                job.rejectionCount = 0;
                job.lastProgressAt = now;

                job.lastFailedTarget = null;
                job.sameTargetFailures = 0;
                job.roofY = Double.NaN;
                job.effectiveStep = Math.min(DEFAULT_STEP, job.effectiveStep * 1.25D);
                job.pacer.rebase(now, job.incrementalRecovery ? RECOVERY_PROBE_DELAY_MS : 0L);
            } else {
                job.rejectionCount++;

                if (job.lastPlannedTarget != null) {
                    if (job.lastFailedTarget != null && job.lastFailedTarget.distanceTo(job.lastPlannedTarget) < 1.5D) {
                        job.sameTargetFailures++;
                    } else {
                        job.sameTargetFailures = 1;
                    }
                    job.lastFailedTarget = job.lastPlannedTarget;
                }

                double reduced = job.rejectionCount >= 3 ? job.effectiveStep * 0.5D
                    : job.rejectionCount == 2 ? Math.max(0.5D, job.effectiveStep * 0.25D)
                    : Math.max(2.0D, job.effectiveStep * 0.5D);
                job.effectiveStep = Math.max(MIN_STEP, reduced);
                job.incrementalRecovery = true;
                job.recoveryAnchorDistance = distance;
                job.recoveryNextSendAt = now + RECOVERY_PROBE_DELAY_MS;
                job.recoveryStableAt = now + RECOVERY_STABLE_MS;
                job.pacer.rebase(now, RECOVERY_PROBE_DELAY_MS);
            }
            if (stableAnchorAt(job, job.current)) job.spatialLegsSinceStable = 0;
            applyRendered(job, job.current, protocolGroundAt(job, job.current));
        }

        if (!armLavaEscape(job, now)) return;
        Vec3 destination = job.lavaEmergency ? job.requestedTarget : resolveDestination(job);
        if (destination == null) return;
        if (job.current.distanceTo(destination) <= ARRIVAL_EPSILON) {

            PacketRoutePlanner.CollisionView view = collisionView(job);
            if (view != null && view.loaded(job.current) && !view.clear(job.current)) {
                Vec3 clearSpot = PacketRoutePlanner.nearestClear(view, job.current, 3.0D);
                if (clearSpot != null && view.lavaSafe(clearSpot)) {
                    job.activeWaypoint = clearSpot;
                    job.arrivalCandidateAt = 0L;
                    return;
                }
            }
            if (job.arrivalCandidateAt == 0L) job.arrivalCandidateAt = now;
            if (now - job.arrivalCandidateAt >= ARRIVAL_SETTLE_MS) {
                finish(job, true, landingMessage(job));
            }
            return;
        }
        job.arrivalCandidateAt = 0L;

        if (job.incrementalRecovery) {
            double recoveryDistance = job.current.distanceTo(destination);
            if (now >= job.recoveryStableAt
                && job.recoveryAnchorDistance - recoveryDistance >= 0.5D) {
                job.incrementalRecovery = false;
                job.recoveryNextSendAt = 0L;
                job.effectiveStep = Math.min(DEFAULT_STEP, Math.max(1.0D, job.effectiveStep * 2.0D));
                job.pacer.rebase(now, 0L);
            } else if (now < job.recoveryNextSendAt) {
                return;
            }
        }

        if (job.pacer.waiting(now)) return;
        int budget = job.incrementalRecovery ? 1 : job.pacer.remainingInWindow();
        while (budget-- > 0) {
            if (job.frames.isEmpty() && !prepareNextFrames(job, destination, now, budget + 1)) return;
            MoveFrame frame = job.frames.pollFirst();
            if (frame == null) return;
            if (!frameLavaSafe(job, frame)) {
                job.frames.clear();
                job.waypoints.clear();
                job.activeWaypoint = null;
                job.resolvedTarget = null;
                job.lavaEmergency = false;
                job.pacer.rebase(now, RECOVERY_PROBE_DELAY_MS);
                return;
            }
            boolean emergencyFrame = job.lavaEmergency && frame.moves();
            if (!send(job, frame)) {
                finish(job, false, "movement connection became unavailable");
                return;
            }
            int sentPackets = packetCount(job, frame);
            job.totalPackets += sentPackets;
            for (int i = 0; i < sentPackets; i++) job.pacer.markSent(now);
            budget -= sentPackets - 1;
            if (frame.moves()) {
                job.current = frame.position();
                job.lastSentPosition = job.current;
                job.lastActivityAt = now;
                if (job.current.distanceTo(destination) < job.bestDistance - 0.01D) job.lastProgressAt = now;
                job.spatialLegsSinceStable = nextGroundingCounter(
                    job.spatialLegsSinceStable, stableAnchorAt(job, job.current));
                applyRendered(job, job.current, frame.onGround());
                if (job.incrementalRecovery) job.recoveryNextSendAt = now + RECOVERY_PROBE_DELAY_MS;
            }
            if (emergencyFrame) {
                job.lavaEmergency = false;
                job.incrementalRecovery = false;
                job.pacer.rebase(now, 0L);
                return;
            }
            if (job.current.distanceTo(destination) <= ARRIVAL_EPSILON && job.frames.isEmpty()
                && job.waypoints.isEmpty() && job.activeWaypoint == null) {
                job.arrivalCandidateAt = now;
                return;
            }
            if (job.pacer.waiting(now)) return;
        }
    }

    private static Vec3 resolveDestination(Job job) {
        PacketRoutePlanner.CollisionView view = collisionView(job);
        if (job.resolvedTarget != null) {
            if (view == null || !view.loaded(job.resolvedTarget)) return null;
            if (view.clear(job.resolvedTarget) && view.lavaSafe(job.resolvedTarget)
                && (!job.adjustedLanding || view.traversable(job.resolvedTarget))) return job.resolvedTarget;
            job.resolvedTarget = null;
            job.adjustedLanding = false;
        }
        if (view != null && view.loaded(job.requestedTarget)) {
            boolean targetLavaSafe = view.lavaSafe(job.requestedTarget);
            if (!targetLavaSafe && System.currentTimeMillis() < job.nextDestinationSearchAt) return null;
            Vec3 clear = resolveLoadedDestination(
                view, job.requestedTarget, job.lavaDestinationSearchRadius);
            if (clear == null) {
                if (targetLavaSafe) {
                    job.resolvedTarget = job.requestedTarget;
                    return job.resolvedTarget;
                }
                job.lavaDestinationSearchRadius = Math.min(32, job.lavaDestinationSearchRadius + 8);
                job.nextDestinationSearchAt = System.currentTimeMillis() + 1_000L;
                return null;
            }
            job.resolvedTarget = clear;
            job.adjustedLanding = clear.distanceTo(job.requestedTarget) > ARRIVAL_EPSILON;
            return clear;
        }

        if (job.owner == Owner.POV) return job.requestedTarget;
        return job.requestedTarget;
    }

    static Vec3 resolveLoadedDestination(PacketRoutePlanner.CollisionView view,
                                         Vec3 requested, double lavaSearchRadius) {
        if (view == null || requested == null || !view.loaded(requested)) return null;
        if (!view.lavaSafe(requested)) {
            return PacketRoutePlanner.safestLavaLanding(view, requested, lavaSearchRadius);
        }
        Vec3 clear = PacketRoutePlanner.nearestClear(view, requested, 3.0D);
        return clear == null ? requested : clear;
    }

    private static boolean prepareNextFrames(Job job, Vec3 destination, long now, int packetBudget) {
        if (job.activeWaypoint == null && !job.waypoints.isEmpty()) job.activeWaypoint = job.waypoints.pollFirst();
        if (job.activeWaypoint == null) {

            if (job.lastPlanTick == job.tickCounter) return false;
            job.lastPlanTick = job.tickCounter;
            PacketRoutePlanner.CollisionView view = collisionView(job);

            if (job.arrivalCandidateAt == 0L && groundingDue(job.spatialLegsSinceStable) && view != null && view.loaded(job.current)) {
                Vec3 localAnchor = PacketRoutePlanner.nearestGroundedAtColumn(view, job.current, 32.0D);
                if (localAnchor != null && localAnchor.distanceTo(job.current) > ARRIVAL_EPSILON) {
                    job.activeWaypoint = localAnchor;
                } else if (localAnchor != null) {
                    job.spatialLegsSinceStable = 0;
                }
            }
            PacketRoutePlanner.Route route = null;
            if (job.activeWaypoint == null && view != null) {

                Vec3 direct = roofStep(view, job, destination);
                if (direct == null) direct = directFastLane(view, job.current, destination);
                if (failedTarget(job, direct)) direct = null;
                if (direct == null) {
                    direct = verticalFirstCandidate(view, job.current, destination);
                    if (failedTarget(job, direct)) direct = null;
                }
                if (direct == null) {
                    direct = raisedArcCandidate(view, job.current, destination);
                    if (failedTarget(job, direct)) direct = null;
                }
                if (direct == null) {
                    direct = straightLegCandidate(view, job.current, destination);
                    if (failedTarget(job, direct)) direct = null;
                }
                if (direct == null && job.sameTargetFailures >= 2) {

                    direct = hopRouteCandidate(view, job, destination);
                }
                if (direct != null) {
                    job.activeWaypoint = direct;
                    job.lastPlannedTarget = direct;
                } else {
                    route = PacketRoutePlanner.planHybridToward(
                        view, job.current, destination, HYBRID_FRONTIER_BLOCKS + job.rescueLevel * 24, 128, 3);
                }
            }
            if (job.activeWaypoint != null) {

            } else if (acceptsPlannedRoute(route, job.current, destination)
                && routeLavaSafe(view, job.current, route.waypoints())) {
                job.waypoints.addAll(route.waypoints());
                job.activeWaypoint = job.waypoints.pollFirst();
                job.lastPlannedTarget = job.activeWaypoint;
            } else {

                job.waypoints.addAll(unrestrictedFallbackWaypoints(view, job.current, destination, job.rescueLevel));
                job.activeWaypoint = job.waypoints.pollFirst();
            }
            if (job.activeWaypoint == null) {

                return false;
            }
        }

        Vec3 from = job.current;
        Vec3 to = job.activeWaypoint;
        double distance = from.distanceTo(to);
        if (distance <= ARRIVAL_EPSILON) {
            job.activeWaypoint = null;
            return prepareNextFrames(job, destination, now, packetBudget);
        }
        if (job.lavaEmergency) {
            job.activeWaypoint = null;
            job.frames.addLast(MoveFrame.position(from, to, protocolGroundAt(job, to)));
            return true;
        }
        int available = Math.max(1, packetBudget);

        double stepLimit = job.vehicle != null ? Math.min(VEHICLE_STEP, job.effectiveStep) : job.effectiveStep;
        FastClipPlan clip = planFastClip(distance, stepLimit, available, job.incrementalRecovery);
        Vec3 next = from.add(to.subtract(from).scale(clip.amount() / distance));
        boolean deepDescent = next.y - from.y < -PacketClipSafety.DIRECT_FALL_LIMIT;
        if (deepDescent && !job.incrementalRecovery) {
            clip = planFastClip(distance, job.effectiveStep, Math.max(1, available - 2), false);
            next = from.add(to.subtract(from).scale(clip.amount() / distance));
        }

        boolean rerouting = false;
        PacketRoutePlanner.CollisionView sweepView = collisionView(job);
        Entity sweepEntity = sweepView == null ? null : sweepView.entity();
        if (sweepEntity != null && Math.hypot(next.x - from.x, next.z - from.z) > ARRIVAL_EPSILON) {
            Vec3 requested = next.subtract(from);
            Vec3 allowed = PacketClipSafety.sweptCollide(sweepEntity, from, next);
            if (!allowed.equals(requested)) {
                double wanted = Math.hypot(requested.x, requested.z);
                double contact = Math.hypot(allowed.x, allowed.z);
                if (contact >= wanted - 1.0E-6D) {

                    next = from.add(allowed);
                } else {
                    job.lastPlannedTarget = to;
                    job.lastFailedTarget = to;
                    job.sameTargetFailures += contact >= 0.5D ? 1 : 2;
                    job.waypoints.clear();
                    job.activeWaypoint = null;
                    if (contact < 0.5D) {

                        Vec3 over = riseOverCandidate(sweepView, job, destination);
                        if (over != null) {
                            job.activeWaypoint = over;
                            job.lastPlannedTarget = over;
                        } else {
                            Vec3 pocket = escapePocketCandidate(sweepView, job, destination);
                            if (pocket != null && !failedTarget(job, pocket)) {
                                job.activeWaypoint = pocket;
                                job.lastPlannedTarget = pocket;
                            }
                        }
                        return false;
                    }
                    next = from.add(allowed);
                    rerouting = true;
                }
            }
        }
        if (!rerouting && next.distanceTo(to) <= ARRIVAL_EPSILON) {
            next = to;
            job.activeWaypoint = null;
        }
        boolean fromGrounded = protocolGroundAt(job, from);
        boolean nextGrounded = protocolGroundAt(job, next);

        for (int i = 1; i < clip.packets(); i++) job.frames.addLast(MoveFrame.status(from, fromGrounded));
        job.frames.addLast(MoveFrame.position(from, next, nextGrounded));
        return true;
    }

    private static boolean routeAdvances(Vec3 from, Vec3 end, Vec3 destination) {
        if (from == null || end == null || destination == null) return false;
        double fromHorizontal = Math.hypot(destination.x - from.x, destination.z - from.z);
        double endHorizontal = Math.hypot(destination.x - end.x, destination.z - end.z);
        if (endHorizontal + ARRIVAL_EPSILON < fromHorizontal) return true;
        return fromHorizontal <= ARRIVAL_EPSILON
            && Math.abs(destination.y - end.y) + ARRIVAL_EPSILON < Math.abs(destination.y - from.y);
    }

    static boolean acceptsPlannedRoute(PacketRoutePlanner.Route route, Vec3 from, Vec3 destination) {
        return route != null && route.madeProgress()
            && (route.state() == PacketRoutePlanner.State.ESCAPE
                || routeAdvances(from, route.waypoints().getLast(), destination));
    }

    private static boolean armLavaEscape(Job job, long now) {
        PacketRoutePlanner.CollisionView view = collisionView(job);
        if (view == null || !view.loaded(job.current)) return false;
        if (view.lavaSafe(job.current)) return true;
        Vec3 escape = PacketRoutePlanner.safestLavaLanding(view, job.current, 32.0D);
        if (escape == null) return false;
        job.frames.clear();
        job.waypoints.clear();
        job.activeWaypoint = escape;
        job.lavaEmergency = true;
        job.incrementalRecovery = false;
        job.pacer.rebase(now, 0L);
        return true;
    }

    private static boolean frameLavaSafe(Job job, MoveFrame frame) {
        PacketRoutePlanner.CollisionView view = collisionView(job);
        if (view == null || frame == null) return false;
        if (!frame.moves()) return view.loaded(job.current) && view.lavaSafe(job.current);
        return packetStepsLavaSafe(view, frame.steps());
    }

    static boolean packetStepsLavaSafe(PacketRoutePlanner.CollisionView view,
                                       List<PacketClipSafety.Step> steps) {
        if (view == null || steps == null || steps.isEmpty()) return false;
        for (PacketClipSafety.Step step : steps) {
            if (step == null || step.position() == null || !view.loaded(step.position())
                || !view.lavaSafe(step.position())) return false;
        }
        return true;
    }

    private static boolean routeLavaSafe(PacketRoutePlanner.CollisionView view, Vec3 start, List<Vec3> route) {
        if (view == null || start == null || route == null || route.isEmpty()) return false;
        for (Vec3 waypoint : route) {
            if (waypoint == null || !view.loaded(waypoint) || !view.lavaSafe(waypoint)) return false;
        }
        return true;
    }

    static boolean lavaSegmentSafe(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 to) {
        if (view == null || from == null || to == null) return false;

        int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 0.3D));
        for (int i = 1; i <= samples; i++) {
            Vec3 sample = from.lerp(to, i / (double) samples);
            if (!view.loaded(sample) || !view.lavaSafe(sample)) return false;
        }
        return true;
    }

    private static Vec3 directFastLane(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 destination) {
        if (view == null || from == null || destination == null) return null;
        if (from.distanceTo(destination) <= ARRIVAL_EPSILON) return null;
        if (!view.loaded(destination) || !view.clear(destination) || !view.lavaSafe(destination)
            || !view.traversable(destination)) return null;
        return clearSegment(view, from, destination) && lavaSegmentSafe(view, from, destination)
            ? destination : null;
    }

    private static Vec3 verticalFirstCandidate(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 destination) {
        if (view == null || from == null || destination == null) return null;
        if (Math.abs(destination.y - from.y) < 4.0D) return null;
        Vec3 vertical = new Vec3(from.x, destination.y, from.z);
        if (!cleanStraight(view, from, vertical)) return null;

        if (Math.hypot(destination.x - vertical.x, destination.z - vertical.z) > ARRIVAL_EPSILON
            && !cleanStraight(view, vertical, destination)) return null;
        return vertical;
    }

    private static Vec3 raisedArcCandidate(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 destination) {
        if (view == null || from == null || destination == null) return null;
        if (from.distanceTo(destination) < 6.0D) return null;
        if (!view.loaded(destination) || !view.clear(destination) || !view.lavaSafe(destination)
            || !view.traversable(destination)) return null;
        for (int lift = 1; lift <= 4; lift++) {
            Vec3 mid = from.add(destination).scale(0.5D).add(0.0D, lift, 0.0D);
            if (!view.loaded(mid) || !view.clear(mid) || !view.lavaSafe(mid)) continue;

            if (submergedEye(view, mid)) continue;
            if (clearSegment(view, from, mid) && lavaSegmentSafe(view, from, mid)
                && clearSegment(view, mid, destination) && lavaSegmentSafe(view, mid, destination)) {
                return mid;
            }
        }
        return null;
    }

    private static Vec3 straightLegCandidate(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 destination) {
        if (view == null || from == null || destination == null) return null;
        double dx = destination.x - from.x;
        double dz = destination.z - from.z;
        if (Math.abs(dx) <= ARRIVAL_EPSILON && Math.abs(dz) <= ARRIVAL_EPSILON) return null;
        Vec3[] baseCorners = {
            new Vec3(destination.x, from.y, from.z),
            new Vec3(from.x, from.y, destination.z),
            new Vec3(destination.x, destination.y, from.z),
            new Vec3(from.x, destination.y, destination.z)
        };
        Vec3 best = null;
        double bestLength = 0.0D;
        for (Vec3 base : baseCorners) {
            for (int lift = 0; lift <= 4; lift++) {
                Vec3 corner = lift == 0 ? base : base.add(0.0D, lift, 0.0D);
                double length = from.distanceTo(corner);
                if (length < 4.0D) break;
                if (!cleanStraight(view, from, corner)) continue;

                if (submergedEye(view, corner)) continue;
                if (length > bestLength) {
                    bestLength = length;
                    best = corner;
                }
                break;
            }
        }

        double horizontal = Math.hypot(dx, dz);
        return best != null && bestLength >= Math.max(4.0D, horizontal * 0.34D) ? best : null;
    }

    private static boolean cleanStraight(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 to) {
        if (!view.loaded(to) || !view.clear(to) || !view.lavaSafe(to)) return false;
        Entity entity = view.entity();
        if (entity != null) {

            double distance = from.distanceTo(to);
            int segments = Math.max(1, (int) Math.ceil(distance / 8.0D));
            Vec3 prev = from;
            for (int i = 1; i <= segments; i++) {
                Vec3 next = from.lerp(to, i / (double) segments);
                if (!PacketClipSafety.sweptClear(entity, prev, next)) return false;
                prev = next;
            }
            return lavaSegmentSafe(view, from, to);
        }
        return clearSegment(view, from, to) && lavaSegmentSafe(view, from, to);
    }

    private static boolean submergedEye(PacketRoutePlanner.CollisionView view, Vec3 pos) {
        Entity entity = view == null ? null : view.entity();
        if (entity == null || entity.level() == null || pos == null) return false;
        double eyeY = pos.y + entity.getEyeHeight();
        net.minecraft.core.BlockPos eyeBlock = net.minecraft.core.BlockPos.containing(pos.x, eyeY, pos.z);
        net.minecraft.world.level.material.FluidState fluid = entity.level().getFluidState(eyeBlock);
        return !fluid.isEmpty() && eyeY < eyeBlock.getY() + fluid.getHeight(entity.level(), eyeBlock);
    }

    private static Vec3 escapePocketCandidate(PacketRoutePlanner.CollisionView view, Job job, Vec3 destination) {
        if (view == null || job == null || destination == null) return null;
        Vec3 current = job.current;
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int dir = 0; dir < 8; dir++) {
            double angle = dir * (Math.PI / 4.0D);
            double dx = -Math.sin(angle);
            double dz = Math.cos(angle);
            double reach = 0.0D;
            double maxReach = 4.0D + job.rescueLevel * 4.0D;
            for (int step = 1; step <= maxReach; step++) {
                Vec3 candidate = new Vec3(current.x + dx * step, current.y, current.z + dz * step);
                if (!cleanStraight(view, current, candidate)) break;
                reach = step;
            }
            if (reach <= 0.0D) continue;
            Vec3 end = new Vec3(current.x + dx * reach, current.y, current.z + dz * reach);
            double regression = end.distanceTo(destination) - current.distanceTo(destination);
            double score = reach - regression * 0.25D;
            if (score > bestScore) {
                bestScore = score;
                best = end;
            }
        }
        return best;
    }

    private static boolean failedTarget(Job job, Vec3 candidate) {
        return candidate != null && job.lastFailedTarget != null
            && candidate.distanceTo(job.lastFailedTarget) < 1.5D;
    }

    private static Vec3 hopRouteCandidate(PacketRoutePlanner.CollisionView view, Job job, Vec3 destination) {
        if (view == null || job == null) return null;

        Vec3 flatAtFeet = new Vec3(destination.x, job.current.y, destination.z);
        if (cleanStraight(view, job.current, flatAtFeet)) return null;
        return riseOverCandidate(view, job, destination);
    }

    private static Vec3 riseOverCandidate(PacketRoutePlanner.CollisionView view, Job job, Vec3 destination) {
        if (view == null || job == null || destination == null) return null;
        Vec3 current = job.current;
        double horizontal = Math.hypot(destination.x - current.x, destination.z - current.z);
        if (horizontal <= ARRIVAL_EPSILON) return null;
        double reach = Math.min(8.0D, horizontal);
        double dirX = (destination.x - current.x) / horizontal;
        double dirZ = (destination.z - current.z) / horizontal;
        int maxLift = 32 + job.rescueLevel * 8;
        for (int lift = 1; lift <= maxLift; lift++) {
            Vec3 lifted = new Vec3(current.x, current.y + lift, current.z);
            if (!cleanStraight(view, current, lifted)) return null;
            Vec3 ahead = new Vec3(lifted.x + dirX * reach, lifted.y, lifted.z + dirZ * reach);
            if (cleanStraight(view, lifted, ahead)) {
                job.roofY = lifted.y;
                return lifted;
            }
        }
        return null;
    }

    private static Vec3 roofStep(PacketRoutePlanner.CollisionView view, Job job, Vec3 destination) {
        if (view == null || job == null || Double.isNaN(job.roofY)) return null;
        Vec3 current = job.current;

        Vec3 drop = new Vec3(current.x, destination.y, current.z);
        if (cleanStraight(view, current, drop) && cleanStraight(view, drop, destination)) {
            job.roofY = Double.NaN;
            return drop;
        }

        double dx = destination.x - current.x;
        double dz = destination.z - current.z;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal > ARRIVAL_EPSILON) {
            double step = Math.min(4.0D, horizontal);
            Vec3 flat = new Vec3(current.x + dx / horizontal * step, job.roofY, current.z + dz / horizontal * step);
            if (cleanStraight(view, current, flat)) return flat;
        }

        job.roofY = Double.NaN;
        return null;
    }

    private static boolean clearSegment(PacketRoutePlanner.CollisionView view, Vec3 from, Vec3 to) {

        int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 0.3D));
        for (int i = 1; i <= samples; i++) {
            Vec3 sample = from.lerp(to, i / (double) samples);
            if (!view.loaded(sample) || !view.clear(sample)) return false;
        }
        return true;
    }

    static List<Vec3> unrestrictedFallbackWaypoints(PacketRoutePlanner.CollisionView view,
                                                     Vec3 start, Vec3 destination) {
        return unrestrictedFallbackWaypoints(view, start, destination, 0);
    }

    static List<Vec3> unrestrictedFallbackWaypoints(PacketRoutePlanner.CollisionView view,
                                                     Vec3 start, Vec3 destination, int rescueLevel) {
        return PacketRoutePlanner.safeFallbackWaypoints(
            view, start, destination, BLIND_POV_FRONTIER_BLOCKS + Math.max(0, rescueLevel) * 16);
    }

    static List<Vec3> unrestrictedFallbackWaypoints(Vec3 start, Vec3 destination) {
        return List.of();
    }

    static int nextGroundingCounter(int current, boolean stable) {
        return stable ? 0 : Math.min(5, Math.max(0, current) + 1);
    }

    static boolean groundingDue(int spatialLegsSinceStable) {
        return spatialLegsSinceStable >= 4;
    }

    static FastClipPlan planFastClip(double distance, double step, int packetBudget, boolean recovery) {
        double safeDistance = Math.max(0.0D, distance);
        double safeStep = Math.max(MIN_STEP, Math.min(DEFAULT_STEP, step));
        int available = Math.max(1, packetBudget);
        if (recovery) return new FastClipPlan(Math.min(safeDistance, safeStep), 1, false);
        int packets = Math.max(1, Math.min(available, (int) Math.ceil(safeDistance / safeStep)));
        return new FastClipPlan(safeDistance, packets, true);
    }

    static boolean correctionAccepted(double bestDistance, double predictedDistance,
                                      double correctedDistance, double predictionError) {
        return bestDistance - correctedDistance >= 0.25D
            || predictionError <= CORRECTION_MATCH_EPSILON
            || correctedDistance + CORRECTION_MATCH_EPSILON < predictedDistance;
    }

    private static boolean send(Job job, MoveFrame frame) {
        if (job.owner == Owner.POV) {
            if (!frame.moves()) return job.session.pilotTeleportStatus(frame.onGround());
            return job.session.pilotTeleportSequence(frame.steps());
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return false;

        boolean noFall = job.owner == Owner.MAIN && TpClickModule.noFallActive();
        boolean previousOwnedSend = enterOwnedSendScope();
        try {
            if (job.vehicle != null) {
                if (frame.moves()) {

                    for (PacketClipSafety.Step step : frame.steps()) {
                        job.vehicle.setPos(step.position());
                        mc.getConnection().send(ServerboundMoveVehiclePacket.fromEntity(job.vehicle));
                    }
                    job.vehicle.positionRider(mc.player);
                } else {
                    mc.getConnection().send(ServerboundMoveVehiclePacket.fromEntity(job.vehicle));
                }
            } else if (!frame.moves()) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(noFall || frame.onGround(), false));
            } else {
                for (PacketClipSafety.Step step : frame.steps()) {
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                        antiKickPosition(job, step.position()), step.onGround(), false));
                }
            }
            return true;
        } finally {
            exitOwnedSendScope(previousOwnedSend);
        }
    }

    private static Vec3 antiKickPosition(Job job, Vec3 position) {
        if (job.owner != Owner.MAIN || !TpClickModule.antiKickActive()) return position;
        long now = System.currentTimeMillis();
        if (now - job.lastAntiKickNudgeAt < 1_000L) return position;

        double lastY = job.lastSentPosition == null ? position.y : job.lastSentPosition.y;
        if (lastY - position.y >= 0.03130D) return position;
        PacketRoutePlanner.CollisionView view = collisionView(job);
        if (view == null || !view.clear(position) || view.supported(position)) return position;
        job.lastAntiKickNudgeAt = now;
        return new Vec3(position.x, position.y - 0.03130D, position.z);
    }

    private static int packetCount(Job job, MoveFrame frame) {
        if (!frame.moves()) return 1;
        return Math.max(1, frame.steps().size());
    }

    private static void applyRendered(Job job, Vec3 position, boolean onGround) {
        if (job.owner == Owner.POV) {
            MultiPilot.applyPacedTeleportStep(job.session, position, onGround);
            return;
        }
        LocalPlayer player = job.player;
        if (player == null) return;
        if (job.vehicle != null) job.vehicle.setPos(position);
        if (job.vehicle != null) job.vehicle.positionRider(player);
        else player.setPos(position.x, position.y, position.z);
        player.setOnGround(onGround);
        player.setDeltaMovement(Vec3.ZERO);
        player.xxa = 0.0F;
        player.zza = 0.0F;
        player.setJumping(false);
        player.resetFallDistance();
    }

    private static PacketRoutePlanner.CollisionView collisionView(Job job) {
        Entity entity = job.owner == Owner.POV ? job.bot : job.vehicle != null ? job.vehicle : job.player;
        return PacketRoutePlanner.forEntity(entity);
    }

    private static boolean protocolGroundAt(Job job, Vec3 position) {
        PacketRoutePlanner.CollisionView view = collisionView(job);
        return protocolGround(view, position);
    }

    private static boolean stableAnchorAt(Job job, Vec3 position) {
        PacketRoutePlanner.CollisionView view = collisionView(job);
        return view != null && position != null && view.loaded(position)
            && view.clear(position) && view.lavaSafe(position) && view.traversable(position);
    }

    static boolean protocolGround(PacketRoutePlanner.CollisionView view, Vec3 position) {
        return view != null && position != null && view.loaded(position)
            && view.clear(position) && view.lavaSafe(position) && view.supported(position);
    }

    public static void onMainCorrection(Vec3 corrected) {
        synchronized (LOCK) {
            if (active != null && active.owner == Owner.MAIN && active.vehicle == null && corrected != null) {
                active.pendingCorrection = new Correction(corrected);
            }
        }
    }

    public static void onMainVehicleCorrection(Vec3 corrected) {
        synchronized (LOCK) {
            if (active != null && active.owner == Owner.MAIN && active.vehicle != null && corrected != null) {
                active.pendingCorrection = new Correction(corrected);
            }
        }
    }

    static void onPovCorrection(MultiSession session, Vec3 corrected) {
        synchronized (LOCK) {
            if (active != null && active.owner == Owner.POV && active.session == session && corrected != null) {
                active.pendingCorrection = new Correction(corrected);
            }
        }
    }

    public static boolean ownsMainMovement() {
        Job job = active;
        return job != null && job.owner == Owner.MAIN;
    }

    static boolean ownsPov(MultiSession session) {
        Job job = active;
        return job != null && job.owner == Owner.POV && job.session == session;
    }

    public static boolean isControllerOwnedSend() {
        if (OWNED_SEND_SCOPES.get() == 0) return false;
        return Boolean.TRUE.equals(OWNED_SEND.get());
    }

    public static void runAtomicClipSend(Runnable action) {
        if (action == null) return;
        boolean previous = enterOwnedSendScope();
        try {
            action.run();
        } finally {
            exitOwnedSendScope(previous);
        }
    }

    private static boolean enterOwnedSendScope() {
        boolean previous = Boolean.TRUE.equals(OWNED_SEND.get());
        if (!previous) {
            OWNED_SEND.set(true);
            OWNED_SEND_SCOPES.incrementAndGet();
        }
        return previous;
    }

    private static void exitOwnedSendScope(boolean previous) {
        if (previous) return;
        OWNED_SEND.set(false);
        OWNED_SEND_SCOPES.decrementAndGet();
    }

    public static boolean shouldSuppressMainMovement(Packet<?> packet) {
        if (!(packet instanceof ServerboundMovePlayerPacket) && !(packet instanceof ServerboundMoveVehiclePacket)
            && !(packet instanceof ServerboundPlayerInputPacket)) {
            return false;
        }
        Job job = active;
        if (job == null || job.owner != Owner.MAIN || isControllerOwnedSend()) return false;

        return job.arrivalCandidateAt == 0L;
    }

    public static void cancelAll(String reason) {
        Job job;
        synchronized (LOCK) {
            job = active;
        }
        if (job != null) finish(job, false, reason == null ? "cancelled" : reason);
    }

    static void cancelPov(MultiSession session, String reason) {
        Job job;
        synchronized (LOCK) {
            job = active != null && active.owner == Owner.POV && active.session == session ? active : null;
        }
        if (job != null) finish(job, false, reason == null ? "POV ownership changed" : reason);
    }

    private static String validateOwner(Job job, Minecraft mc) {
        if (job.owner == Owner.POV) {
            if (MultiPilot.activeCommandSession() != job.session || !job.session.isPiloted()) return "POV ownership changed";
            if (!job.session.pilotPacketsReady()) return "POV connection changed";
            if (job.session.macroOwnsPilot()) return "macro took POV ownership";
            if (!job.dimension.equals(job.session.takeoverDimension())) return "POV dimension changed";
            if (job.bot == null || job.bot.isDeadOrDying()) return "POV bot died";
            return null;
        }
        if (MultiPilot.isActive()) return "POV ownership changed";
        if (job.macroOwned ? !MacroExecutor.isRunning() : MacroExecutor.isRunning()) {
            return job.macroOwned ? "owning macro stopped" : "macro took movement ownership";
        }
        if (mc.player != job.player || mc.getConnection() != job.mainConnection || mc.level != job.level) {
            return "player connection changed";
        }
        if (job.player.isDeadOrDying()) return "player died";
        if (job.vehicle != null && job.player.getVehicle() != job.vehicle) return "vehicle ownership changed";
        return null;
    }

    private static boolean physicalMovementDown(Minecraft mc) {
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
            || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()
            || mc.options.keyJump.isDown() || mc.options.keyShift.isDown();
    }

    private static void releaseMovementInput(Minecraft mc) {
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
    }

    private static boolean inputWasBlocked;

    private static void restoreMovementInput(Minecraft mc) {
        if (mc == null || mc.options == null) return;
        autismclient.util.AutismKeyMappingBridge.of(mc.options.keyUp).autism$resetPressedState();
        autismclient.util.AutismKeyMappingBridge.of(mc.options.keyDown).autism$resetPressedState();
        autismclient.util.AutismKeyMappingBridge.of(mc.options.keyLeft).autism$resetPressedState();
        autismclient.util.AutismKeyMappingBridge.of(mc.options.keyRight).autism$resetPressedState();
        autismclient.util.AutismKeyMappingBridge.of(mc.options.keyJump).autism$resetPressedState();
        autismclient.util.AutismKeyMappingBridge.of(mc.options.keyShift).autism$resetPressedState();
    }

    private static String status(MultiSession requestedOwner) {
        synchronized (LOCK) {
            if (active == null) return "No active TP";
            if (requestedOwner != null && (active.owner != Owner.POV || active.session != requestedOwner)) {
                return "No active TP for this POV";
            }
            return String.format(Locale.ROOT,
                "TP %s%s: %.2f %.2f %.2f -> %.2f %.2f %.2f, %d packets, step %.3f",
                active.owner == Owner.POV ? active.session.accountId() : "main",
                active.incrementalRecovery ? " recovery" : "",
                active.current.x, active.current.y, active.current.z,
                active.requestedTarget.x, active.requestedTarget.y, active.requestedTarget.z,
                active.totalPackets, active.effectiveStep);
        }
    }

    private static String stop(MultiSession requestedOwner, String reason) {
        Job job;
        synchronized (LOCK) {
            job = active;
            if (job == null) return "No active TP";
            if (requestedOwner != null && (job.owner != Owner.POV || job.session != requestedOwner)) {
                return "No active TP for this POV";
            }
        }
        synchronized (LOCK) {
            if (active == job) active = null;
        }
        if (job.owner == Owner.MAIN && job.player != null) job.player.setDeltaMovement(Vec3.ZERO);
        if (job.completion != null) job.completion.complete(new MacroResult(false, reason));
        return "TP stopped";
    }

    private static void finish(Job job, boolean success, String reason) {
        synchronized (LOCK) {
            if (active != job) return;
            active = null;
        }
        if (job.owner == Owner.MAIN && inputWasBlocked) {

            inputWasBlocked = false;
            restoreMovementInput(Minecraft.getInstance());
        }
        if (job.owner == Owner.MAIN && job.player != null) {
            job.player.setDeltaMovement(Vec3.ZERO);
        }
        if (job.completion != null) job.completion.complete(new MacroResult(success, reason));
        String color = success ? "§a" : "§e";
        autismclient.util.AutismClientMessaging.sendPrefixed(color + "TP " + (success ? "complete: " : "stopped: ")
            + "§f" + reason);
    }

    private static Vec3 controlledMainPosition(LocalPlayer player) {
        Entity vehicle = player.getVehicle();
        return vehicle == null ? player.position() : vehicle.position();
    }

    private static void neutralizeMain(LocalPlayer player) {
        if (player == null || Minecraft.getInstance().getConnection() == null) return;
        boolean sprinting = player.isSprinting();
        player.xxa = 0.0F;
        player.zza = 0.0F;
        player.setJumping(false);
        player.setShiftKeyDown(false);
        player.setSprinting(false);
        player.stopFallFlying();
        player.getAbilities().flying = false;
        PacketRoutePlanner.CollisionView view = PacketRoutePlanner.forEntity(player);
        player.setOnGround(view != null && view.loaded(player.position())
            && view.lavaSafe(player.position()) && view.supported(player.position()));
        player.setDeltaMovement(Vec3.ZERO);
        boolean previousOwnedSend = enterOwnedSendScope();
        try {
            Minecraft.getInstance().getConnection().send(new ServerboundPlayerInputPacket(Input.EMPTY));
            Minecraft.getInstance().getConnection().send(new ServerboundPlayerAbilitiesPacket(player.getAbilities()));
            if (sprinting) {
                Minecraft.getInstance().getConnection().send(new ServerboundPlayerCommandPacket(
                    player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            }
        } finally {
            exitOwnedSendScope(previousOwnedSend);
        }
    }

    private static String landingMessage(Job job) {
        if (!job.adjustedLanding) return String.format(Locale.ROOT, "arrived at %.2f %.2f %.2f",
            job.current.x, job.current.y, job.current.z);
        return String.format(Locale.ROOT, "adjusted landing %.2f %.2f %.2f",
            job.current.x, job.current.y, job.current.z);
    }

    private static String startedMessage(Vec3 destination, int packets, int pause, boolean vehicle) {
        return String.format(Locale.ROOT, "TP started%s: %.2f %.2f %.2f · %d packets / %d ms",
            vehicle ? " for vehicle" : "",
            destination.x, destination.y, destination.z, packets, pause);
    }

    private static Double coordinate(String token, double origin) {
        if (token == null || token.isBlank() || token.charAt(0) == '^') return null;
        try {
            double value;
            if (token.charAt(0) == '~') {
                value = token.length() == 1 ? origin : origin + Double.parseDouble(token.substring(1));
            } else {
                value = Double.parseDouble(token);
            }
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer boundedInteger(String value, int min, int max) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= min && parsed <= max ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Pacer {
        private final int maxPackets;
        private final int pauseMs;
        private int packetsInWindow;
        private long resumeAt;
        private long lastSendTick = Long.MIN_VALUE;

        public Pacer(int maxPackets, int pauseMs) {
            this.maxPackets = clamp(maxPackets, 1, 100);
            this.pauseMs = clamp(pauseMs, 50, 10_000);
        }

        public boolean canSend(long tick, long now) {
            if (tick == lastSendTick || now < resumeAt) return false;
            lastSendTick = tick;
            return true;
        }

        public void markSent(long now) {
            packetsInWindow++;
            if (packetsInWindow >= maxPackets) {
                packetsInWindow = 0;
                resumeAt = now + pauseMs;
            }
        }

        public boolean waiting(long now) { return now < resumeAt; }
        public int remainingInWindow() { return Math.max(1, maxPackets - packetsInWindow); }
        public int packetsInWindow() { return packetsInWindow; }
        public long resumeAt() { return resumeAt; }

        public void rebase(long now, long delayMs) {
            packetsInWindow = 0;
            resumeAt = now + Math.max(0L, delayMs);
            lastSendTick = Long.MIN_VALUE;
        }
    }

    private enum Owner { MAIN, POV }
    record FastClipPlan(double amount, int packets, boolean banked) {}
    private record MoveFrame(Vec3 position, boolean moves, boolean onGround,
                             java.util.List<PacketClipSafety.Step> steps) {
        MoveFrame {
            steps = steps == null ? java.util.List.of() : java.util.List.copyOf(steps);
        }

        static MoveFrame status(Vec3 position, boolean onGround) {
            return new MoveFrame(position, false, onGround, java.util.List.of());
        }

        static MoveFrame position(Vec3 from, Vec3 to, boolean onGround) {
            return new MoveFrame(to, true, onGround, PacketClipSafety.positionSteps(from, to, onGround));
        }
    }
    private record Correction(Vec3 position) {}

    private static final class Job {
        final Owner owner;
        final LocalPlayer player;
        final Object mainConnection;
        final Object level;
        final Entity vehicle;
        final MultiSession session;
        final RemotePlayer bot;
        final String dimension;
        final Vec3 requestedTarget;
        final Pacer pacer;
        final boolean macroOwned;
        final long macroId;
        final CompletableFuture<MacroResult> completion;
        final Deque<Vec3> waypoints = new ArrayDeque<>();
        final Deque<MoveFrame> frames = new ArrayDeque<>();
        Vec3 current;
        Vec3 lastSentPosition;
        Vec3 resolvedTarget;
        Vec3 activeWaypoint;
        double effectiveStep;
        double bestDistance;
        int totalPackets;
        long tickCounter;
        long lastPlanTick = Long.MIN_VALUE;
        final long startedAt = System.currentTimeMillis();
        long lastProgressAt = startedAt;

        long lastActivityAt = startedAt;
        int rescueLevel;
        long arrivalCandidateAt;
        long recoveryNextSendAt;
        long recoveryStableAt;
        long nextDestinationSearchAt;
        double recoveryAnchorDistance;
        int spatialLegsSinceStable;
        int rejectionCount;

        Vec3 lastPlannedTarget;
        Vec3 lastFailedTarget;
        int sameTargetFailures;

        double roofY = Double.NaN;
        long lastAntiKickNudgeAt;
        int lavaDestinationSearchRadius = 12;
        boolean adjustedLanding;
        boolean incrementalRecovery;
        boolean lavaEmergency;
        Correction pendingCorrection;

        private Job(Owner owner, LocalPlayer player, Object mainConnection, Object level, Entity vehicle,
                    MultiSession session, RemotePlayer bot, String dimension, Vec3 start, CommandRequest request,
                    boolean macroOwned, long macroId, CompletableFuture<MacroResult> completion) {
            this.owner = owner;
            this.player = player;
            this.mainConnection = mainConnection;
            this.level = level;
            this.vehicle = vehicle;
            this.session = session;
            this.bot = bot;
            this.dimension = dimension == null ? "" : dimension;
            this.current = start;
            this.lastSentPosition = start;
            this.requestedTarget = request.destination();
            this.effectiveStep = DEFAULT_STEP;
            this.bestDistance = start.distanceTo(request.destination());
            this.pacer = new Pacer(request.maxPackets(), request.pauseMs());
            this.macroOwned = macroOwned;
            this.macroId = macroId;
            this.completion = completion;
        }

        static Job main(LocalPlayer player, Object connection, Object level, Entity vehicle, CommandRequest request) {
            return new Job(Owner.MAIN, player, connection, level, vehicle, null, null, "",
                vehicle == null ? player.position() : vehicle.position(), request, false, 0L, null);
        }

        static Job mainMacro(LocalPlayer player, Object connection, Object level, Entity vehicle,
                             CommandRequest request, long macroId, CompletableFuture<MacroResult> completion) {
            return new Job(Owner.MAIN, player, connection, level, vehicle, null, null, "",
                vehicle == null ? player.position() : vehicle.position(), request, true, macroId, completion);
        }

        static Job pov(MultiSession session, RemotePlayer bot, CommandRequest request) {
            Vec3 start = session.takeoverPosition().position();
            return new Job(Owner.POV, null, null, null, null, session, bot, session.takeoverDimension(), start,
                request, false, 0L, null);
        }
    }
}
