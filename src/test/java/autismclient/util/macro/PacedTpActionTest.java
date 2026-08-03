package autismclient.util.macro;

import autismclient.gui.macro.editor.ActionFieldRegistry;
import autismclient.util.multi.PacketTeleportController;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

final class PacedTpActionTest {
    @Test
    void settingsRoundTripAndBuildMixedRelativeCoordinates() {
        PacedTpAction action = new PacedTpAction();
        action.x = 10.0D;
        action.y = 72.5D;
        action.z = -4.0D;
        action.relativeX = true;
        action.relativeZ = true;
        action.maxPackets = 37;
        action.pauseMs = 1250;

        PacedTpAction restored = new PacedTpAction();
        restored.fromTag(action.toTag());
        assertEquals(MacroActionType.TP, restored.getType());
        assertTrue(restored.relativeX);
        assertFalse(restored.relativeY);
        assertTrue(restored.relativeZ);
        assertEquals(37, restored.maxPackets);
        assertEquals(1250, restored.pauseMs);

        PacketTeleportController.CommandRequest request = PacketTeleportController.parse(
            restored.commandArguments(), new Vec3(5.0D, 64.0D, 9.0D), 20, 500);
        assertEquals(new Vec3(15.0D, 72.5D, 5.0D), request.destination());
    }

    @Test
    void packetEditorExposesEveryTpControl() {
        Set<String> keys = ActionFieldRegistry.get(MacroActionType.TP).fields().stream()
            .map(field -> field.key()).collect(Collectors.toSet());
        assertEquals(Set.of("x", "y", "z", "relativeX", "relativeY", "relativeZ",
            "maxPackets", "pauseMs"), keys);
    }
}
