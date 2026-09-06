package autismclient.util.multi;

import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiEntityTrackerTest {
    @Test
    void relativeMovementUsesTheProtocolBaseWithoutRenderInterpolation() {
        MultiEntityTracker tracker = new MultiEntityTracker();
        tracker.put(7, UUID.randomUUID(), "player", 10.0, 64.0, -3.0, Vec3.ZERO,
            0.0F, 0.0F, 0.0F, false, -1);

        tracker.moveRelative(7, (short) 2048, (short) -4096, (short) 1024,
            true, 90.0F, 15.0F, true, true);
        tracker.moveRelative(7, (short) 2048, (short) 0, (short) 0,
            true, 0.0F, 0.0F, false, true);

        MultiEntityTracker.State state = tracker.state(7);
        assertEquals(new Vec3(11.0, 63.0, -2.75), state.position());
        assertEquals(90.0F, state.yRot());
        assertEquals(15.0F, state.xRot());
        assertEquals(true, state.onGround());
    }

    @Test
    void rotationOnlyPacketDoesNotMoveTheEntity() {
        MultiEntityTracker tracker = new MultiEntityTracker();
        Vec3 start = new Vec3(1.25, 70.0, 9.5);
        tracker.put(2, "player", start.x, start.y, start.z);

        tracker.moveRelative(2, (short) 300, (short) 300, (short) 300,
            false, -45.0F, 22.5F, true, false);

        assertEquals(start, tracker.state(2).position());
        assertEquals(-45.0F, tracker.state(2).yRot());
    }

    @Test
    void teleportHonorsMixedRelativePositionVelocityAndRotation() {
        MultiEntityTracker tracker = new MultiEntityTracker();
        tracker.put(9, UUID.randomUUID(), "player", 10.0, 20.0, 30.0, new Vec3(1.0, 2.0, 3.0),
            40.0F, 10.0F, 50.0F, true, -1);
        PositionMoveRotation change = new PositionMoveRotation(
            new Vec3(2.0, 80.0, -5.0), new Vec3(0.5, 0.0, -1.0), 15.0F, -5.0F);

        tracker.teleport(9, change,
            Set.of(Relative.X, Relative.Z, Relative.DELTA_X, Relative.DELTA_Z, Relative.Y_ROT), false);

        MultiEntityTracker.State state = tracker.state(9);
        assertEquals(new Vec3(12.0, 80.0, 25.0), state.position());
        assertEquals(new Vec3(1.5, 0.0, 2.0), state.movement());
        assertEquals(55.0F, state.yRot());
        assertEquals(-5.0F, state.xRot());
    }

    @Test
    void fishingHookKeepsOwnerAndDecodesHookedEntityMetadata() {
        MultiEntityTracker tracker = new MultiEntityTracker();
        tracker.put(100, UUID.randomUUID(), "fishing_bobber", 1.0, 2.0, 3.0, Vec3.ZERO,
            0.0F, 0.0F, 0.0F, false, 41);

        tracker.fishingHookTarget(100, 78);

        assertEquals(41, tracker.state(100).ownerId());
        assertEquals(77, tracker.state(100).hookedId());
        tracker.fishingHookTarget(100, 0);
        assertEquals(-1, tracker.state(100).hookedId());
    }

    @Test
    void absoluteSyncAndVelocityReplaceThePublishedValues() {
        MultiEntityTracker tracker = new MultiEntityTracker();
        tracker.put(3, "zombie", 0.0, 0.0, 0.0);
        tracker.sync(3, new PositionMoveRotation(new Vec3(4.0, 5.0, 6.0),
            new Vec3(0.1, 0.2, 0.3), 30.0F, 12.0F), true);
        tracker.motion(3, new Vec3(-0.5, 0.4, 0.25));
        tracker.headRotation(3, 75.0F);

        MultiEntityTracker.State state = tracker.state(3);
        assertEquals(new Vec3(4.0, 5.0, 6.0), state.position());
        assertEquals(new Vec3(-0.5, 0.4, 0.25), state.movement());
        assertEquals(75.0F, state.headYRot());
        assertEquals(true, state.onGround());
    }

    @Test
    void uuidMapsRenderedEntityToTheCurrentBotConnectionId() {
        MultiEntityTracker tracker = new MultiEntityTracker();
        UUID uuid = UUID.randomUUID();
        tracker.put(21, uuid, "player", 1.0, 2.0, 3.0, Vec3.ZERO,
            0.0F, 0.0F, 0.0F, true, -1);

        assertEquals(21, tracker.state(uuid).id());

        tracker.put(44, uuid, "player", 4.0, 5.0, 6.0, Vec3.ZERO,
            0.0F, 0.0F, 0.0F, true, -1);
        assertEquals(44, tracker.state(uuid).id());
        assertEquals(null, tracker.state(21));

        tracker.remove(44);
        assertEquals(null, tracker.state(uuid));
    }

    @Test
    void nearbyArmorStandSurvivesAFullTrackerOfLobbyNoise() {
        MultiEntityTracker tracker = new MultiEntityTracker();
        for (int id = 0; id < 512; id++) {
            tracker.put(id, UUID.randomUUID(), "minecraft:item", 100.0 + id, 64.0, 0.0,
                Vec3.ZERO, 0.0F, 0.0F, 0.0F, true, -1, Vec3.ZERO);
        }

        tracker.put(900, UUID.randomUUID(), "minecraft:armor_stand", 2.0, 64.0, 0.0,
            Vec3.ZERO, 0.0F, 0.0F, 0.0F, true, -1, new Vec3(2.0, 64.0, 0.0));

        assertNotNull(tracker.state(900));
    }

    @Test
    void nearerInteractionHitboxReplacesADistantOneAtTheCap() {
        MultiEntityTracker tracker = new MultiEntityTracker();
        Vec3 focus = new Vec3(0.0, 64.0, 0.0);
        for (int id = 0; id < 512; id++) {
            tracker.put(id, UUID.randomUUID(), "minecraft:armor_stand", 100.0 + id, 64.0, 0.0,
                Vec3.ZERO, 0.0F, 0.0F, 0.0F, true, -1, focus);
        }

        tracker.put(901, UUID.randomUUID(), "minecraft:interaction", 1.0, 64.0, 0.0,
            Vec3.ZERO, 0.0F, 0.0F, 0.0F, true, -1, focus);

        assertNotNull(tracker.state(901));
    }

    @Test
    void botOnlyArmorStandAndInteractionHaveClickableRaycastBoxes() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        MultiEntityTracker tracker = new MultiEntityTracker();
        tracker.put(90, UUID.randomUUID(), "minecraft:armor_stand", 2.0, 64.0, 0.0,
            Vec3.ZERO, 0.0F, 0.0F, 0.0F, true, -1);
        tracker.put(91, UUID.randomUUID(), "minecraft:interaction", 4.0, 64.0, 0.0,
            Vec3.ZERO, 0.0F, 0.0F, 0.0F, true, -1);

        assertTrue(MultiPilot.isPotentialBotOnlyTarget("minecraft:armor_stand"));
        assertTrue(MultiPilot.isPotentialBotOnlyTarget("minecraft:interaction"));
        assertFalse(MultiPilot.isPotentialBotOnlyTarget("minecraft:item"));
        net.minecraft.world.phys.AABB armorStandBox = MultiPilot.trackedEntityBox(tracker.state(90));
        assertTrue(armorStandBox.getYsize() > 1.0D);
        assertTrue(armorStandBox.clip(new Vec3(0.0D, 65.0D, 0.0D), new Vec3(5.0D, 65.0D, 0.0D)).isPresent());
        assertEquals(1.0D, MultiPilot.trackedEntityBox(tracker.state(91)).getXsize());
        assertEquals(1.0D, MultiPilot.trackedEntityBox(tracker.state(91)).getYsize());
    }

    @Test
    void povArmorStandAimToleranceCatchesANearMissWithoutBecomingUnlimited() {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            2.75D, 64.0D, 0.55D, 3.25D, 65.975D, 1.05D);
        Vec3 eye = new Vec3(0.0D, 65.62D, 0.0D);
        Vec3 look = new Vec3(1.0D, 0.0D, 0.0D);

        assertTrue(box.clip(eye, eye.add(look.scale(3.75D))).isEmpty());
        assertNotNull(MultiPilot.povAimAssistLocation(box, eye, look, 3.75D, 0.85D));
        assertEquals(null, MultiPilot.povAimAssistLocation(box, eye, look, 3.75D, 0.25D));
    }
}
