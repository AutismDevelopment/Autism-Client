package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

public class ServerTickTracker {
    private static final long TICK_DURATION_NANOS = 50_000_000L;
    private static final Minecraft MC = Minecraft.getInstance();

    private static final long MIN_WARMUP_MS = 2000;
    private static final int MIN_SAMPLES = 40;

    private static final int WORLD_TIME_SAMPLE_COUNT = 60;
    private static final long[] worldTimeSamples = new long[WORLD_TIME_SAMPLE_COUNT];
    private static int worldTimeSampleIndex = 0;
    private static int worldTimeSamplesFilled = 0;

    private static volatile long trackingStartTime = 0;
    private static volatile long lastWorldTimePacket = 0;
    private static volatile long lastServerGameTime = Long.MIN_VALUE;
    private static volatile double estimatedTps = 20.0;

    private static volatile long averagedTickPhase = 0;
    private static volatile long baseTickTime = 0;

    private static volatile long lastEntityPacketTime = 0;
    private static volatile int entityPacketBurstCount = 0;
    private static final long BURST_THRESHOLD_NANOS = 5_000_000L;

    public static void onWorldTimePacket(ClientboundSetTimePacket packet) {
        long now = System.nanoTime();

        if (trackingStartTime == 0) {
            trackingStartTime = now;
        }

        long previousPacketTime = lastWorldTimePacket;
        long previousGameTime = lastServerGameTime;
        lastWorldTimePacket = now;
        lastServerGameTime = packet.gameTime();
        if (previousPacketTime > 0 && previousGameTime != Long.MIN_VALUE) {
            long gameTicks = packet.gameTime() - previousGameTime;
            long elapsedNanos = now - previousPacketTime;
            if (gameTicks > 0 && gameTicks <= 200 && elapsedNanos > 0) {
                double sample = Math.max(0.0, Math.min(20.0, (gameTicks * 1_000_000_000.0) / elapsedNanos));
                estimatedTps = estimatedTps <= 0.0 ? sample : (estimatedTps * 0.75) + (sample * 0.25);
            }
        }

        int pingMs = getPingMs();
        long halfPingNanos = (pingMs * 1_000_000L) / 2;
        long serverSendTime = now - halfPingNanos;

        worldTimeSamples[worldTimeSampleIndex] = serverSendTime;
        worldTimeSampleIndex = (worldTimeSampleIndex + 1) % WORLD_TIME_SAMPLE_COUNT;
        if (worldTimeSamplesFilled < WORLD_TIME_SAMPLE_COUNT) worldTimeSamplesFilled++;

        updateAveragedPhase();
    }

    public static void onEntityPacket() {
        long now = System.nanoTime();

        if (now - lastEntityPacketTime < BURST_THRESHOLD_NANOS) {
            entityPacketBurstCount++;
        } else {
            entityPacketBurstCount = 1;
        }

        lastEntityPacketTime = now;
    }

    public static void onS2CPacket(Packet<?> packet) {

        if (packet instanceof ClientboundSetTimePacket worldTimePacket) {
            onWorldTimePacket(worldTimePacket);
        } else if (packet instanceof ClientboundMoveEntityPacket.Pos ||
                   packet instanceof ClientboundSetEntityMotionPacket ||
                   packet instanceof ClientboundPlayerPositionPacket) {
            onEntityPacket();
        }

    }

    private static void updateAveragedPhase() {
        if (worldTimeSamplesFilled < 3) return;

        long sumPhase = 0;
        int validSamples = Math.min(worldTimeSamplesFilled, WORLD_TIME_SAMPLE_COUNT);

        for (int i = 0; i < validSamples; i++) {

            long phase = worldTimeSamples[i] % TICK_DURATION_NANOS;
            sumPhase += phase;
        }

        averagedTickPhase = sumPhase / validSamples;

        long mostRecent = worldTimeSamples[(worldTimeSampleIndex - 1 + WORLD_TIME_SAMPLE_COUNT) % WORLD_TIME_SAMPLE_COUNT];
        long tickNumber = mostRecent / TICK_DURATION_NANOS;
        baseTickTime = (tickNumber * TICK_DURATION_NANOS) + averagedTickPhase;

        long now = System.nanoTime();
        if (baseTickTime > now) {
            baseTickTime -= TICK_DURATION_NANOS;
        }
    }

