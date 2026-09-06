package autismclient.modules;

import autismclient.util.oresim.AutismOreSimEngine;
import autismclient.util.oresim.AutismOreSimOre;
import autismclient.util.oresim.AutismOreSimOre.Kind;
import autismclient.util.oresim.AutismOreSimOre.OreStates;
import autismclient.util.oresim.AutismOreSimSeedInput;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;

public final class ModuleOreSim {

    static final int NEAR_RANGE = 5;

    public static final int DRAW_CAP = 8192;

    private static final int PER_CHUNK_CAP = 2048;

    private static final int NEAR_CAP = 64;

    private static final int VERIFY_INTERVAL = 4;
    private static final int VERIFY_RAY_BUDGET = 96;

    private static final long SEED_SETTLE_NANOS = 400_000_000L;

    private static final int EXAMINED_RESET_SWEEPS = 25;
    private static int verifyTimer;
    private static int examinedSweeps;
    private static boolean seedObserved;
    private static Long pendingSeed;
    private static long seedReadyAtNanos;
    private static final LongOpenHashSet examinedRecently = new LongOpenHashSet();

    private static final long[] boxPositions = new long[DRAW_CAP];
    private static final int[] boxStates = new int[DRAW_CAP];

    private static final double[] realHeapDist = new double[DRAW_CAP];
    private static final ModuleEspChunkCache.Entry[] realHeapEntry = new ModuleEspChunkCache.Entry[DRAW_CAP];

    static final int OTHER_COLOR = 0xFFCCCCCC;

    private static final Selection EMPTY_SELECTION = new Selection("", Set.of(), List.of(), 0);
    private static volatile Selection cachedNormal = EMPTY_SELECTION;
    private static volatile Selection cachedOreSim = EMPTY_SELECTION;

    private ModuleOreSim() {
    }

    private static int cachedModeRevision = Integer.MIN_VALUE;
    private static boolean cachedOreSimMode;
    private static boolean cachedEspStyle;

    private static void refreshMode(Module xray) {
        int revision = ModuleRegistry.revision();
        if (revision == cachedModeRevision) return;
        cachedModeRevision = revision;
        cachedOreSimMode = xray != null && "OreSim".equals(xray.value("mode"));
        cachedEspStyle = xray != null && "ESP".equals(xray.value("render-style"));
    }

    static boolean oreSimMode(Module xray) {
        if (xray == null) return false;
        refreshMode(xray);
        return cachedOreSimMode;
    }

    static boolean espStyle(Module xray) {
        if (xray == null) return false;
        refreshMode(xray);
        return cachedEspStyle;
    }

    static boolean drawsBoxes(Module xray) {
        return xray != null && xray.isEnabled() && espStyle(xray);
    }

    static boolean drawsGhosts(Module xray) {
        return xray != null && xray.isEnabled() && oreSimMode(xray) && !espStyle(xray);
    }

    static boolean tintActive(Module xray) {
        return xray != null && xray.isEnabled() && !espStyle(xray);
    }

    static float fillAlpha(Module xray) {
        return 0.30f;
    }

    private record Selection(String value, Set<Block> blocks, List<String> nonOreIds, int mask) {
    }

    private static Selection selection(Module xray) {
        if (xray == null) return EMPTY_SELECTION;
        if (oreSimMode(xray)) {
            Selection next = parse(xray.value("oresim-ores"), cachedOreSim);
            cachedOreSim = next;
            return next;
        }
        Selection next = parse(xray.value("whitelist"), cachedNormal);
        cachedNormal = next;
        return next;
    }

