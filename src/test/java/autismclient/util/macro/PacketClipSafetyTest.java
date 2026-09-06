package autismclient.util.macro;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PacketClipSafetyTest {
    @Test
    void deepDescentResetsFallDistanceBeforeLanding() {
        Vec3 from = new Vec3(2.0D, 80.0D, 4.0D);
        Vec3 target = new Vec3(12.0D, 50.0D, 14.0D);
        List<PacketClipSafety.Step> steps = PacketClipSafety.positionSteps(from, target, true);
        assertEquals(3, steps.size());
        assertEquals(target, steps.get(0).position());
        assertFalse(steps.get(0).onGround());
        assertEquals(target.y + 0.0625D, steps.get(1).position().y, 1.0E-9D);
        assertFalse(steps.get(1).onGround());
        assertEquals(target, steps.get(2).position());
        assertTrue(steps.get(2).onGround());

        double y = from.y;
        double fallDistance = 0.0D;
        double landingDistance = 0.0D;
        for (PacketClipSafety.Step step : steps) {
            double dy = step.position().y - y;
            if (dy < 0.0D) fallDistance -= dy;
            if (step.onGround()) {
                landingDistance = fallDistance;
                fallDistance = 0.0D;
            }
            if (dy > 0.0D) fallDistance = 0.0D;
            y = step.position().y;
        }
        assertEquals(0.0625D, landingDistance, 1.0E-9D);
        assertTrue(landingDistance < PacketClipSafety.DIRECT_FALL_LIMIT);
    }

    @Test
    void shallowMovementNeedsOnePacket() {
        List<PacketClipSafety.Step> steps = PacketClipSafety.positionSteps(
            new Vec3(0.0D, 64.0D, 0.0D), new Vec3(4.0D, 62.0D, 3.0D), true);
        assertEquals(1, steps.size());
        assertTrue(steps.getFirst().onGround());
    }
}
