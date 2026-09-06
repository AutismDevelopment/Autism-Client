package autismclient.modules;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleEspMeshTest {
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    @Test
    void adjacentFullBlocksBecomeOneOuterRectangularOutline() {
        ModuleEspMesh.Geometry mesh = ModuleEspMesh.build(List.of(
            box(0, 0, 0, RED),
            box(1, 0, 0, RED)
        ));

        assertEquals(10, mesh.quads().size(), "the two touching fill faces must be removed");
        assertEquals(12, mesh.edges().size(), "flat one-block seams must collapse into the cuboid outline");
        assertTrue(mesh.edges().stream().anyMatch(edge -> same(edge.x1(), 0) && same(edge.x2(), 2)
            && same(edge.y1(), 0) && same(edge.y2(), 0) && same(edge.z1(), 0) && same(edge.z2(), 0)));
        assertFalse(mesh.quads().stream().anyMatch(face -> allX(face, 1)),
            "the shared internal face must not be emitted");
        assertFalse(mesh.edges().stream().anyMatch(edge -> verticalAt(edge, 1, 0)),
            "the flat front-face seam must not be emitted");
    }

    @Test
    void irregularVoxelUnionKeepsConcaveCreasesButNotFlatSeams() {
        ModuleEspMesh.Geometry mesh = ModuleEspMesh.build(List.of(
            box(0, 0, 0, RED),
            box(1, 0, 0, RED),
            box(0, 0, 1, RED)
        ));

        assertEquals(14, mesh.quads().size());
        assertFalse(mesh.edges().stream().anyMatch(edge -> verticalAt(edge, 1, 0)));
        assertTrue(mesh.edges().stream().anyMatch(edge -> verticalAt(edge, 1, 1)),
            "the inside corner of the L must remain visible");
    }

    @Test
    void touchingDifferentStorageColorsKeepTheirBoundary() {
        ModuleEspMesh.Geometry mesh = ModuleEspMesh.build(List.of(
            box(0, 0, 0, RED),
            box(1, 0, 0, BLUE)
        ));

        assertEquals(12, mesh.quads().size());
        assertEquals(24, mesh.edges().size());
    }

    @Test
    void matchingInsetStorageShapesCoalesceLikeADoubleChest() {
        ModuleEspMesh.Geometry mesh = ModuleEspMesh.build(List.of(
            new ModuleEspMesh.Box(new AABB(0.0625, 0, 0.0625, 1, 0.875, 0.9375), RED),
            new ModuleEspMesh.Box(new AABB(1, 0, 0.0625, 1.9375, 0.875, 0.9375), RED)
        ));

        assertEquals(6, mesh.quads().size());
        assertEquals(12, mesh.edges().size());
        assertTrue(mesh.edges().stream().anyMatch(edge -> same(edge.x1(), 0.0625)
            && same(edge.x2(), 1.9375)));
    }

    private static ModuleEspMesh.Box box(int x, int y, int z, int color) {
        return new ModuleEspMesh.Box(new AABB(x, y, z, x + 1, y + 1, z + 1), color);
    }

    private static boolean verticalAt(ModuleEspMesh.Edge edge, double x, double z) {
        return same(edge.x1(), x) && same(edge.x2(), x)
            && same(edge.z1(), z) && same(edge.z2(), z)
            && !same(edge.y1(), edge.y2());
    }

    private static boolean allX(ModuleEspMesh.Quad face, double x) {
        return same(face.x1(), x) && same(face.x2(), x) && same(face.x3(), x) && same(face.x4(), x);
    }

    private static boolean same(double left, double right) {
        return Math.abs(left - right) < 1.0E-8;
    }
}
