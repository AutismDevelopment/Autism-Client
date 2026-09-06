package autismclient.util.oresim;

import net.minecraft.world.level.ChunkPos;

import java.util.function.LongPredicate;

final class NearestChunkFrontier {

    record Chunk(int x, int z, double distanceSquared) {
    }

    private NearestChunkFrontier() {
    }

    static Chunk nearestMissing(double playerX, double playerZ, int centerChunkX, int centerChunkZ,
                                int radius, LongPredicate completed) {
        Chunk nearest = null;
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                if (completed.test(ChunkPos.pack(chunkX, chunkZ))) continue;
                double distance = distanceSquared(playerX, playerZ, chunkX, chunkZ);
                if (nearest == null || compare(
                    distance, chunkX, chunkZ, nearest.distanceSquared(), nearest.x(), nearest.z()) < 0) {
                    nearest = new Chunk(chunkX, chunkZ, distance);
                }
            }
        }
        return nearest;
    }

    static boolean canPublish(double playerX, double playerZ, int chunkX, int chunkZ, Chunk nearestMissing) {
        if (nearestMissing == null) return true;
        return compare(distanceSquared(playerX, playerZ, chunkX, chunkZ), chunkX, chunkZ,
            nearestMissing.distanceSquared(), nearestMissing.x(), nearestMissing.z()) <= 0;
    }

    static double distanceSquared(double x, double z, int chunkX, int chunkZ) {
        double minX = chunkX * 16.0 + 0.5;
        double minZ = chunkZ * 16.0 + 0.5;
        double maxX = (chunkX + 1) * 16.0 - 0.5;
        double maxZ = (chunkZ + 1) * 16.0 - 0.5;
        double dx = x < minX ? minX - x : x > maxX ? x - maxX : 0.0;
        double dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0.0;
        return dx * dx + dz * dz;
    }

    static int compare(double leftDistance, int leftX, int leftZ,
                       double rightDistance, int rightX, int rightZ) {
        int distanceOrder = Double.compare(leftDistance, rightDistance);
        if (distanceOrder != 0) return distanceOrder;
        int xOrder = Integer.compare(leftX, rightX);
        return xOrder != 0 ? xOrder : Integer.compare(leftZ, rightZ);
    }
}
