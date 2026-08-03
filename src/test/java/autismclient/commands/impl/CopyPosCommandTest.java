package autismclient.commands.impl;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CopyPosCommandTest {
    @Test
    void copiesWholePasteReadyCoordinates() {
        assertEquals("12 65 -4", CopyPosCommand.format(new Vec3(12.5D, 64.9D, -3.125D)));
        assertEquals("30000000 0 -1", CopyPosCommand.format(new Vec3(30_000_000D, -0.0D, -0.000001D)));
    }

    @Test
    void fractionalStandingHeightRoundsUpInsteadOfIntoTheSupportingBlock() {
        assertEquals("10 65 20", CopyPosCommand.format(new Vec3(10.5D, 64.5D, 20.5D)));
        assertEquals("10 65 20", CopyPosCommand.format(new Vec3(10.5D, 65.00000001D, 20.5D)),
            "tiny floating drift above a full block must not add a whole extra block");
        assertEquals("-2 -63 -4", CopyPosCommand.format(new Vec3(-1.5D, -63.5D, -3.5D)));
    }
}
