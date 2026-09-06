package autismclient.util.oresim;

import autismclient.util.worldgen.mc26_2.AutismRegionGenerator;
import autismclient.util.worldgen.mc26_2.AutismWorldgenContext;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

public final class AutismOreSimEngine {

    public static final int MAX_SIM_RADIUS = 8;
    private static final int TILE_SIZE = 4;
    private static final int REGION_SIZE = TILE_SIZE + AutismRegionGenerator.MARGIN * 2;
    private static final int INITIAL_REGION_SIZE = AutismRegionGenerator.MARGIN * 2 + 1;
    private static final int MAX_POSITIONS = 600_000;
    private static final int MAX_CONTEXTS = 3;
    private static final long TAG_VERIFY_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);

    private static final Object STATE_LOCK = new Object();
    private static final Map<Long, ChunkOres> CHUNKS = new ConcurrentHashMap<>();

    private static final Map<Long, ChunkOres> PENDING_CHUNKS = new HashMap<>();
    private static final LongOpenHashSet DISPROVEN = new LongOpenHashSet();
    private static final int MAX_DISPROVEN = 200_000;
    private static final AutismOreSimWorker WORKER = new AutismOreSimWorker("Autism-OreSim-Worldgen");
    private static final LinkedHashMap<ContextKey, AutismWorldgenContext> CONTEXT_CACHE =
        new LinkedHashMap<>(4, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ContextKey, AutismWorldgenContext> eldest) {
                return size() > MAX_CONTEXTS;
            }
        };

    private static ContextKey activeKey;
    private static volatile AutismWorldgenContext activeContext;
    private static volatile long epoch;
    private static volatile boolean contextLoading;
    private static volatile boolean generationInFlight;

    private static boolean tagVerificationInFlight;
    private static volatile boolean failed;
    private static volatile boolean unsupportedDimension;
    private static volatile boolean unverifiedWorldgen;
    private static volatile String failureMessage = "";
    private static int playerBlockX;
    private static int playerBlockZ;
    private static int targetRadius = 1;

    private static boolean initialChunkGenerated;
    private static final AtomicInteger REVISION = new AtomicInteger();
    private static volatile int totalPositions;
    private static volatile boolean positionCapHit;
    private static volatile boolean selectionTruncated;

    private static volatile long suspendedAtNanos;
    private static long nextTagVerificationNanos;

    private AutismOreSimEngine() {
    }

    public enum Status {
        IDLE,
        LOADING_CONTEXT,
        GENERATING,
        READY,
        UNVERIFIED_WORLDGEN,
        UNSUPPORTED_DIMENSION,
        ERROR
    }

    private record ContextKey(long seed, ResourceKey<Level> dimension) {
    }

    private record Tile(int minChunkX, int minChunkZ, double distanceSq) {
        long key() {
            return ChunkPos.pack(minChunkX, minChunkZ);
        }
    }

    public record TargetProgress(long loadId, int completedChunks, int totalChunks) {
        public TargetProgress {
            totalChunks = Math.max(0, totalChunks);
            completedChunks = Math.max(0, Math.min(completedChunks, totalChunks));
        }

        public double fraction() {
            return totalChunks == 0 ? 0.0 : completedChunks / (double) totalChunks;
        }

        public boolean complete() {
            return totalChunks > 0 && completedChunks >= totalChunks;
        }
    }

    private static final class ChunkOres {
        static final ChunkOres EMPTY = new ChunkOres(new long[0], new int[0]);

        final long[] positions;
        final int[] stateIds;

        ChunkOres(long[] positions, int[] stateIds) {
            this.positions = positions;
            this.stateIds = stateIds;
        }

        int size() {
            return positions.length;
        }
    }

    public static Status status() {
        if (failed) return Status.ERROR;
        if (unsupportedDimension) return Status.UNSUPPORTED_DIMENSION;
        if (unverifiedWorldgen) return Status.UNVERIFIED_WORLDGEN;
        if (activeKey == null) return Status.IDLE;
        if (contextLoading || activeContext == null) return Status.LOADING_CONTEXT;
        if (generationInFlight) return Status.GENERATING;
        return Status.READY;
    }

    public static boolean ready() {
        return activeContext != null && !failed && !unsupportedDimension && !unverifiedWorldgen;
    }

    public static boolean loading() {
        return contextLoading || generationInFlight;
    }

    public static boolean failed() {
        return failed;
    }

    public static String failureMessage() {
        return failureMessage;
    }

    public static int chunkCount() {
        return CHUNKS.size();
    }

    public static TargetProgress targetProgress() {
        synchronized (STATE_LOCK) {
            if (activeKey == null) return new TargetProgress(epoch, 0, 0);
            int centerX = SectionPos.blockToSectionCoord(playerBlockX);
            int centerZ = SectionPos.blockToSectionCoord(playerBlockZ);
            int radius = Math.max(1, Math.min(targetRadius, MAX_SIM_RADIUS));
            int diameter = radius * 2 + 1;
            int complete = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long key = ChunkPos.pack(centerX + dx, centerZ + dz);
                    if (CHUNKS.containsKey(key) || PENDING_CHUNKS.containsKey(key)) complete++;
                }
            }
            return new TargetProgress(epoch, complete, diameter * diameter);
        }
    }

    public static int storedPositions() {
        return totalPositions;
    }

    public static boolean capped() {
        return positionCapHit;
    }

    public static int revision() {
        return REVISION.get();
    }

    public static void clear() {
        suspendedAtNanos = 0L;
        synchronized (STATE_LOCK) {
            if (activeKey == null && CHUNKS.isEmpty() && PENDING_CHUNKS.isEmpty()
                && !contextLoading && !generationInFlight && !tagVerificationInFlight
                && !failed && !unsupportedDimension
                && !unverifiedWorldgen && failureMessage.isEmpty() && DISPROVEN.isEmpty()
                && WORKER.isIdle()) return;
            epoch++;
            discardQueuedWork();
            activeKey = null;
            activeContext = null;
            contextLoading = false;
            generationInFlight = false;
            tagVerificationInFlight = false;
            initialChunkGenerated = false;
            failed = false;
            unsupportedDimension = false;
            unverifiedWorldgen = false;
            failureMessage = "";
            positionCapHit = false;
            nextTagVerificationNanos = 0L;
            totalPositions = 0;
            CHUNKS.clear();
            PENDING_CHUNKS.clear();
            DISPROVEN.clear();
            REVISION.incrementAndGet();
        }
    }

    public static void suspend() {
        if (suspendedAtNanos == 0L) suspendedAtNanos = System.nanoTime();
    }

    public static void resume() {
        suspendedAtNanos = 0L;
    }

    public static boolean isSuspended() {
        return suspendedAtNanos != 0L;
    }

    public static boolean expireSuspended(long retentionNanos) {
        long since = suspendedAtNanos;
        if (since == 0L || System.nanoTime() - since < retentionNanos) return false;
        suspendedAtNanos = 0L;
        clear();
        return true;
    }

    public static void forgetDisproven() {
        if (!DISPROVEN.isEmpty()) {
            DISPROVEN.clear();
            REVISION.incrementAndGet();
        }
    }

    public static void tick(ClientLevel level, BlockPos playerPos, Long worldSeed, int radius, int enabledMask) {
        if (level == null || playerPos == null || worldSeed == null) {
            clear();
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        if (!isSupportedDimension(dimension)) {
            synchronized (STATE_LOCK) {
                if (!unsupportedDimension || activeKey != null || !CHUNKS.isEmpty() || !PENDING_CHUNKS.isEmpty()
                    || contextLoading || generationInFlight || unverifiedWorldgen) {
                    epoch++;
                    discardQueuedWork();
                    activeKey = null;
                    activeContext = null;
                    contextLoading = false;
                    generationInFlight = false;
                    tagVerificationInFlight = false;
                    initialChunkGenerated = false;
                    CHUNKS.clear();
                    PENDING_CHUNKS.clear();
                    DISPROVEN.clear();
                    totalPositions = 0;
                    REVISION.incrementAndGet();
                }
                failed = false;
                unsupportedDimension = true;
                unverifiedWorldgen = false;
                failureMessage = "Unsupported dimension " + dimension.identifier();
            }
            return;
        }

        ContextKey wanted = new ContextKey(worldSeed, dimension);
        synchronized (STATE_LOCK) {
            if (!wanted.equals(activeKey)) activate(wanted);
            playerBlockX = playerPos.getX();
            playerBlockZ = playerPos.getZ();
            targetRadius = Math.max(1, Math.min(radius, MAX_SIM_RADIUS));
            pruneOutOfRange();

            publishReadyPending();

            if (activeContext != null && !generationInFlight && !tagVerificationInFlight
                && System.nanoTime() >= nextTagVerificationNanos) {
                startTagVerification(activeContext, epoch);
                return;
            }
            if (unverifiedWorldgen || tagVerificationInFlight) return;

            if (enabledMask == 0 || failed || positionCapHit || activeContext == null || generationInFlight) {
                return;
            }
            if (!initialChunkGenerated) {
                int centerChunkX = SectionPos.blockToSectionCoord(playerBlockX);
                int centerChunkZ = SectionPos.blockToSectionCoord(playerBlockZ);
                long centerKey = ChunkPos.pack(centerChunkX, centerChunkZ);
                if (CHUNKS.containsKey(centerKey) || PENDING_CHUNKS.containsKey(centerKey)) {
                    initialChunkGenerated = true;
                } else {
                    startInitialChunkGeneration(centerChunkX, centerChunkZ, activeContext, epoch);
                    return;
                }
            }
            Tile tile = nearestMissingTile(playerBlockX, playerBlockZ, targetRadius);
            if (tile != null) startTileGeneration(tile, activeContext, epoch);
        }
    }

    private static boolean isSupportedDimension(ResourceKey<Level> dimension) {
        return Level.OVERWORLD.equals(dimension) || Level.NETHER.equals(dimension) || Level.END.equals(dimension);
    }

    private static void activate(ContextKey wanted) {
        epoch++;
        discardQueuedWork();
        activeKey = wanted;
        activeContext = CONTEXT_CACHE.get(wanted);
        contextLoading = activeContext == null;
        generationInFlight = false;
        tagVerificationInFlight = false;
        initialChunkGenerated = false;
        failed = false;
        unsupportedDimension = false;
        unverifiedWorldgen = false;
        failureMessage = "";
        positionCapHit = false;
        nextTagVerificationNanos = 0L;
        totalPositions = 0;
        DISPROVEN.clear();
        if (!CHUNKS.isEmpty()) CHUNKS.clear();
        PENDING_CHUNKS.clear();
        REVISION.incrementAndGet();
        if (activeContext == null) startContextLoad(wanted, epoch);
    }

    private static void startContextLoad(ContextKey key, long token) {
        WORKER.submit(() -> {
            AutismWorldgenContext built;
            boolean tagsVerified;
            try {
                built = AutismWorldgenContext.create(key.seed(), key.dimension());
                tagsVerified = built.vanillaBlockTagsVerified();
            } catch (Throwable error) {
                fail(token, "Minecraft 26.2 worldgen bootstrap failed", error);
                return;
            }
            synchronized (STATE_LOCK) {
                if (token != epoch || !key.equals(activeKey)) return;
                CONTEXT_CACHE.put(key, built);
                activeContext = built;
                contextLoading = false;
                nextTagVerificationNanos = System.nanoTime() + TAG_VERIFY_INTERVAL_NANOS;
                if (!tagsVerified) {
                    markUnverifiedLocked("Minecraft block tags differ from the vanilla 26.2 worldgen profile");
                }
            }
        });
    }

    private static void startInitialChunkGeneration(int chunkX, int chunkZ,
                                                    AutismWorldgenContext context, long token) {
        generationInFlight = true;
        WORKER.submit(() -> {
            try {
                if (!context.vanillaBlockTagsVerified()) {
                    markUnverified(token, "Minecraft block tags changed before initial chunk generation");
                    return;
                }
                int regionMinX = chunkX - AutismRegionGenerator.MARGIN;
                int regionMinZ = chunkZ - AutismRegionGenerator.MARGIN;
                Map<Long, ChunkAccess> generated = new AutismRegionGenerator(context)
                    .generate(regionMinX, regionMinZ, INITIAL_REGION_SIZE, () -> token != epoch);
                long chunkKey = ChunkPos.pack(chunkX, chunkZ);
                ChunkAccess chunk = generated.get(chunkKey);
                if (chunk == null) {
                    throw new IllegalStateException("Generator omitted initial complete chunk " + chunkX + ", " + chunkZ);
                }
                ChunkOres ores = extract(chunk, () -> token != epoch);

                if (!context.vanillaBlockTagsVerified()) {
                    markUnverified(token, "Minecraft block tags changed during initial chunk generation");
                    return;
                }

                synchronized (STATE_LOCK) {
                    if (token != epoch || context != activeContext) return;
                    if (!CHUNKS.containsKey(chunkKey) && !PENDING_CHUNKS.containsKey(chunkKey)) {
                        if (totalPositions + ores.size() > MAX_POSITIONS) {
                            positionCapHit = true;
                            return;
                        }
                        CHUNKS.put(chunkKey, ores);
                        totalPositions += ores.size();
                        REVISION.incrementAndGet();
                    }
                    nextTagVerificationNanos = System.nanoTime() + TAG_VERIFY_INTERVAL_NANOS;
                    initialChunkGenerated = true;
                }
            } catch (CancellationException ignored) {

            } catch (Throwable error) {
                fail(token, "Minecraft 26.2 initial chunk generation failed at " + chunkX + ", " + chunkZ,
                    error);
            } finally {
                synchronized (STATE_LOCK) {
                    if (token == epoch) generationInFlight = false;
                }
            }
        });
    }

    private static void startTileGeneration(Tile tile, AutismWorldgenContext context, long token) {
        generationInFlight = true;
        LongOpenHashSet alreadyComplete = new LongOpenHashSet(TILE_SIZE * TILE_SIZE);
        for (int dx = 0; dx < TILE_SIZE; dx++) {
            for (int dz = 0; dz < TILE_SIZE; dz++) {
                long key = ChunkPos.pack(tile.minChunkX() + dx, tile.minChunkZ() + dz);
                if (CHUNKS.containsKey(key) || PENDING_CHUNKS.containsKey(key)) alreadyComplete.add(key);
            }
        }
        WORKER.submit(() -> {
            try {
                if (!context.vanillaBlockTagsVerified()) {
                    markUnverified(token, "Minecraft block tags changed before region generation");
                    return;
                }
                int regionMinX = tile.minChunkX() - AutismRegionGenerator.MARGIN;
                int regionMinZ = tile.minChunkZ() - AutismRegionGenerator.MARGIN;
                Map<Long, ChunkAccess> generated = new AutismRegionGenerator(context)
                    .generate(regionMinX, regionMinZ, REGION_SIZE, () -> token != epoch);
                Map<Long, ChunkOres> completed = new HashMap<>(TILE_SIZE * TILE_SIZE);
                for (int dx = 0; dx < TILE_SIZE; dx++) {
                    for (int dz = 0; dz < TILE_SIZE; dz++) {
                        int chunkX = tile.minChunkX() + dx;
                        int chunkZ = tile.minChunkZ() + dz;
                        long chunkKey = ChunkPos.pack(chunkX, chunkZ);

                        if (alreadyComplete.contains(chunkKey)) continue;
                        ChunkAccess chunk = generated.get(chunkKey);
                        if (chunk == null) throw new IllegalStateException("Generator omitted complete chunk " + chunkX + ", " + chunkZ);
                        ChunkOres ores = extract(chunk, () -> token != epoch);
                        completed.put(chunkKey, ores);
                    }
                }

                if (!context.vanillaBlockTagsVerified()) {
                    markUnverified(token, "Minecraft block tags changed during region generation");
                    return;
                }

                synchronized (STATE_LOCK) {
                    if (token != epoch || context != activeContext) return;
                    int added = 0;
                    for (Map.Entry<Long, ChunkOres> entry : completed.entrySet()) {
                        if (!CHUNKS.containsKey(entry.getKey()) && !PENDING_CHUNKS.containsKey(entry.getKey())) {
                            added += entry.getValue().size();
                        }
                    }
                    if (totalPositions + added > MAX_POSITIONS) {
                        positionCapHit = true;
                        return;
                    }
                    for (Map.Entry<Long, ChunkOres> entry : completed.entrySet()) {
                        if (CHUNKS.containsKey(entry.getKey()) || PENDING_CHUNKS.containsKey(entry.getKey())) continue;
                        PENDING_CHUNKS.put(entry.getKey(), entry.getValue());
                        totalPositions += entry.getValue().size();
                    }
                    nextTagVerificationNanos = System.nanoTime() + TAG_VERIFY_INTERVAL_NANOS;
                }
            } catch (CancellationException ignored) {

            } catch (Throwable error) {
                fail(token, "Minecraft 26.2 region generation failed at tile "
                    + tile.minChunkX() + ", " + tile.minChunkZ(), error);
            } finally {
                synchronized (STATE_LOCK) {
                    if (token == epoch) generationInFlight = false;
                }
            }
        });
    }

    private static ChunkOres extract(ChunkAccess chunk, java.util.function.BooleanSupplier cancelled) {
        LongArrayList positions = new LongArrayList();
        IntArrayList stateIds = new IntArrayList();
        AutismRegionGenerator.forEachBlock(
            chunk,
            state -> AutismOreSimOre.isOreSimBlock(state.getBlock()),
            (x, y, z, state) -> {
                int stateId = AutismOreSimOre.OreStates.internGenerated(state);
                if (stateId < 0) return;
                positions.add(BlockPos.asLong(x, y, z));
                stateIds.add(stateId);
            }, cancelled);
        return positions.isEmpty()
            ? ChunkOres.EMPTY
            : new ChunkOres(positions.toLongArray(), stateIds.toIntArray());
    }

    private static void fail(long token, String message, Throwable error) {
        boolean accepted = false;
        synchronized (STATE_LOCK) {
            if (token != epoch) return;
            failed = true;
            contextLoading = false;
            generationInFlight = false;
            tagVerificationInFlight = false;
            initialChunkGenerated = false;
            failureMessage = message + ": " + error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : " - " + error.getMessage());
            activeContext = null;
            CHUNKS.clear();
            PENDING_CHUNKS.clear();
            totalPositions = 0;
            unverifiedWorldgen = false;
            REVISION.incrementAndGet();
            accepted = true;
        }
        if (accepted) autismclient.AutismClientAddon.LOG.warn(message, error);
    }

    private static void markUnverified(long token, String message) {
        synchronized (STATE_LOCK) {
            if (token != epoch) return;
            markUnverifiedLocked(message);
        }
    }

    private static void markUnverifiedLocked(String message) {
        epoch++;
        discardQueuedWork();
        contextLoading = false;
        generationInFlight = false;
        tagVerificationInFlight = false;
        initialChunkGenerated = false;
        failed = false;
        unsupportedDimension = false;
        unverifiedWorldgen = true;
        failureMessage = message;
        positionCapHit = false;
        nextTagVerificationNanos = System.nanoTime() + TAG_VERIFY_INTERVAL_NANOS;
        totalPositions = 0;
        CHUNKS.clear();
        PENDING_CHUNKS.clear();
        DISPROVEN.clear();
        REVISION.incrementAndGet();
    }

    private static void discardQueuedWork() {
        WORKER.cancelAll();
    }

    private static void startTagVerification(AutismWorldgenContext context, long token) {
        tagVerificationInFlight = true;
        WORKER.submit(() -> {
            boolean verified = false;
            Throwable failure = null;
            try {
                verified = context.vanillaBlockTagsVerified();
            } catch (Throwable error) {
                failure = error;
            }

            if (failure != null) {
                autismclient.AutismClientAddon.LOG.warn("OreSim could not verify Minecraft block tags", failure);
            }
            synchronized (STATE_LOCK) {
                if (token != epoch || context != activeContext) return;
                tagVerificationInFlight = false;
                nextTagVerificationNanos = System.nanoTime() + TAG_VERIFY_INTERVAL_NANOS;
                if (!verified) {
                    markUnverifiedLocked(failure == null
                        ? "Minecraft block tags differ from the vanilla 26.2 worldgen profile"
                        : "Minecraft block tags could not be verified");
                    return;
                }
                if (unverifiedWorldgen) {

                    epoch++;
                    unverifiedWorldgen = false;
                    failureMessage = "";
                    initialChunkGenerated = false;
                    REVISION.incrementAndGet();
                }
            }
        });
    }

    private static Tile nearestMissingTile(int blockX, int blockZ, int radius) {
        int centerChunkX = SectionPos.blockToSectionCoord(blockX);
        int centerChunkZ = SectionPos.blockToSectionCoord(blockZ);
        int minTileX = Math.floorDiv(centerChunkX - radius, TILE_SIZE);
        int maxTileX = Math.floorDiv(centerChunkX + radius, TILE_SIZE);
        int minTileZ = Math.floorDiv(centerChunkZ - radius, TILE_SIZE);
        int maxTileZ = Math.floorDiv(centerChunkZ + radius, TILE_SIZE);
        Tile best = null;
        for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
            for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                int minChunkX = tileX * TILE_SIZE;
                int minChunkZ = tileZ * TILE_SIZE;
                if (tileComplete(minChunkX, minChunkZ)) continue;
                double distance = distanceToTileSq(blockX + 0.5, blockZ + 0.5, minChunkX, minChunkZ);
                if (best == null || distance < best.distanceSq()
                    || (distance == best.distanceSq() && tileOrderBefore(minChunkX, minChunkZ, best))) {
                    best = new Tile(minChunkX, minChunkZ, distance);
                }
            }
        }
        return best;
    }

    private static boolean tileComplete(int minChunkX, int minChunkZ) {
        for (int dx = 0; dx < TILE_SIZE; dx++) {
            for (int dz = 0; dz < TILE_SIZE; dz++) {
                long key = ChunkPos.pack(minChunkX + dx, minChunkZ + dz);
                if (!CHUNKS.containsKey(key) && !PENDING_CHUNKS.containsKey(key)) return false;
            }
        }
        return true;
    }

    private static double distanceToTileSq(double x, double z, int minChunkX, int minChunkZ) {
        double minX = minChunkX * 16.0 + 0.5;
        double minZ = minChunkZ * 16.0 + 0.5;
        double maxX = (minChunkX + TILE_SIZE) * 16.0 - 0.5;
        double maxZ = (minChunkZ + TILE_SIZE) * 16.0 - 0.5;
        double dx = x < minX ? minX - x : x > maxX ? x - maxX : 0.0;
        double dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0.0;
        return dx * dx + dz * dz;
    }

    private static boolean tileOrderBefore(int x, int z, Tile other) {
        return x < other.minChunkX() || x == other.minChunkX() && z < other.minChunkZ();
    }

    private static void publishReadyPending() {
        if (PENDING_CHUNKS.isEmpty()) return;
        NearestChunkFrontier.Chunk missing = nearestMissingChunk();
        boolean changed = false;
        Iterator<Map.Entry<Long, ChunkOres>> iterator = PENDING_CHUNKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, ChunkOres> entry = iterator.next();
            int chunkX = ChunkPos.getX(entry.getKey());
            int chunkZ = ChunkPos.getZ(entry.getKey());
            if (!NearestChunkFrontier.canPublish(
                playerBlockX + 0.5, playerBlockZ + 0.5, chunkX, chunkZ, missing)) continue;
            CHUNKS.put(entry.getKey(), entry.getValue());
            iterator.remove();
            changed = true;
        }
        if (changed) REVISION.incrementAndGet();
    }

    private static NearestChunkFrontier.Chunk nearestMissingChunk() {
        int centerX = SectionPos.blockToSectionCoord(playerBlockX);
        int centerZ = SectionPos.blockToSectionCoord(playerBlockZ);
        return NearestChunkFrontier.nearestMissing(
            playerBlockX + 0.5,
            playerBlockZ + 0.5,
            centerX,
            centerZ,
            targetRadius,
            key -> CHUNKS.containsKey(key) || PENDING_CHUNKS.containsKey(key));
    }

    private static void pruneOutOfRange() {
        int centerX = SectionPos.blockToSectionCoord(playerBlockX);
        int centerZ = SectionPos.blockToSectionCoord(playerBlockZ);
        int keep = targetRadius + TILE_SIZE;
        int removed = 0;
        for (Map.Entry<Long, ChunkOres> entry : CHUNKS.entrySet()) {
            long key = entry.getKey();
            if (Math.abs(ChunkPos.getX(key) - centerX) <= keep
                && Math.abs(ChunkPos.getZ(key) - centerZ) <= keep) continue;
            if (CHUNKS.remove(key, entry.getValue())) removed += entry.getValue().size();
        }
        Iterator<Map.Entry<Long, ChunkOres>> pending = PENDING_CHUNKS.entrySet().iterator();
        while (pending.hasNext()) {
            Map.Entry<Long, ChunkOres> entry = pending.next();
            long key = entry.getKey();
            if (Math.abs(ChunkPos.getX(key) - centerX) <= keep
                && Math.abs(ChunkPos.getZ(key) - centerZ) <= keep) continue;
            removed += entry.getValue().size();
            pending.remove();
        }
        if (removed > 0) {
            totalPositions = Math.max(0, totalPositions - removed);
            positionCapHit = false;
            REVISION.incrementAndGet();
        }
    }

    public static boolean selectionTruncated() {
        return selectionTruncated;
    }

    public static int selectNearest(double eyeX, double eyeY, double eyeZ, int radius,
                                    IntPredicate stateAllowed, int cap, long[] outPos, int[] outState) {
        selectionTruncated = false;
        if (cap <= 0 || outPos == null || outState == null || CHUNKS.isEmpty()) return 0;
        int limit = Math.min(cap, Math.min(outPos.length, outState.length));
        if (limit <= 0) return 0;

        NearestPositionSelector nearest = new NearestPositionSelector(limit);
        int centerX = SectionPos.blockToSectionCoord((int) Math.floor(eyeX));
        int centerZ = SectionPos.blockToSectionCoord((int) Math.floor(eyeZ));
        int drawRadius = Math.max(1, Math.min(radius, MAX_SIM_RADIUS));

        boolean anyDisproven = !DISPROVEN.isEmpty();

        for (int ring = 0; ring <= drawRadius; ring++) {
            if (nearest.isFull()) {
                double ringMin = Math.max(0.0, (ring - 1) * 16.0);
                if (ringMin * ringMin > nearest.farthestDistanceSquared()) break;
            }
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    ChunkOres ores = CHUNKS.get(ChunkPos.pack(centerX + dx, centerZ + dz));
                    if (ores == null || ores.positions.length == 0) continue;
                    for (int i = 0; i < ores.positions.length; i++) {
                        int stateId = ores.stateIds[i];
                        if (stateAllowed != null && !stateAllowed.test(stateId)) continue;
                        long packed = ores.positions[i];
                        if (anyDisproven && DISPROVEN.contains(packed)) continue;
                        double px = BlockPos.getX(packed) + 0.5 - eyeX;
                        double py = BlockPos.getY(packed) + 0.5 - eyeY;
                        double pz = BlockPos.getZ(packed) + 0.5 - eyeZ;
                        double distance = px * px + py * py + pz * pz;
                        nearest.offer(distance, packed, stateId);
                    }
                }
            }
        }

        selectionTruncated = nearest.truncated();
        return nearest.writeNearestFirst(outPos, outState);
    }

    public static void collectNear(BlockPos center, int blockRadius, LongOpenHashSet out) {
        if (center == null || out == null || CHUNKS.isEmpty()) return;
        int minX = center.getX() - blockRadius;
        int maxX = center.getX() + blockRadius;
        int minY = center.getY() - blockRadius;
        int maxY = center.getY() + blockRadius;
        int minZ = center.getZ() - blockRadius;
        int maxZ = center.getZ() + blockRadius;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                ChunkOres ores = CHUNKS.get(ChunkPos.pack(chunkX, chunkZ));
                if (ores == null) continue;
                for (long packed : ores.positions) {
                    if (DISPROVEN.contains(packed)) continue;
                    int x = BlockPos.getX(packed);
                    int y = BlockPos.getY(packed);
                    int z = BlockPos.getZ(packed);
                    if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                        out.add(packed);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface PositionJudge {
        boolean shouldDrop(int stateId, long packedPos);
    }

    public static void verifyNear(BlockPos center, int blockRadius, PositionJudge judge) {
        if (center == null || judge == null || CHUNKS.isEmpty()) return;
        int minX = center.getX() - blockRadius;
        int maxX = center.getX() + blockRadius;
        int minY = center.getY() - blockRadius;
        int maxY = center.getY() + blockRadius;
        int minZ = center.getZ() - blockRadius;
        int maxZ = center.getZ() + blockRadius;
        boolean changed = false;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                ChunkOres ores = CHUNKS.get(ChunkPos.pack(chunkX, chunkZ));
                if (ores == null) continue;
                for (int i = 0; i < ores.positions.length; i++) {
                    long packed = ores.positions[i];
                    if (DISPROVEN.contains(packed)) continue;
                    int x = BlockPos.getX(packed);
                    int y = BlockPos.getY(packed);
                    int z = BlockPos.getZ(packed);
                    if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) continue;
                    if (judge.shouldDrop(ores.stateIds[i], packed) && DISPROVEN.size() < MAX_DISPROVEN) {
                        changed |= DISPROVEN.add(packed);
                    }
                }
            }
        }
        if (changed) REVISION.incrementAndGet();
    }

    public static int accuracyPercent() {
        return -1;
    }
}
