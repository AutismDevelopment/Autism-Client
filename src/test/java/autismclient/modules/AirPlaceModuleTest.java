package autismclient.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AirPlaceModuleTest {
    @Test
    void activatesOnlyForMissedRaycasts() {
        BlockPos pos = new BlockPos(2, 70, -4);
        BlockHitResult miss = BlockHitResult.miss(Vec3.atCenterOf(pos), Direction.UP, pos);
        BlockHitResult block = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);

        assertTrue(AirPlaceModule.usesAirMiss(miss, miss));
        assertFalse(AirPlaceModule.usesAirMiss(block, miss));
        assertFalse(AirPlaceModule.usesAirMiss(miss, block));
    }

    @Test
    void everyTipUsesFourWordsMaximum() {
        assertTrue(words(AirPlaceModule.MODULE_DESCRIPTION) <= 4);
        AirPlaceModule.settingTips().forEach(tip ->
            assertTrue(words(tip) <= 4, "Tip was too long: " + tip));
    }

    private static int words(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }
}
