package autismclient.util;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutismServerRotationViewTest {
    @AfterEach
    void clearTracker() {
        AutismServerRotationView.reset();
    }

    @Test
    void tracksOnlyMovementPacketsThatActuallyCarryRotation() {
        AutismServerRotationView.onPacketWritten(
            new ServerboundMovePlayerPacket.Rot(135.0F, 42.0F, true, false));
        assertEquals(new AutismServerRotationView.Rotation(135.0F, 42.0F),
            AutismServerRotationView.currentRotation());

        AutismServerRotationView.onPacketWritten(
            new ServerboundMovePlayerPacket.Pos(new Vec3(4.0D, 70.0D, -2.0D), true, false));
        assertEquals(new AutismServerRotationView.Rotation(135.0F, 42.0F),
            AutismServerRotationView.currentRotation(),
            "position-only packets must preserve the rotation the server already holds");
    }

    @Test
    void killAuraStyleSilentRotationChangesOnlyTheRenderSnapshot() {
        AutismServerRotationView.onPacketWritten(
            new ServerboundMovePlayerPacket.Rot(-147.5F, 31.25F, true, false));
        LivingEntityRenderState renderState = new LivingEntityRenderState();
        renderState.bodyRot = 18.0F;
        renderState.yRot = 9.0F;
        renderState.xRot = -4.0F;

        AutismServerRotationView.applyRotation(
            renderState, AutismServerRotationView.currentRotation());

        assertEquals(-147.5F, renderState.bodyRot, 1.0E-6F);
        assertEquals(0.0F, renderState.yRot, 1.0E-6F);
        assertEquals(31.25F, renderState.xRot, 1.0E-6F);
    }

    @Test
    void sanitizesTheRenderPoseWithoutTouchingAnEntity() {
        AutismServerRotationView.update(725.0F, 95.0F);
        AutismServerRotationView.Rotation rotation = AutismServerRotationView.currentRotation();
        assertNotNull(rotation);
        assertEquals(5.0F, rotation.yaw(), 1.0E-6F);
        assertEquals(90.0F, rotation.pitch(), 1.0E-6F);

        LivingEntityRenderState state = new LivingEntityRenderState();
        state.bodyRot = -30.0F;
        state.yRot = 75.0F;
        state.xRot = -20.0F;
        AutismServerRotationView.applyRotation(state, rotation);
        assertEquals(5.0F, state.bodyRot, 1.0E-6F);
        assertEquals(0.0F, state.yRot, 1.0E-6F);
        assertEquals(90.0F, state.xRot, 1.0E-6F);
    }

    @Test
    void ignoresInvalidWireRotations() {
        AutismServerRotationView.update(20.0F, 10.0F);
        AutismServerRotationView.update(Float.NaN, 50.0F);
        assertEquals(new AutismServerRotationView.Rotation(20.0F, 10.0F),
            AutismServerRotationView.currentRotation());
    }

    @Test
    void renderGateIsStrictlyThirdPersonLocalCameraOnly() {
        assertTrue(AutismServerRotationView.shouldApply(true, true, true, false, false, true));
        assertFalse(AutismServerRotationView.shouldApply(true, true, true, false, false, false),
            "without a silent owner vanilla's already-interpolated render state must remain untouched");
        assertFalse(AutismServerRotationView.shouldApply(true, true, false, false, false, true));
        assertFalse(AutismServerRotationView.shouldApply(false, true, true, false, false, true));
        assertFalse(AutismServerRotationView.shouldApply(true, false, true, false, false, true));
        assertFalse(AutismServerRotationView.shouldApply(true, true, true, true, false, true));
        assertFalse(AutismServerRotationView.shouldApply(true, true, true, false, true, true));
    }

    @Test
    void interpolatesAcrossTheYawWrapByTheShortPath() {
        AutismServerRotationView.Rotation halfway = AutismServerRotationView.interpolate(
            new AutismServerRotationView.Rotation(179.0F, 10.0F),
            new AutismServerRotationView.Rotation(-179.0F, 30.0F),
            0.5F);

        assertEquals(-180.0F, halfway.yaw(), 1.0E-4F);
        assertEquals(20.0F, halfway.pitch(), 1.0E-4F);
    }

    @Test
    void completedEndpointDoesNotRewindWhenPartialTickResets() {
        AutismServerRotationView.Timeline timeline = new AutismServerRotationView.Timeline(
            new AutismServerRotationView.Rotation(10.0F, 5.0F),
            new AutismServerRotationView.Rotation(70.0F, 25.0F),
            40);

        assertEquals(new AutismServerRotationView.Rotation(40.0F, 15.0F),
            AutismServerRotationView.interpolatedRotation(timeline, 0.5F, 40));
        assertEquals(new AutismServerRotationView.Rotation(70.0F, 25.0F),
            AutismServerRotationView.interpolatedRotation(timeline, 0.0F, 41),
            "a tick without another look packet must hold the latest endpoint");
    }

    @Test
    void multiplePacketsInOneTickKeepOneInterpolationInterval() {
        AutismServerRotationView.updateAtTick(10.0F, 5.0F, 20);
        AutismServerRotationView.updateAtTick(40.0F, 15.0F, 21);
        AutismServerRotationView.updateAtTick(70.0F, 25.0F, 21);

        assertEquals(new AutismServerRotationView.Rotation(40.0F, 15.0F),
            AutismServerRotationView.interpolatedRotation(
                AutismServerRotationView.currentTimeline(), 0.5F, 21));
        assertEquals(new AutismServerRotationView.Rotation(70.0F, 25.0F),
            AutismServerRotationView.currentRotation());
    }
}
