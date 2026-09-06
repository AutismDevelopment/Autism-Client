package autismclient.modules;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrajectorySimTest {
    private static TrajectorySim.Info info(double gravity, double drag, double dragInWater, double v0, float roll) {
        return new TrajectorySim.Info(gravity, 0.25, v0, drag, dragInWater, roll, true, true, false);
    }

    @Test
    void launchVelocityFacesTheAimAndCarriesTheInitialSpeed() {
        TrajectorySim.Info snowball = info(0.03, 0.99, 0.8, 1.5, 0.0F);

        Vec3 forward = TrajectorySim.launchVelocity(0.0F, 0.0F, snowball);
        assertEquals(1.5, forward.length(), 1.0E-6);
        assertTrue(forward.z > 1.49, "yaw 0 should fire toward +Z, got " + forward);
        assertEquals(0.0, forward.y, 1.0E-6);

        Vec3 down = TrajectorySim.launchVelocity(0.0F, 90.0F, snowball);
        assertEquals(1.5, down.length(), 1.0E-6);
        assertTrue(down.y < -1.49, "pitch 90 should fire downward, got " + down);
    }

    @Test
    void rollTiltsThePitchLikeASplashPotion() {

        TrajectorySim.Info potion = info(0.05, 0.99, 0.8, 0.5, -20.0F);
        Vec3 thrown = TrajectorySim.launchVelocity(0.0F, 0.0F, potion);
        assertTrue(thrown.y > 0.0, "negative roll should angle the throw upward, got " + thrown);
        assertEquals(0.5, thrown.length(), 1.0E-6);
    }

    @Test
    void eachTickAppliesDragThenGravity() {
        TrajectorySim.Info generic = info(0.03, 0.99, 0.8, 1.5, 0.0F);
        Vec3 stepped = TrajectorySim.stepVelocity(new Vec3(1.0, 1.0, 1.0), generic, false);
        assertEquals(0.99, stepped.x, 1.0E-9);
        assertEquals(0.99, stepped.z, 1.0E-9);
        assertEquals(0.99 - 0.03, stepped.y, 1.0E-9);
    }

    @Test
    void waterUsesTheHeavierDrag() {
        TrajectorySim.Info generic = info(0.03, 0.99, 0.8, 1.5, 0.0F);
        Vec3 inWater = TrajectorySim.stepVelocity(new Vec3(1.0, 0.0, 0.0), generic, true);
        Vec3 inAir = TrajectorySim.stepVelocity(new Vec3(1.0, 0.0, 0.0), generic, false);
        assertEquals(0.8, inWater.x, 1.0E-9);
        assertTrue(inWater.x < inAir.x, "water must slow the projectile more than air");
    }

    @Test
    void gravitylessProjectilesKeepTheirHeight() {

        TrajectorySim.Info windCharge = new TrajectorySim.Info(0.0, 1.0, 1.5, 1.0, 1.0, 0.0F, true, true, false);
        Vec3 stepped = TrajectorySim.stepVelocity(new Vec3(0.5, 0.5, 0.5), windCharge, false);
        assertEquals(0.5, stepped.y, 1.0E-9);
        assertEquals(0.5, stepped.x, 1.0E-9);
    }

    @Test
    void onlyTheFishingBobberStopsOnWater() {

        assertTrue(TrajectorySim.infoForTest("fishing").stopsOnFluid(), "fishing rod must clip against fluids");
        assertTrue(!TrajectorySim.infoForTest("generic").stopsOnFluid(), "thrown items pass through water");
        assertTrue(!TrajectorySim.infoForTest("bow").stopsOnFluid(), "arrows pass through water");
    }

    @Test
    void multishotSpreadMatchesVanillaTripleShot() {
        assertArrayEqualsF(new float[]{0.0F}, TrajectorySim.yawOffsets(1));
        assertArrayEqualsF(new float[]{-10.0F, 0.0F, 10.0F}, TrajectorySim.yawOffsets(3));
        float[] five = TrajectorySim.yawOffsets(5);
        assertEquals(5, five.length);
        assertEquals(-10.0F, five[0], 1.0E-5);
        assertEquals(10.0F, five[4], 1.0E-5);
        assertEquals(0.0F, five[2], 1.0E-5);
    }

    private static void assertArrayEqualsF(float[] expected, float[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual[i], 1.0E-5);
    }
}
