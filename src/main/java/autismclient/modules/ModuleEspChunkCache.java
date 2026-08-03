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

    private static final long REVALIDATE_TICKS = 100L;
    private static final int MAX_CACHED_CHUNKS = 4096;
    private static volatile Object cachedLevel;

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
        if (chunks.size() >= MAX_CACHED_CHUNKS) chunks.clear();
        List<Entry> entries = new ArrayList<>();
        scanner.scan(chunk, entries);
        List<Entry> frozen = List.copyOf(entries);
        chunks.put(key, new ChunkScan(stamp, gameTime, frozen));
        return frozen;
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
    }

    private static void markDirty(int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        BLOCK_ESP.chunks.remove(key);
        STORAGE_BE.chunks.remove(key);
        BARRIER_ESP.chunks.remove(key);
        SPAWNER_ESP.chunks.remove(key);
    }

    public static void onPacketReceived(Packet<?> packet) {
        if (BLOCK_ESP.chunks.isEmpty() && STORAGE_BE.chunks.isEmpty() && BARRIER_ESP.chunks.isEmpty()
            && SPAWNER_ESP.chunks.isEmpty()) return;
        if (packet instanceof ClientboundBlockUpdatePacket update) {
            markDirty(update.getPos().getX() >> 4, update.getPos().getZ() >> 4);
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
            sectionUpdate.runUpdates((pos, state) -> markDirty(pos.getX() >> 4, pos.getZ() >> 4));
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
            markDirty(chunkPacket.getX(), chunkPacket.getZ());
        } else if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            markDirty(forget.pos().x(), forget.pos().z());
        } else if (packet instanceof ClientboundRespawnPacket || packet instanceof ClientboundLoginPacket) {
            clearAll();
        }
    }
}