    private static Selection parse(String raw, Selection cached) {
        String safe = raw == null ? "" : raw;
        if (cached.value().equals(safe)) return cached;
        Set<Block> blocks = new LinkedHashSet<>();
        List<String> nonOre = new ArrayList<>();
        int mask = 0;
        for (String token : safe.split("\\|")) {
            String id = token.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) continue;
            if (!id.contains(":")) id = "minecraft:" + id;
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null) continue;
            Block block = BuiltInRegistries.BLOCK.getOptional(parsed).orElse(Blocks.AIR);
            if (block == Blocks.AIR) continue;
            blocks.add(block);
            Kind family = AutismOreSimOre.familyOf(id);
            if (family != null) mask |= 1 << family.ordinal();
            else if (!nonOre.contains(id)) nonOre.add(id);
        }
        return new Selection(safe, Set.copyOf(blocks), List.copyOf(nonOre), mask);
    }

    static int enabledMask(Module xray) {
        return selection(xray).mask();
    }

    static boolean whitelistHasFamily(Module xray, Kind kind) {
        return kind != null && (selection(xray).mask() & (1 << kind.ordinal())) != 0;
    }

    static List<String> nonOreWhitelistIds(Module xray) {
        if (xray == null) return List.of();
        Selection next = parse(xray.value("whitelist"), cachedNormal);
        cachedNormal = next;
        return next.nonOreIds();
    }

    static boolean whitelistHasId(Module xray, String id) {
        return nonOreWhitelistIds(xray).contains(id);
    }

    static int colorFor(Module xray, Kind kind) {
        return ModuleRenderUtil.color(xray, kind.colorId(), kind.defaultColor);
    }

    static int colorForBlock(Module xray, Block block) {
        if (block == null) return OTHER_COLOR;
        int revision = ModuleRegistry.revision();
        if (revision != blockColorRevision) {
            blockColorRevision = revision;
            blockColors.clear();
        }
        int cached = blockColors.getOrDefault(block, Integer.MIN_VALUE);
        if (cached != Integer.MIN_VALUE) return cached;
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        Kind family = id == null ? null : AutismOreSimOre.familyOf(id.toString());
        int color = family != null ? colorFor(xray, family)
            : id == null ? OTHER_COLOR : ModuleRenderUtil.color(xray, "block-color-" + id, OTHER_COLOR);
        blockColors.put(block, color);
        return color;
    }

    private static int blockColorRevision = Integer.MIN_VALUE;
    private static final Object2IntOpenHashMap<Block> blockColors = new Object2IntOpenHashMap<>();

    private static volatile AutismOreSimSeedInput.Result lastSeedInput = AutismOreSimSeedInput.parse(null);

    static Long seed(Module xray) {
        AutismOreSimSeedInput.Result parsed = AutismOreSimSeedInput.parse(
            xray == null ? null : xray.value("oresim-seed"));
        lastSeedInput = parsed;
        return parsed.value();
    }

    public static AutismOreSimSeedInput.Status seedInputStatus(Module xray) {
        seed(xray);
        return lastSeedInput.status();
    }

    public static Long debugSeed(Module xray) {
        return seed(xray);
    }

    public static int debugSelectionSize(Module xray) {
        return selection(xray).blocks().size();
    }

    public static int debugEnabledMask(Module xray) {
        return enabledMask(xray);
    }

    static int simulationRadius(Module xray) {
        int configured = xray == null ? 3 : xray.integer("oresim-radius");
        return Math.max(1, Math.min(configured, AutismOreSimEngine.MAX_SIM_RADIUS));
    }

    static void tick(Module xray, ClientLevel level, Player player) {
        if (!oreSimMode(xray) || xray == null || !xray.isEnabled() || level == null || player == null) {

            AutismOreSimEngine.suspend();
            return;
        }
        AutismOreSimEngine.resume();
        Long parsedSeed = seed(xray);
        int mask = enabledMask(xray);
        long now = System.nanoTime();
        if (!seedObserved || !Objects.equals(parsedSeed, pendingSeed)) {
            seedObserved = true;
            pendingSeed = parsedSeed;
            seedReadyAtNanos = now + SEED_SETTLE_NANOS;
            AutismOreSimEngine.clear();
            return;
        }
        if (parsedSeed == null || mask == 0 || now < seedReadyAtNanos) {
            AutismOreSimEngine.clear();
            return;
        }
        AutismOreSimEngine.tick(level, player.blockPosition(), parsedSeed,
            simulationRadius(xray), mask);
        if (++verifyTimer >= VERIFY_INTERVAL) {
            verifyTimer = 0;
            retractDisproven(xray, level, player);
        }
    }

    private static final long RETENTION_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(2);

    public static void tickRetention() {
        if (AutismOreSimEngine.expireSuspended(RETENTION_NANOS)) {
            seedObserved = false;
            pendingSeed = null;
            seedReadyAtNanos = 0L;
        }
    }

    private static void retractDisproven(Module xray, ClientLevel level, Player player) {
        Selection selection = selection(xray);
        if (selection.blocks().isEmpty()) return;
        Vec3 eyes = player.getEyePosition();
        double maxDistSq = (double) NEAR_RANGE * NEAR_RANGE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] rays = {0};

        AutismOreSimEngine.verifyNear(player.blockPosition(), NEAR_RANGE, (stateId, packed) -> {
            BlockState predicted = OreStates.state(stateId);
            if (predicted == null) return false;
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            double cx = x + 0.5 - eyes.x;
            double cy = y + 0.5 - eyes.y;
            double cz = z + 0.5 - eyes.z;
            if (cx * cx + cy * cy + cz * cz > maxDistSq) return false;
            cursor.set(x, y, z);
            BlockState actual = level.getBlockState(cursor);
            if (actual.getBlock() == predicted.getBlock()) return false;

            boolean open = actual.isAir() || touchesAir(level, cursor);
            if (!open) return false;

            if (!examinedRecently.add(packed)) return false;
            if (rays[0] >= VERIFY_RAY_BUDGET) return false;
            return canSee(level, player, eyes, cursor.immutable(), actual.isAir(), rays);
        });

        if (++examinedSweeps >= EXAMINED_RESET_SWEEPS || examinedRecently.size() > 4096) {
            examinedSweeps = 0;
            examinedRecently.clear();
        }
    }

    static long contentKey(Module xray, ClientLevel level) {
        if (oreSimMode(xray)) return ((long) AutismOreSimEngine.revision() << 1) | 1L;
        return (long) ModuleEspChunkCache.generation() << 1;
    }

    private static boolean stateAllowed(Selection selection, int stateId) {
        BlockState state = OreStates.state(stateId);
        return state != null && selection.blocks().contains(state.getBlock());
    }

    private static Selection allowedStatesSource;
    private static boolean[] allowedStates = new boolean[0];

    private static boolean[] allowedStates(Selection selection) {
        int count = OreStates.count();
        boolean[] table = allowedStates;
        if (selection == allowedStatesSource && table.length == count) return table;
        table = new boolean[count];
        for (int id = 0; id < count; id++) {
            table[id] = stateAllowed(selection, id);
        }
        allowedStatesSource = selection;
        allowedStates = table;
        return table;
    }

    private static IntPredicate allowedStatePredicate(Selection selection) {
        boolean[] table = allowedStates(selection);
        return stateId -> stateId >= 0 && stateId < table.length && table[stateId];
    }

    static int collectGhosts(Module xray, Player player, long[] outPos, int[] outState) {
        if (xray == null || !xray.isEnabled() || player == null || !oreSimMode(xray)) return 0;
        Selection selection = selection(xray);
        if (selection.blocks().isEmpty()) return 0;
        Vec3 eyes = player.getEyePosition();
        return AutismOreSimEngine.selectNearest(eyes.x, eyes.y, eyes.z,
            simulationRadius(xray),
            allowedStatePredicate(selection), DRAW_CAP, outPos, outState);
    }

    static void collect(Module xray, ClientLevel level, Player player, BiConsumer<AABB, Integer> emit) {
        if (emit == null || xray == null || !xray.isEnabled() || level == null || player == null) return;
        if (oreSimMode(xray)) collectSimulated(xray, player, emit);
        else collectRealBlocks(xray, level, player, emit);
    }

    private static void collectSimulated(Module xray, Player player, BiConsumer<AABB, Integer> emit) {
        Selection selection = selection(xray);
        if (selection.blocks().isEmpty()) return;
        Vec3 eyes = player.getEyePosition();
        int count = AutismOreSimEngine.selectNearest(eyes.x, eyes.y, eyes.z,
            simulationRadius(xray),
            allowedStatePredicate(selection), DRAW_CAP, boxPositions, boxStates);

        int[] colors = new int[Kind.values().length];
        for (Kind kind : Kind.values()) colors[kind.ordinal()] = colorFor(xray, kind);
        for (int i = 0; i < count; i++) {
            Kind kind = OreStates.kind(boxStates[i]);
            emit.accept(boxAt(boxPositions[i]), kind == null ? OTHER_COLOR : colors[kind.ordinal()]);
        }
    }

    private static void collectRealBlocks(Module xray, ClientLevel level, Player player, BiConsumer<AABB, Integer> emit) {
        Selection selection = selection(xray);
        if (selection.blocks().isEmpty()) return;
        ModuleEspChunkCache.onLevel(level);

        Object2IntMap<Block> colors = new Object2IntOpenHashMap<>();
        colors.defaultReturnValue(OTHER_COLOR);
        int colorsHash = 0;
        for (Block block : selection.blocks()) {
            int color = colorForBlock(xray, block);
            colors.put(block, color);
            colorsHash = colorsHash * 31 + color;
        }

        int chunkRadius = ModuleRenderUtil.effectiveRenderChunkRadius();
        int playerBlockY = player.getBlockY();

        String stamp = selection.value() + '|' + colorsHash + "|b" + (playerBlockY >> 4);
        long gameTime = level.getGameTime();
        ClientChunkCache chunks = level.getChunkSource();
        int playerChunkX = player.chunkPosition().x();
        int playerChunkZ = player.chunkPosition().z();
        Vec3 eyes = player.getEyePosition();

        double[] heapDist = realHeapDist;
        ModuleEspChunkCache.Entry[] heapEntry = realHeapEntry;
        int size = 0;

        for (int ring = 0; ring <= chunkRadius; ring++) {
            if (size == DRAW_CAP) {
                double ringMin = (ring - 1) * 16.0;
                if (ringMin > 0 && ringMin * ringMin > heapDist[0]) break;
            }
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    LevelChunk chunk = chunks.getChunk(playerChunkX + dx, playerChunkZ + dz, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    List<ModuleEspChunkCache.Entry> entries = ModuleEspChunkCache.XRAY_ESP.chunkEntries(
                        chunk, gameTime, stamp, (scanned, out) -> scanChunk(scanned, selection, colors, playerBlockY, out));
                    for (ModuleEspChunkCache.Entry entry : entries) {
                        Vec3 center = entry.trace();
                        double px = center.x - eyes.x;
                        double py = center.y - eyes.y;
                        double pz = center.z - eyes.z;
                        double distSq = px * px + py * py + pz * pz;
                        if (size < DRAW_CAP) {
                            heapDist[size] = distSq;
                            heapEntry[size] = entry;
                            siftUp(heapDist, heapEntry, size++);
                        } else if (distSq < heapDist[0]) {

                            heapDist[0] = distSq;
                            heapEntry[0] = entry;
                            siftDown(heapDist, heapEntry, size);
                        }
                    }
                }
            }
        }
        for (int i = 0; i < size; i++) {
            emit.accept(heapEntry[i].box(), heapEntry[i].color());
            heapEntry[i] = null;
        }
    }

    private static void siftUp(double[] dist, ModuleEspChunkCache.Entry[] entries, int index) {
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (dist[parent] >= dist[index]) break;
            swap(dist, entries, parent, index);
            index = parent;
        }
    }

    private static void siftDown(double[] dist, ModuleEspChunkCache.Entry[] entries, int size) {
        int index = 0;
        while (true) {
            int left = index * 2 + 1;
            if (left >= size) break;
            int largest = left;
            int right = left + 1;
            if (right < size && dist[right] > dist[left]) largest = right;
            if (dist[index] >= dist[largest]) break;
            swap(dist, entries, index, largest);
            index = largest;
        }
    }

    private static void swap(double[] dist, ModuleEspChunkCache.Entry[] entries, int a, int b) {
        double d = dist[a];
        dist[a] = dist[b];
        dist[b] = d;
        ModuleEspChunkCache.Entry e = entries[a];
        entries[a] = entries[b];
        entries[b] = e;
    }

    private static void scanChunk(LevelChunk chunk, Selection selection, Object2IntMap<Block> colors,
                                  int playerBlockY, List<ModuleEspChunkCache.Entry> out) {
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int playerBand = playerBlockY >> 4;
        Set<Block> targets = selection.blocks();
        java.util.function.Predicate<BlockState> isTarget = state -> targets.contains(state.getBlock());

        int[] order = new int[sections.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        for (int i = 1; i < order.length; i++) {
            int value = order[i];
            int key = Math.abs(chunk.getSectionYFromSectionIndex(value) - playerBand);
            int j = i - 1;
            while (j >= 0 && Math.abs(chunk.getSectionYFromSectionIndex(order[j]) - playerBand) > key) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = value;
        }

        for (int index : order) {
            if (out.size() >= PER_CHUNK_CAP) break;
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;

            if (!section.maybeHas(isTarget)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
            for (int sy = 0; sy < 16 && out.size() < PER_CHUNK_CAP; sy++) {
                for (int sx = 0; sx < 16 && out.size() < PER_CHUNK_CAP; sx++) {
                    for (int sz = 0; sz < 16 && out.size() < PER_CHUNK_CAP; sz++) {
                        BlockState state = section.getBlockState(sx, sy, sz);
                        Block block = state.getBlock();
                        if (!targets.contains(block)) continue;
                        int x = minX + sx;
                        int y = baseY + sy;
                        int z = minZ + sz;
                        out.add(new ModuleEspChunkCache.Entry(
                            new AABB(new BlockPos(x, y, z)),
                            new Vec3(x + 0.5, y + 0.5, z + 0.5),
                            colors.getInt(block)));
                    }
                }
            }
        }
    }

    static void collectNearbyReal(Module xray, ClientLevel level, Player player, NearSink sink) {
        if (sink == null || !oreSimMode(xray) || level == null || player == null) return;
        Selection selection = selection(xray);
        if (selection.blocks().isEmpty()) return;

        BlockPos center = player.blockPosition();
        Vec3 eyes = player.getEyePosition();
        double maxDistSq = (double) NEAR_RANGE * NEAR_RANGE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<BlockPos> candidates = null;
        int matched = 0;
        int touching = 0;

        for (int dx = -NEAR_RANGE; dx <= NEAR_RANGE; dx++) {
            for (int dy = -NEAR_RANGE; dy <= NEAR_RANGE; dy++) {
                for (int dz = -NEAR_RANGE; dz <= NEAR_RANGE; dz++) {
                    int x = center.getX() + dx;
                    int y = center.getY() + dy;
                    int z = center.getZ() + dz;
                    double cx = x + 0.5 - eyes.x;
                    double cy = y + 0.5 - eyes.y;
                    double cz = z + 0.5 - eyes.z;
                    if (cx * cx + cy * cy + cz * cz > maxDistSq) continue;
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!selection.blocks().contains(state.getBlock())) continue;
                    matched++;
                    if (!touchesAir(level, cursor)) continue;
                    touching++;
                    if (candidates == null) candidates = new ArrayList<>();
                    candidates.add(cursor.immutable());
                }
            }
        }
        nearMatched = matched;
        nearTouchingAir = touching;
        if (candidates == null) {
            nearAlreadyPredicted = 0;
            nearVisible = 0;
            return;
        }

        LongOpenHashSet predicted = new LongOpenHashSet();
        AutismOreSimEngine.collectNear(center, NEAR_RANGE + 1, predicted);
        int emitted = 0;
        int skippedPredicted = 0;
        int[] rays = {0};
        for (BlockPos pos : candidates) {
            if (emitted >= NEAR_CAP) break;
            if (predicted.contains(pos.asLong())) {
                skippedPredicted++;
                continue;
            }
            if (!visible(level, player, eyes, pos, rays)) continue;
            sink.accept(pos, level.getBlockState(pos));
            emitted++;
        }
        nearAlreadyPredicted = skippedPredicted;
        nearVisible = emitted;
    }

    @FunctionalInterface
    public interface NearSink {
        void accept(BlockPos pos, BlockState state);
    }

    private static volatile int nearMatched;
    private static volatile int nearTouchingAir;
    private static volatile int nearAlreadyPredicted;
    private static volatile int nearVisible;

    public static String nearDiagnostics() {
        return "matched=" + nearMatched + " touchingAir=" + nearTouchingAir
            + " alreadyPredicted=" + nearAlreadyPredicted + " drawn=" + nearVisible;
    }

    private static boolean touchesAir(ClientLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            neighbour.setWithOffset(pos, direction);
            if (level.getBlockState(neighbour).isAir()) return true;
        }
        return false;
    }

    private static boolean canSee(ClientLevel level, Player player, Vec3 eyes, BlockPos pos, boolean empty,
                                  int[] rays) {
        if (!empty) return visible(level, player, eyes, pos, rays);
        rays[0]++;
        BlockHitResult hit = level.clip(new ClipContext(eyes, Vec3.atCenterOf(pos),
            ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static boolean visible(ClientLevel level, Player player, Vec3 eyes, BlockPos pos, int[] rays) {
        if (rayReaches(level, player, eyes, Vec3.atCenterOf(pos), pos, rays)) return true;
        for (Direction direction : Direction.values()) {
            double faceX = pos.getX() + 0.5 + direction.getStepX() * 0.5;
            double faceY = pos.getY() + 0.5 + direction.getStepY() * 0.5;
            double faceZ = pos.getZ() + 0.5 + direction.getStepZ() * 0.5;

            if ((eyes.x - faceX) * direction.getStepX()
                + (eyes.y - faceY) * direction.getStepY()
                + (eyes.z - faceZ) * direction.getStepZ() <= 0.0) continue;
            if (rayReaches(level, player, eyes, new Vec3(faceX, faceY, faceZ), pos, rays)) return true;
        }
        return false;
    }

    private static boolean rayReaches(ClientLevel level, Player player, Vec3 from, Vec3 to, BlockPos target,
                                      int[] rays) {
        rays[0]++;
        BlockHitResult hit = level.clip(
            new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    private static AABB boxAt(long packed) {
        return new AABB(new BlockPos(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed)));
    }

    static String info(Module xray) {
        if (xray == null || !xray.isEnabled() || !oreSimMode(xray)) return "";

        boolean shaderCullMode = !espStyle(xray) && ModuleRenderUtil.xrayUsesShaderCullMode();
        Long parsedSeed = seed(xray);
        if (lastSeedInput.status() == AutismOreSimSeedInput.Status.INVALID) return "invalid seed";
        if (parsedSeed == null) return "no seed";
        if (enabledMask(xray) == 0) return "no ores";
        if (AutismOreSimEngine.status() == AutismOreSimEngine.Status.UNSUPPORTED_DIMENSION) {
            return "unsupported dimension";
        }
        if (AutismOreSimEngine.status() == AutismOreSimEngine.Status.UNVERIFIED_WORLDGEN) {
            return "unverified worldgen";
        }
        if (AutismOreSimEngine.failed()) return "worldgen failed";

        AutismOreSimEngine.TargetProgress progress = AutismOreSimEngine.targetProgress();
        if (progress.totalChunks() > 0) return progressInfo(progress);
        if (shaderCullMode) return "Shader cull mode";
        return AutismOreSimEngine.status() == AutismOreSimEngine.Status.LOADING_CONTEXT
            ? "loading worldgen"
            : "waiting";
    }

    static String progressInfo(AutismOreSimEngine.TargetProgress progress) {
        return progress.completedChunks() + "/" + progress.totalChunks() + " chunks";
    }
}
