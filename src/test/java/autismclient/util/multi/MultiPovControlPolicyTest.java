package autismclient.util.multi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultiPovControlPolicyTest {
    @Test
    void enteringPovStopsOnlyHorizontalMainVelocity() {
        Vec3 stopped = MultiPilot.stoppedMainVelocity(new Vec3(1.25D, -0.42D, -3.0D));
        assertEquals(0.0D, stopped.x);
        assertEquals(-0.42D, stopped.y);
        assertEquals(0.0D, stopped.z);
    }

    @Test
    void localAndControlledPlayersNeverUseBotTruthTranslation() {
        assertFalse(MultiPilot.shouldTranslateFromBotTruth(true, 12, 99));
        assertFalse(MultiPilot.shouldTranslateFromBotTruth(false, 99, 99));
        assertTrue(MultiPilot.shouldTranslateFromBotTruth(false, 12, 99));
    }

    @Test
    void protectedBlockPressUsesVanillaStartThenSwingOrder() {
        BlockPos pos = new BlockPos(4, 70, -3);
        List<Packet<?>> packets = MultiPilot.initialBlockPressPackets(pos, Direction.NORTH, 41);
        assertEquals(2, packets.size());
        assertInstanceOf(ServerboundPlayerActionPacket.class, packets.get(0));
        assertInstanceOf(ServerboundSwingPacket.class, packets.get(1));
        ServerboundPlayerActionPacket start = (ServerboundPlayerActionPacket) packets.get(0);
        assertEquals(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, start.getAction());
        assertEquals(pos, start.getPos());
        assertEquals(Direction.NORTH, start.getDirection());
        assertEquals(41, start.getSequence());
    }
}
