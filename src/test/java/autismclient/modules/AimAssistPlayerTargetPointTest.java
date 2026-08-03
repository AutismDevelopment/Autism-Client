package autismclient.modules;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AimAssistPlayerTargetPointTest {
    private static final double EPSILON = 1.0E-9;
    private static final AABB STANDING_PLAYER = new AABB(-0.3, 10.0, -0.3, 0.3, 11.8, 0.3);

    @Test
    void everyPlayerTargetPointUsesModelCenterLine() {
        Vec3 eyes = new Vec3(4.0, 11.62, 4.0);

        for (String targetPoint : new String[] {"Nearest", "Center", "Head", "Body", "Feet"}) {
            Vec3 point = BuiltinModules.AimAssistModule.playerModelPoint(
                eyes,
                STANDING_PLAYER,
                0.0,
                11.62,
                0.0,
                targetPoint
            );
            assertEquals(0.0, point.x, EPSILON, targetPoint);
            assertEquals(0.0, point.z, EPSILON, targetPoint);
        }
    }

    @Test
    void nearestPlayerPointDoesNotSlideAcrossFacesAtDifferentAngles() {
        Vec3 east = BuiltinModules.AimAssistModule.playerModelPoint(
            new Vec3(4.0, 11.2, 0.0), STANDING_PLAYER, 0.0, 11.62, 0.0, "Nearest");
        Vec3 diagonal = BuiltinModules.AimAssistModule.playerModelPoint(
            new Vec3(4.0, 11.2, 4.0), STANDING_PLAYER, 0.0, 11.62, 0.0, "Nearest");
        Vec3 west = BuiltinModules.AimAssistModule.playerModelPoint(
            new Vec3(-4.0, 11.2, 0.0), STANDING_PLAYER, 0.0, 11.62, 0.0, "Nearest");

        assertEquals(east, diagonal);
        assertEquals(east, west);
        assertEquals(new Vec3(0.0, 11.2, 0.0), east);
    }

    @Test
    void playerPointsStayInsideShortPoseBoxes() {
        AABB swimmingPlayer = new AABB(-0.3, 20.0, -0.3, 0.3, 20.6, 0.3);

        Vec3 head = BuiltinModules.AimAssistModule.playerModelPoint(
            new Vec3(0.0, 30.0, 0.0), swimmingPlayer, 0.0, 20.4, 0.0, "Head");
        Vec3 feet = BuiltinModules.AimAssistModule.playerModelPoint(
            new Vec3(0.0, 30.0, 0.0), swimmingPlayer, 0.0, 20.4, 0.0, "Feet");
        Vec3 nearest = BuiltinModules.AimAssistModule.playerModelPoint(
            new Vec3(0.0, 30.0, 0.0), swimmingPlayer, 0.0, 20.4, 0.0, "Nearest");

        assertEquals(20.4, head.y, EPSILON);
        assertEquals(20.06, feet.y, EPSILON);
        assertEquals(20.54, nearest.y, EPSILON);
    }

    @Test
    void horizontalCenterComesFromPlayerModelNotHitboxCenter() {
        AABB offsetHitbox = new AABB(2.0, 10.0, 5.0, 2.6, 11.8, 5.6);

        Vec3 point = BuiltinModules.AimAssistModule.playerModelPoint(
            new Vec3(8.0, 11.0, 8.0), offsetHitbox, 2.1, 11.62, 5.2, "Center");

        assertEquals(2.1, point.x, EPSILON);
        assertEquals(5.2, point.z, EPSILON);
    }
}
