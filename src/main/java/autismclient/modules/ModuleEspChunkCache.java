package autismclient.modules;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ModuleEspChunkCache {
    static final ModuleEspChunkCache BLOCK_ESP = new ModuleEspChunkCache();
    static final ModuleEspChunkCache STORAGE_BE = new ModuleEspChunkCache();

    static final ModuleEspChunkCache BARRIER_ESP = new ModuleEspChunkCache();

    static final ModuleEspChunkCache SPAWNER_ESP = new ModuleEspChunkCache();

    static final ModuleEspChunkCache XRAY_ESP = new ModuleEspChunkCache();

    private static final long REVALIDATE_TICKS = 100L;
    private static final int MAX_CACHED_CHUNKS = 4096;
    private static volatile Object cachedLevel;

    private static final java.util.concurrent.atomic.AtomicInteger GENERATION =
        new java.util.concurrent.atomic.AtomicInteger();

    public static int generation() {
        return GENERATION.get();
    }

    record Entry(AABB box, Vec3 trace, int color) {}

    interface ChunkScanner {
        void scan(LevelChunk chunk, List<Entry> out);
    }

    private record ChunkScan(String stamp, long scannedAt, List<Entry> entries) {}

    private final ConcurrentHashMap<Long, ChunkScan> chunks = new ConcurrentHashMap<>();

    private ModuleEspChunkCache() {
    }

    List<Entry> chunkEntries(LevelChunk chunk, long gameTime, String stamp, ChunkScanner scanner) {
        long key = chunk.getPos().pack();
        ChunkScan cached = chunks.get(key);
        if (cached != null && cached.stamp.equals(stamp) && gameTime - cached.scannedAt < REVALIDATE_TICKS) {
            return cached.entries;
        }
        if (chunks.size() >= MAX_CACHED_CHUNKS) evictFarthest(chunk.getPos());
        List<Entry> entries = new ArrayList<>();
        scanner.scan(chunk, entries);
        List<Entry> frozen = List.copyOf(entries);
        chunks.put(key, new ChunkScan(stamp, gameTime, frozen));

        if (cached == null || !cached.entries.equals(frozen)) GENERATION.incrementAndGet();
        return frozen;
    }

    private void evictFarthest(ChunkPos center) {
        int target = MAX_CACHED_CHUNKS * 3 / 4;
        for (int keep = 24; keep >= 4 && chunks.size() > target; keep -= 4) {
            int radius = keep;
            chunks.keySet().removeIf(key -> Math.max(
                Math.abs(ChunkPos.getX(key) - center.x()),
                Math.abs(ChunkPos.getZ(key) - center.z())) > radius);
        }
        if (chunks.size() >= MAX_CACHED_CHUNKS) chunks.clear();
    }

    static void onLevel(Object level) {
        if (cachedLevel == level) return;
        cachedLevel = level;
        clearAll();
    }

    static void clearAll() {
        BLOCK_ESP.chunks.clear();
        STORAGE_BE.chunks.clear();
        BARRIER_ESP.chunks.clear();
        SPAWNER_ESP.chunks.clear();
        XRAY_ESP.chunks.clear();
        GENERATION.incrementAndGet();
    }

    private static void markDirty(int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        boolean had = BLOCK_ESP.chunks.remove(key) != null;
        had |= STORAGE_BE.chunks.remove(key) != null;
        had |= BARRIER_ESP.chunks.remove(key) != null;
        had |= SPAWNER_ESP.chunks.remove(key) != null;
        had |= XRAY_ESP.chunks.remove(key) != null;

        if (had) GENERATION.incrementAndGet();
    }

    public static void onPacketReceived(Packet<?> packet) {
        if (BLOCK_ESP.chunks.isEmpty() && STORAGE_BE.chunks.isEmpty() && BARRIER_ESP.chunks.isEmpty()
            && SPAWNER_ESP.chunks.isEmpty() && XRAY_ESP.chunks.isEmpty()) return;
        if (packet instanceof ClientboundBlockUpdatePacket update) {
            markDirty(update.getPos().getX() >> 4, update.getPos().getZ() >> 4);
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
            sectionUpdate.runUpdates((pos, state) -> markDirty(pos.getX() >> 4, pos.getZ() >> 4));
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
            markDirty(chunkPacket.getX(), chunkPacket.getZ());

            GENERATION.incrementAndGet();
        } else if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            markDirty(forget.pos().x(), forget.pos().z());
        } else if (packet instanceof ClientboundRespawnPacket || packet instanceof ClientboundLoginPacket) {
            clearAll();
        }
    }
}
