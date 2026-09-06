package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class RaycastAimTest {
    private static List<Supplier<MacroAction>> raycastActions() {
        return List.of(
            PlaceAction::new,
            BreakAction::new,
            InstaBreakAction::new,
            InteractEntityAction::new,
            OpenContainerAction::new
        );
    }

    @Test
    void everyInteractionActionOffersRaycastAndDefaultsOff() {
        for (Supplier<MacroAction> factory : raycastActions()) {
            MacroAction action = factory.get();
            assertTrue(action instanceof RaycastAim, action.getClass().getSimpleName() + " should offer Raycast");
            assertFalse(((RaycastAim) action).isRaycast(),
                action.getClass().getSimpleName() + " must default to off");
        }
    }

    @Test
    void raycastRoundTripsThroughNbt() {
        for (Supplier<MacroAction> factory : raycastActions()) {
            MacroAction action = factory.get();
            ((RaycastAim) action).setRaycast(true);

            MacroAction restored = factory.get();
            restored.fromTag(action.toTag());

            assertTrue(((RaycastAim) restored).isRaycast(),
                action.getClass().getSimpleName() + " should keep Raycast on");
        }
    }

    @Test
    void oldSavesWithoutTheKeyStayOff() {
        for (Supplier<MacroAction> factory : raycastActions()) {
            MacroAction action = factory.get();
            net.minecraft.nbt.CompoundTag legacy = new net.minecraft.nbt.CompoundTag();
            legacy.putString("type", action.getType().name());
            action.fromTag(legacy);

            assertFalse(((RaycastAim) action).isRaycast(),
                action.getClass().getSimpleName() + " must stay off for old saves");
        }
    }

    @Test
    void aimIsNotHeldUntilAnActionAsksForIt() {
        MacroRaycastAim.release();
        assertFalse(MacroRaycastAim.isActive());
        assertNull(MacroRaycastAim.active());

        MacroRaycastAim.hold(new autismclient.util.AutismRotationUtil.Rotation(90.0f, 45.0f));
        assertTrue(MacroRaycastAim.isActive());
        assertEquals(90.0f, MacroRaycastAim.active().yaw());
        assertEquals(45.0f, MacroRaycastAim.active().pitch());

        MacroRaycastAim.release();
        assertFalse(MacroRaycastAim.isActive());
    }

    @Test
    void aBlockActionReportsTheBlockItself() {

        PlaceAction place = new PlaceAction();
        place.blockPos = new net.minecraft.core.BlockPos(10, 64, -3);

        RaycastAim.Target target = place.raycastTarget(null);

        assertEquals(new net.minecraft.core.BlockPos(10, 64, -3), target.block());
        assertNull(target.entity());
        assertNull(target.point());
    }

    @Test
    void aTargetIsNullRatherThanEmptyWhenThereIsNothingToAimAt() {
        assertNull(RaycastAim.Target.ofBlock(null));
        assertNull(RaycastAim.Target.ofEntity(null));
        assertNull(RaycastAim.Target.ofPoint(null));
    }
}
