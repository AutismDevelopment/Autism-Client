package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class AutismHoleScanner {

    private static final float BLAST_RESISTANT_AT = 600.0f;

    private static final float INDESTRUCTIBLE_AT = 3_600_000.0f;

    private static final Direction[] HORIZONTALS =
        {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

    private static final byte UNKNOWN = 0;
    private static final byte AIR = 1;
    private static final byte BREAKABLE = 2;
    private static final byte RESISTANT = 3;
    private static final byte INDESTRUCTIBLE = 4;

    private static final int MOVE_THRESHOLD = 4;

    private static final int REVALIDATE_TICKS = 20;
    private static final int CELLS_PER_PUMP = 16384;
    private static final int MAX_HORIZONTAL = 128;
    private static final int MAX_VERTICAL = 64;

    private static final int MAX_SYNC_CELLS = 64_000;

    private static final Set<Subscriber> SUBSCRIBERS = new CopyOnWriteArraySet<>();

    private static volatile Snapshot published = Snapshot.EMPTY;
    private static volatile boolean resetRequested;

    private static Scan active;
    private static BlockPos lastCentre;
    private static int lastHorizontal;
    private static int lastVertical;
    private static long lastPumpTick = Long.MIN_VALUE;
    private static long lastFinishedTick = Long.MIN_VALUE;

    private static WeakReference<Level> lastLevel;

    private AutismHoleScanner() {
    }

    public enum HoleType {
        ONE_BY_ONE_BEDROCK,
        ONE_BY_ONE,
        ONE_BY_TWO,
        TWO_BY_TWO
    }

    public static final class Hole {
        private final HoleType type;
        private final BlockPos pos;
        private final int sizeX;
        private final int sizeZ;
        private final AABB box;

        private Hole(HoleType type, BlockPos pos, int sizeX, int sizeZ) {
            this.type = type;
            this.pos = pos;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + sizeX, pos.getY() + 1, pos.getZ() + sizeZ);
        }

        public HoleType type() {
            return type;
        }

        public BlockPos pos() {
            return pos;
        }

        public int sizeX() {
            return sizeX;
        }

        public int sizeZ() {
            return sizeZ;
        }

        public AABB box() {
            return box;
        }

        public boolean bedrockOnly() {
            return type == HoleType.ONE_BY_ONE_BEDROCK;
        }

        public int cellCount() {
            return sizeX * sizeZ;
        }

        public boolean contains(int x, int y, int z) {
            return y == pos.getY()
                && x >= pos.getX() && x < pos.getX() + sizeX
                && z >= pos.getZ() && z < pos.getZ() + sizeZ;
        }

        public boolean contains(BlockPos other) {
            return other != null && contains(other.getX(), other.getY(), other.getZ());
        }

        public boolean isInvalidatedByFilling(BlockPos placed) {
            if (placed == null) return false;
            return placed.getX() >= pos.getX() && placed.getX() < pos.getX() + sizeX
                && placed.getZ() >= pos.getZ() && placed.getZ() < pos.getZ() + sizeZ
                && placed.getY() >= pos.getY() && placed.getY() <= pos.getY() + 2;
        }

        public List<BlockPos> cells() {
            List<BlockPos> list = new ArrayList<>(sizeX * sizeZ);
            for (int dx = 0; dx < sizeX; dx++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    list.add(new BlockPos(pos.getX() + dx, pos.getY(), pos.getZ() + dz));
                }
            }
            return list;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Hole other)) return false;
            return type == other.type && sizeX == other.sizeX && sizeZ == other.sizeZ
                && pos.equals(other.pos);
        }

        @Override
        public int hashCode() {
            return pos.hashCode() * 31 + type.hashCode();
        }

        @Override
        public String toString() {
            return type + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }
    }

    public interface Subscriber {
        int horizontalDistance();

        int verticalDistance();
    }

    private record Snapshot(List<Hole> holes, Map<Long, Hole> byCell) {
        static final Snapshot EMPTY = new Snapshot(List.of(), Map.of());
    }

    public static void subscribe(Subscriber subscriber) {
        if (subscriber != null) SUBSCRIBERS.add(subscriber);
    }

    public static void unsubscribe(Subscriber subscriber) {
        if (subscriber != null && SUBSCRIBERS.remove(subscriber) && SUBSCRIBERS.isEmpty()) clear();
    }

    public static boolean running() {
        return !SUBSCRIBERS.isEmpty();
    }

    public static void clear() {
        published = Snapshot.EMPTY;
        resetRequested = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) reset();
        else mc.execute(AutismHoleScanner::reset);
    }

    public static void tick() {
        pump();
    }

    public static List<Hole> holes() {
        pump();
        return published.holes();
    }

    public static Hole holeAt(BlockPos pos) {
        pump();
        return pos == null ? null : published.byCell().get(pos.asLong());
    }

    public static boolean isSafeHole(BlockPos pos) {
        return holeAt(pos) != null;
    }

    public static List<Hole> scan(BlockPos centre, int horizontalRadius, int verticalRadius) {
        Level level = Minecraft.getInstance().level;
        if (level == null || centre == null) return List.of();
        Scan scan = new Scan(level, centre, horizontalRadius, verticalRadius, MAX_SYNC_CELLS);
        scan.advance(Integer.MAX_VALUE);
        return List.copyOf(scan.found);
    }

    public static Hole scanAt(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null || pos == null) return null;
        Scan scan = new Scan(level, pos, 0, 0, MAX_SYNC_CELLS);
        scan.advance(Integer.MAX_VALUE);
        return scan.found.isEmpty() ? null : scan.found.get(0);
    }

    private static void pump() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return;

        if (resetRequested) {
            resetRequested = false;
            reset();
        }

        Level level = mc.level;
        if (level == null || mc.player == null || SUBSCRIBERS.isEmpty()) {
            if (active != null || lastCentre != null) reset();
            if (published != Snapshot.EMPTY) published = Snapshot.EMPTY;
            rememberLevel(level);
            return;
        }
        if (lastLevel() != level) {
            reset();
            rememberLevel(level);
            published = Snapshot.EMPTY;
        }

        long gameTime = level.getGameTime();
        if (gameTime == lastPumpTick) return;
        lastPumpTick = gameTime;

        if (active == null) {
            int horizontal = 0;
            int vertical = 0;
            for (Subscriber subscriber : SUBSCRIBERS) {
                horizontal = Math.max(horizontal, subscriber.horizontalDistance());
                vertical = Math.max(vertical, subscriber.verticalDistance());
            }
            BlockPos centre = mc.player.blockPosition();
            boolean moved = lastCentre == null || centre.distManhattan(lastCentre) >= MOVE_THRESHOLD;
            boolean resized = horizontal != lastHorizontal || vertical != lastVertical;
            boolean stale = gameTime - lastFinishedTick >= REVALIDATE_TICKS;
            if (!moved && !resized && !stale) return;
            lastHorizontal = horizontal;
            lastVertical = vertical;
            active = new Scan(level, centre, horizontal, vertical, Integer.MAX_VALUE);
        }

        if (!active.advance(CELLS_PER_PUMP)) return;

        publish(active.found);
        lastCentre = active.centre;
        lastFinishedTick = gameTime;
        active = null;
    }

    private static Level lastLevel() {
        WeakReference<Level> ref = lastLevel;
        return ref == null ? null : ref.get();
    }

    private static void rememberLevel(Level level) {
        if (lastLevel() == level) return;
        lastLevel = level == null ? null : new WeakReference<>(level);
    }

    private static void reset() {
        active = null;
        lastCentre = null;
        lastLevel = null;
        lastHorizontal = 0;
        lastVertical = 0;
        lastPumpTick = Long.MIN_VALUE;
        lastFinishedTick = Long.MIN_VALUE;
    }

    private static void publish(List<Hole> found) {
        List<Hole> frozen = List.copyOf(found);
        Map<Long, Hole> byCell = new HashMap<>(Math.max(16, frozen.size() * 3));
        for (Hole hole : frozen) {
            for (int dx = 0; dx < hole.sizeX; dx++) {
                for (int dz = 0; dz < hole.sizeZ; dz++) {
                    byCell.putIfAbsent(
                        BlockPos.asLong(hole.pos.getX() + dx, hole.pos.getY(), hole.pos.getZ() + dz),
                        hole);
                }
            }
        }
        published = new Snapshot(frozen, byCell);
    }

    private static long cellCount(int horizontal, int vertical) {
        long side = 2L * horizontal + 1L;
        return side * side * (2L * vertical + 1L);
    }

    private static final class Scan {

        private final WeakReference<Level> level;
        private final BlockPos centre;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private final int bufX;
        private final int bufZ;
        private final int spanX;
        private final int spanZ;
        private final int layerSize;
        private final byte[] states;
        private final BitSet claimed;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        private final List<Hole> found = new ArrayList<>();

        private int x;
        private int y;
        private int z;
        private int loadedY = Integer.MIN_VALUE;
        private boolean finished;

        Scan(Level level, BlockPos centre, int horizontal, int vertical, long maxCells) {
            this.level = new WeakReference<>(level);
            this.centre = centre;

            int h = Mth.clamp(horizontal, 0, MAX_HORIZONTAL);
            int v = Mth.clamp(vertical, 0, MAX_VERTICAL);
            while (h > 0 && cellCount(h, v) > maxCells) h--;
            while (v > 0 && cellCount(h, v) > maxCells) v--;

            this.minX = centre.getX() - h;
            this.maxX = centre.getX() + h;
            this.minZ = centre.getZ() - h;
            this.maxZ = centre.getZ() + h;

            this.minY = Math.max(centre.getY() - v, level.getMinY() + 1);
            this.maxY = Math.min(centre.getY() + v, level.getMaxY() - 3);

            this.bufX = minX - 2;
            this.bufZ = minZ - 2;
            this.spanX = (maxX - minX + 1) + 4;
            this.spanZ = (maxZ - minZ + 1) + 4;
            this.layerSize = spanX * spanZ;
            this.states = new byte[layerSize * 4];
            this.claimed = new BitSet(layerSize);

            this.x = minX;
            this.y = minY;
            this.z = minZ;
        }

        boolean advance(int cellBudget) {
            if (finished) return true;
            int budget = Math.max(1, cellBudget);
            while (budget > 0) {
                if (y > maxY) {
                    finished = true;
                    return true;
                }
                if (loadedY != y) beginRow(y);
                detect(x, y, z);
                budget--;
                if (++z > maxZ) {
                    z = minZ;
                    if (++x > maxX) {
                        x = minX;
                        y++;
                    }
                }
            }
            if (y > maxY) finished = true;
            return finished;
        }

        private void beginRow(int row) {
            if (loadedY == Integer.MIN_VALUE || row - loadedY != 1) {
                Arrays.fill(states, UNKNOWN);
            } else {

                int slot = (row + 2) & 3;
                Arrays.fill(states, slot * layerSize, (slot + 1) * layerSize, UNKNOWN);
            }

            claimed.clear();
            loadedY = row;
        }

        private void detect(int cellX, int cellY, int cellZ) {
            if (isClaimed(cellX, cellZ)) return;
            if (!checkColumn(cellX, cellY, cellZ)) return;

            int resistant = 0;
            int open1 = -1;
            int open2 = -1;
            for (int i = 0; i < HORIZONTALS.length; i++) {
                Direction direction = HORIZONTALS[i];
                if (isResistant(state(cellX + direction.getStepX(), cellY, cellZ + direction.getStepZ()))) {
                    resistant++;
                } else if (open1 < 0) {
                    open1 = i;
                } else if (open2 < 0) {
                    open2 = i;
                }
            }

            switch (resistant) {
                case 4 -> addOneByOne(cellX, cellY, cellZ);
                case 3 -> addOneByTwo(cellX, cellY, cellZ, HORIZONTALS[open1]);
                case 2 -> addTwoByTwo(cellX, cellY, cellZ, HORIZONTALS[open1], HORIZONTALS[open2]);
                default -> {
                }
            }
        }

        private void addOneByOne(int cellX, int cellY, int cellZ) {
            boolean bedrock = state(cellX, cellY - 1, cellZ) == INDESTRUCTIBLE;
            for (int i = 0; bedrock && i < HORIZONTALS.length; i++) {
                Direction direction = HORIZONTALS[i];
                bedrock = state(cellX + direction.getStepX(), cellY, cellZ + direction.getStepZ())
                    == INDESTRUCTIBLE;
            }
            add(new Hole(bedrock ? HoleType.ONE_BY_ONE_BEDROCK : HoleType.ONE_BY_ONE,
                new BlockPos(cellX, cellY, cellZ), 1, 1));
        }

        private void addOneByTwo(int cellX, int cellY, int cellZ, Direction open) {
            int otherX = cellX + open.getStepX();
            int otherZ = cellZ + open.getStepZ();
            if (!checkColumn(otherX, cellY, otherZ)) return;

            Direction back = open.getOpposite();
            for (Direction direction : HORIZONTALS) {
                if (direction == back) continue;
                if (!isResistant(state(otherX + direction.getStepX(), cellY,
                    otherZ + direction.getStepZ()))) {
                    return;
                }
            }

            boolean alongX = open.getAxis() == Direction.Axis.X;
            add(new Hole(HoleType.ONE_BY_TWO,
                new BlockPos(Math.min(cellX, otherX), cellY, Math.min(cellZ, otherZ)),
                alongX ? 2 : 1, alongX ? 1 : 2));
        }

        private void addTwoByTwo(int cellX, int cellY, int cellZ, Direction d1, Direction d2) {
            int ax = cellX + d1.getStepX();
            int az = cellZ + d1.getStepZ();
            if (!checkCell(ax, cellY, az, d1, d2.getOpposite())) return;

            int bx = cellX + d2.getStepX();
            int bz = cellZ + d2.getStepZ();
            if (!checkCell(bx, cellY, bz, d2, d1.getOpposite())) return;

            int cx = bx + d1.getStepX();
            int cz = bz + d1.getStepZ();
            if (!checkCell(cx, cellY, cz, d1, d2)) return;

            add(new Hole(HoleType.TWO_BY_TWO,
                new BlockPos(Math.min(cellX, cx), cellY, Math.min(cellZ, cz)), 2, 2));
        }

        private boolean checkCell(int cellX, int cellY, int cellZ, Direction a, Direction b) {
            return checkColumn(cellX, cellY, cellZ)
                && isResistant(state(cellX + a.getStepX(), cellY, cellZ + a.getStepZ()))
                && isResistant(state(cellX + b.getStepX(), cellY, cellZ + b.getStepZ()));
        }

        private boolean checkColumn(int cellX, int cellY, int cellZ) {
            return isResistant(state(cellX, cellY - 1, cellZ))
                && state(cellX, cellY, cellZ) == AIR
                && state(cellX, cellY + 1, cellZ) == AIR
                && state(cellX, cellY + 2, cellZ) == AIR;
        }

        private void add(Hole hole) {
            found.add(hole);
            for (int dx = 0; dx < hole.sizeX; dx++) {
                for (int dz = 0; dz < hole.sizeZ; dz++) {
                    claim(hole.pos.getX() + dx, hole.pos.getZ() + dz);
                }
            }
        }

        private int planeIndex(int cellX, int cellZ) {
            int dx = cellX - bufX;
            int dz = cellZ - bufZ;
            if (dx < 0 || dz < 0 || dx >= spanX || dz >= spanZ) return -1;
            return dx * spanZ + dz;
        }

        private void claim(int cellX, int cellZ) {
            int index = planeIndex(cellX, cellZ);
            if (index >= 0) claimed.set(index);
        }

        private boolean isClaimed(int cellX, int cellZ) {
            int index = planeIndex(cellX, cellZ);
            return index >= 0 && claimed.get(index);
        }

        private byte state(int cellX, int cellY, int cellZ) {
            int index = planeIndex(cellX, cellZ);

            if (index < 0 || cellY < loadedY - 1 || cellY > loadedY + 2) {
                return classify(cellX, cellY, cellZ);
            }
            index += (cellY & 3) * layerSize;
            byte cached = states[index];
            if (cached != UNKNOWN) return cached;
            byte value = classify(cellX, cellY, cellZ);
            states[index] = value;
            return value;
        }

        private byte classify(int cellX, int cellY, int cellZ) {
            Level world = level.get();

            if (world == null) return BREAKABLE;
            BlockState blockState = world.getBlockState(cursor.set(cellX, cellY, cellZ));
            if (blockState.isAir()) return AIR;
            float resistance = blockState.getBlock().getExplosionResistance();
            if (resistance >= INDESTRUCTIBLE_AT) return INDESTRUCTIBLE;
            if (resistance >= BLAST_RESISTANT_AT) return RESISTANT;
            return BREAKABLE;
        }

        private static boolean isResistant(byte value) {
            return value == RESISTANT || value == INDESTRUCTIBLE;
        }
    }
}
