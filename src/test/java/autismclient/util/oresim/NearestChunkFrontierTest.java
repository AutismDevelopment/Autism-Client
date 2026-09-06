package autismclient.util.oresim;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NearestChunkFrontierTest {

    @Test
    void farSideOfGeneratedTileWaitsForCloserPhysicalChunks() {
        double playerX = 8.5;
        double playerZ = 8.5;
        LongOpenHashSet completed = new LongOpenHashSet();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) completed.add(ChunkPos.pack(x, z));
        }

        NearestChunkFrontier.Chunk missing = NearestChunkFrontier.nearestMissing(
            playerX, playerZ, 0, 0, 4, completed::contains);

        assertEquals(-1, missing.x());
        assertEquals(0, missing.z());
        assertTrue(NearestChunkFrontier.canPublish(playerX, playerZ, 0, 0, missing));
        assertFalse(NearestChunkFrontier.canPublish(playerX, playerZ, 3, 3, missing));
    }

    @Test
    void allCompletedChunksCanPublishAndTiesAreDeterministic() {
        NearestChunkFrontier.Chunk none = NearestChunkFrontier.nearestMissing(
            0.5, 0.5, 0, 0, 1, ignored -> true);
        assertTrue(NearestChunkFrontier.canPublish(0.5, 0.5, 7, -9, none));

        assertTrue(NearestChunkFrontier.compare(4.0, -1, 0, 4.0, 0, -1) < 0);
        assertTrue(NearestChunkFrontier.compare(4.0, 0, -1, 4.0, 0, 1) < 0);
    }
}
