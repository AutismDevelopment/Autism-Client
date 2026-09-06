package autismclient.modules;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModuleEspMesh {
    private static final double EPSILON = 1.0E-7;

    record Box(AABB box, int color) {
    }

    record Quad(double x1, double y1, double z1,
                double x2, double y2, double z2,
                double x3, double y3, double z3,
                double x4, double y4, double z4,
                int color) {
    }

    record Edge(double x1, double y1, double z1,
                double x2, double y2, double z2,
                int color) {
    }

    record Geometry(List<Quad> quads, List<Edge> edges) {
        private static final Geometry EMPTY = new Geometry(List.of(), List.of());
    }

    private record GridEdge(int axis, int x, int y, int z, int color) {
    }

    private ModuleEspMesh() {
    }

    static Geometry build(List<Box> input) {
        if (input == null || input.isEmpty()) return Geometry.EMPTY;

        Map<Integer, LongOpenHashSet> voxelsByColor = new HashMap<>();
        List<Box> partial = new ArrayList<>();
        for (Box target : input) {
            if (target == null || target.box() == null) continue;
            int[] voxel = fullVoxel(target.box());
            if (voxel == null) {
                partial.add(target);
            } else {
                voxelsByColor.computeIfAbsent(target.color(), ignored -> new LongOpenHashSet())
                    .add(BlockPos.asLong(voxel[0], voxel[1], voxel[2]));
            }
        }

        List<Quad> quads = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<Integer, LongOpenHashSet> group : voxelsByColor.entrySet()) {
            buildVoxelGroup(group.getValue(), group.getKey(), quads, edges);
        }
        for (Box box : mergePartialBoxes(partial)) addBox(box.box(), box.color(), quads, edges);
        return new Geometry(List.copyOf(quads), List.copyOf(edges));
    }

    static boolean isFullVoxel(AABB box) {
        return fullVoxel(box) != null;
    }

    private static int[] fullVoxel(AABB box) {
        if (box == null) return null;
        int x = (int) Math.rint(box.minX);
        int y = (int) Math.rint(box.minY);
        int z = (int) Math.rint(box.minZ);
        if (!close(box.minX, x) || !close(box.minY, y) || !close(box.minZ, z)
            || !close(box.maxX, x + 1.0) || !close(box.maxY, y + 1.0) || !close(box.maxZ, z + 1.0)) {
            return null;
        }
        return new int[]{x, y, z};
    }

    private static void buildVoxelGroup(LongOpenHashSet occupied, int color,
                                        List<Quad> quads, List<Edge> outputEdges) {
        if (occupied == null || occupied.isEmpty()) return;
        LongOpenHashSet[] candidates = {
            new LongOpenHashSet(occupied.size() * 4),
            new LongOpenHashSet(occupied.size() * 4),
            new LongOpenHashSet(occupied.size() * 4)
        };

        for (LongIterator iterator = occupied.iterator(); iterator.hasNext();) {
            long packed = iterator.nextLong();
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);

            if (!contains(occupied, x, y - 1, z)) addFace(quads, 0, x, y, z, x + 1, y + 1, z + 1, color);
            if (!contains(occupied, x, y + 1, z)) addFace(quads, 1, x, y, z, x + 1, y + 1, z + 1, color);
            if (!contains(occupied, x, y, z + 1)) addFace(quads, 2, x, y, z, x + 1, y + 1, z + 1, color);
            if (!contains(occupied, x, y, z - 1)) addFace(quads, 3, x, y, z, x + 1, y + 1, z + 1, color);
            if (!contains(occupied, x - 1, y, z)) addFace(quads, 4, x, y, z, x + 1, y + 1, z + 1, color);
            if (!contains(occupied, x + 1, y, z)) addFace(quads, 5, x, y, z, x + 1, y + 1, z + 1, color);

            addVoxelEdgeCandidates(candidates, x, y, z);
        }

        List<GridEdge> visible = new ArrayList<>();
        for (int axis = 0; axis < candidates.length; axis++) {
            for (LongIterator iterator = candidates[axis].iterator(); iterator.hasNext();) {
                long packed = iterator.nextLong();
                int x = BlockPos.getX(packed);
                int y = BlockPos.getY(packed);
                int z = BlockPos.getZ(packed);
                if (isBoundaryCrease(occupied, axis, x, y, z)) {
                    visible.add(new GridEdge(axis, x, y, z, color));
                }
            }
        }
        mergeGridEdges(visible, outputEdges);
    }

    private static void addVoxelEdgeCandidates(LongOpenHashSet[] candidates, int x, int y, int z) {
        candidates[0].add(BlockPos.asLong(x, y, z));
        candidates[0].add(BlockPos.asLong(x, y + 1, z));
        candidates[0].add(BlockPos.asLong(x, y, z + 1));
        candidates[0].add(BlockPos.asLong(x, y + 1, z + 1));

        candidates[1].add(BlockPos.asLong(x, y, z));
        candidates[1].add(BlockPos.asLong(x + 1, y, z));
        candidates[1].add(BlockPos.asLong(x, y, z + 1));
        candidates[1].add(BlockPos.asLong(x + 1, y, z + 1));

        candidates[2].add(BlockPos.asLong(x, y, z));
        candidates[2].add(BlockPos.asLong(x + 1, y, z));
        candidates[2].add(BlockPos.asLong(x, y + 1, z));
        candidates[2].add(BlockPos.asLong(x + 1, y + 1, z));
    }

    private static boolean isBoundaryCrease(LongOpenHashSet occupied, int axis, int x, int y, int z) {
        boolean a;
        boolean b;
        boolean c;
        boolean d;
        if (axis == 0) {
            a = contains(occupied, x, y - 1, z - 1);
            b = contains(occupied, x, y, z - 1);
            c = contains(occupied, x, y - 1, z);
            d = contains(occupied, x, y, z);
        } else if (axis == 1) {
            a = contains(occupied, x - 1, y, z - 1);
            b = contains(occupied, x, y, z - 1);
            c = contains(occupied, x - 1, y, z);
            d = contains(occupied, x, y, z);
        } else {
            a = contains(occupied, x - 1, y - 1, z);
            b = contains(occupied, x, y - 1, z);
            c = contains(occupied, x - 1, y, z);
            d = contains(occupied, x, y, z);
        }
        int count = (a ? 1 : 0) + (b ? 1 : 0) + (c ? 1 : 0) + (d ? 1 : 0);
        return count == 1 || count == 3 || count == 2 && (a && d || b && c);
    }

    private static void mergeGridEdges(List<GridEdge> input, List<Edge> output) {
        input.sort((left, right) -> {
            int compared = Integer.compare(left.color(), right.color());
            if (compared != 0) return compared;
            compared = Integer.compare(left.axis(), right.axis());
            if (compared != 0) return compared;
            compared = Integer.compare(fixedA(left), fixedA(right));
            if (compared != 0) return compared;
            compared = Integer.compare(fixedB(left), fixedB(right));
            if (compared != 0) return compared;
            return Integer.compare(axisStart(left), axisStart(right));
        });
        int index = 0;
        while (index < input.size()) {
            GridEdge first = input.get(index++);
            int end = axisStart(first) + 1;
            while (index < input.size()) {
                GridEdge next = input.get(index);
                if (next.color() != first.color() || next.axis() != first.axis()
                    || fixedA(next) != fixedA(first) || fixedB(next) != fixedB(first)
                    || axisStart(next) != end) {
                    break;
                }
                end++;
                index++;
            }
            addGridEdge(output, first, end);
        }
    }

    private static int axisStart(GridEdge edge) {
        return edge.axis() == 0 ? edge.x() : edge.axis() == 1 ? edge.y() : edge.z();
    }

    private static int fixedA(GridEdge edge) {
        return edge.axis() == 0 ? edge.y() : edge.x();
    }

    private static int fixedB(GridEdge edge) {
        return edge.axis() == 2 ? edge.y() : edge.z();
    }

    private static void addGridEdge(List<Edge> output, GridEdge edge, int end) {
        if (edge.axis() == 0) {
            output.add(new Edge(edge.x(), edge.y(), edge.z(), end, edge.y(), edge.z(), edge.color()));
        } else if (edge.axis() == 1) {
            output.add(new Edge(edge.x(), edge.y(), edge.z(), edge.x(), end, edge.z(), edge.color()));
        } else {
            output.add(new Edge(edge.x(), edge.y(), edge.z(), edge.x(), edge.y(), end, edge.color()));
        }
    }

    private static boolean contains(LongOpenHashSet occupied, int x, int y, int z) {
        return occupied.contains(BlockPos.asLong(x, y, z));
    }

    private static List<Box> mergePartialBoxes(List<Box> boxes) {
        if (boxes.size() < 2) return boxes;
        List<Box> merged = new ArrayList<>(boxes);
        boolean changed;
        int passes = 0;
        do {
            int before = merged.size();
            merged = mergeAxis(merged, 0);
            merged = mergeAxis(merged, 1);
            merged = mergeAxis(merged, 2);
            changed = merged.size() < before;
        } while (changed && ++passes < 12);
        return merged;
    }

    private static List<Box> mergeAxis(List<Box> boxes, int axis) {
        if (boxes.size() < 2) return boxes;
        List<Box> sorted = new ArrayList<>(boxes);
        sorted.sort(partialComparator(axis));
        List<Box> output = new ArrayList<>(sorted.size());
        Box current = sorted.getFirst();
        for (int i = 1; i < sorted.size(); i++) {
            Box next = sorted.get(i);
            if (sameStrip(current, next, axis) && axisMin(next.box(), axis) <= axisMax(current.box(), axis) + EPSILON) {
                current = new Box(unionAlong(current.box(), next.box(), axis), current.color());
            } else {
                output.add(current);
                current = next;
            }
        }
        output.add(current);
        return output;
    }

    private static Comparator<Box> partialComparator(int axis) {
        return (left, right) -> {
            int compared = Integer.compare(left.color(), right.color());
            if (compared != 0) return compared;
            AABB a = left.box();
            AABB b = right.box();
            for (int candidate = 0; candidate < 3; candidate++) {
                if (candidate == axis) continue;
                compared = Double.compare(axisMin(a, candidate), axisMin(b, candidate));
                if (compared != 0) return compared;
                compared = Double.compare(axisMax(a, candidate), axisMax(b, candidate));
                if (compared != 0) return compared;
            }
            compared = Double.compare(axisMin(a, axis), axisMin(b, axis));
            return compared != 0 ? compared : Double.compare(axisMax(a, axis), axisMax(b, axis));
        };
    }

    private static boolean sameStrip(Box left, Box right, int axis) {
        if (left.color() != right.color()) return false;
        for (int candidate = 0; candidate < 3; candidate++) {
            if (candidate == axis) continue;
            if (!close(axisMin(left.box(), candidate), axisMin(right.box(), candidate))
                || !close(axisMax(left.box(), candidate), axisMax(right.box(), candidate))) return false;
        }
        return true;
    }

    private static AABB unionAlong(AABB left, AABB right, int axis) {
        return switch (axis) {
            case 0 -> new AABB(Math.min(left.minX, right.minX), left.minY, left.minZ,
                Math.max(left.maxX, right.maxX), left.maxY, left.maxZ);
            case 1 -> new AABB(left.minX, Math.min(left.minY, right.minY), left.minZ,
                left.maxX, Math.max(left.maxY, right.maxY), left.maxZ);
            default -> new AABB(left.minX, left.minY, Math.min(left.minZ, right.minZ),
                left.maxX, left.maxY, Math.max(left.maxZ, right.maxZ));
        };
    }

    private static double axisMin(AABB box, int axis) {
        return axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
    }

    private static double axisMax(AABB box, int axis) {
        return axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) <= EPSILON;
    }

    private static void addBox(AABB box, int color, List<Quad> quads, List<Edge> edges) {
        for (int face = 0; face < 6; face++) {
            addFace(quads, face, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
        }
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        edges.add(new Edge(x1, y1, z1, x2, y1, z1, color));
        edges.add(new Edge(x2, y1, z1, x2, y1, z2, color));
        edges.add(new Edge(x2, y1, z2, x1, y1, z2, color));
        edges.add(new Edge(x1, y1, z2, x1, y1, z1, color));
        edges.add(new Edge(x1, y2, z1, x2, y2, z1, color));
        edges.add(new Edge(x2, y2, z1, x2, y2, z2, color));
        edges.add(new Edge(x2, y2, z2, x1, y2, z2, color));
        edges.add(new Edge(x1, y2, z2, x1, y2, z1, color));
        edges.add(new Edge(x1, y1, z1, x1, y2, z1, color));
        edges.add(new Edge(x2, y1, z1, x2, y2, z1, color));
        edges.add(new Edge(x2, y1, z2, x2, y2, z2, color));
        edges.add(new Edge(x1, y1, z2, x1, y2, z2, color));
    }

    private static void addFace(List<Quad> quads, int face,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2, int color) {
        switch (face) {
            case 0 -> quads.add(new Quad(x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, color));
            case 1 -> quads.add(new Quad(x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, color));
            case 2 -> quads.add(new Quad(x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, color));
            case 3 -> quads.add(new Quad(x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, color));
            case 4 -> quads.add(new Quad(x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, color));
            default -> quads.add(new Quad(x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, color));
        }
    }
}