    public static int getPingMs() {
        if (MC.getConnection() == null || MC.player == null) return 0;

        PlayerInfo entry = MC.getConnection().getPlayerInfo(MC.player.getUUID());
        if (entry == null) return 0;

        return entry.getLatency();
    }

    public static long getNextServerTickNanos() {
        if (baseTickTime == 0) {
            return System.nanoTime() + TICK_DURATION_NANOS;
        }

        long now = System.nanoTime();
        long elapsed = now - baseTickTime;
        long ticksElapsed = elapsed / TICK_DURATION_NANOS;

        return baseTickTime + ((ticksElapsed + 1) * TICK_DURATION_NANOS);
    }

    public static long getOptimalSendTime(int bufferMs, boolean ignorePing) {
        long nextTick = getNextServerTickNanos();

        int pingMs = ignorePing ? 0 : getPingMs();
        long halfPingNanos = (pingMs * 1_000_000L) / 2;
        long bufferNanos = bufferMs * 1_000_000L;

        long optimalTime = nextTick - bufferNanos - halfPingNanos;

        long now = System.nanoTime();
        while (optimalTime <= now) {
            optimalTime += TICK_DURATION_NANOS;
        }

        return optimalTime;
    }

    public static boolean isReady() {
        if (trackingStartTime == 0 || worldTimeSamplesFilled < MIN_SAMPLES) {
            return false;
        }

        long elapsedMs = (System.nanoTime() - trackingStartTime) / 1_000_000L;
        return elapsedMs >= MIN_WARMUP_MS && baseTickTime > 0;
    }

    public static float getWarmupProgress() {
        if (trackingStartTime == 0) return 0f;

        long elapsedMs = (System.nanoTime() - trackingStartTime) / 1_000_000L;
        float timeProgress = Math.min(1f, elapsedMs / (float) MIN_WARMUP_MS);
        float sampleProgress = Math.min(1f, worldTimeSamplesFilled / (float) MIN_SAMPLES);

        return Math.min(timeProgress, sampleProgress);
    }

    public static float getConfidence() {
        if (!isReady()) return 0f;

        float sampleConfidence = Math.min(1f, worldTimeSamplesFilled / (float) WORLD_TIME_SAMPLE_COUNT);

        long timeSinceLast = System.nanoTime() - lastWorldTimePacket;
        float freshnessConfidence = timeSinceLast < 100_000_000L ? 1f :
            Math.max(0f, 1f - (timeSinceLast / 500_000_000f));

        return sampleConfidence * freshnessConfidence;
    }

    public static long getMsUntilOptimal(int bufferMs, boolean ignorePing) {
        long optimalTime = getOptimalSendTime(bufferMs, ignorePing);
        return Math.max(0, (optimalTime - System.nanoTime()) / 1_000_000L);
    }

    public static long getMsUntilNextTick() {
        long nextTick = getNextServerTickNanos();
        return Math.max(0, (nextTick - System.nanoTime()) / 1_000_000L);
    }

    public static long getMsSinceLastTick() {
        if (lastWorldTimePacket == 0) return 0;
        return Math.max(0, (System.nanoTime() - lastWorldTimePacket) / 1_000_000L);
    }

    public static double getEstimatedTps() {
        return Math.max(0.0, Math.min(20.0, estimatedTps));
    }

    public static int getSampleCount() {
        return worldTimeSamplesFilled;
    }

    public static long getTrackingTimeMs() {
        if (trackingStartTime == 0) return 0;
        return (System.nanoTime() - trackingStartTime) / 1_000_000L;
    }

    public static long getAveragedPhaseMs() {
        return averagedTickPhase / 1_000_000L;
    }

    public static void reset() {
        worldTimeSamplesFilled = 0;
        worldTimeSampleIndex = 0;
        trackingStartTime = 0;
        lastWorldTimePacket = 0;
        lastServerGameTime = Long.MIN_VALUE;
        estimatedTps = 20.0;
        averagedTickPhase = 0;
        baseTickTime = 0;
        lastEntityPacketTime = 0;
        entityPacketBurstCount = 0;
    }

    public static String getDebugInfo() {
        return String.format("Ping: %dms | Samples: %d/%d | Phase: %dms | Confidence: %.0f%%",
            getPingMs(), worldTimeSamplesFilled, MIN_SAMPLES,
            getAveragedPhaseMs(), getConfidence() * 100);
    }
}
