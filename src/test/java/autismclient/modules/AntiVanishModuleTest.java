package autismclient.modules;

import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AntiVanishModuleTest {
    @Test
    void compactsHudReasons() {
        assertEquals("Staff", AntiVanishModule.compactHudName(new AntiVanishModule.HudEntry("CRITICAL", "Sound + Block", 100)));
        assertEquals("WATCH", AntiVanishModule.compactHudReason(new AntiVanishModule.HudEntry("CRITICAL", "Sound + Block", 100)));
        assertEquals("Rank", AntiVanishModule.compactHudReason(new AntiVanishModule.HudEntry("Alice", "Rank Detection: admin", 10)));
        assertEquals("Sound", AntiVanishModule.compactHudReason(new AntiVanishModule.HudEntry("Unknown", "Suspicious Sound: step", 14)));
    }

    @Test
    void anonymousBlockEvidenceUsesLocation() {
        assertEquals("5m N", AntiVanishModule.blockEvidenceSubject("", "5m N"));
        assertEquals("HiddenStaff", AntiVanishModule.blockEvidenceSubject("HiddenStaff", "5m N"));
    }

    @Test
    void superVanishPlacementUsesMissingSoundAsEvidence() {
        assertTrue(AntiVanishModule.anonymousPlacementReady(
            "minecraft:air", "minecraft:stone", false, true));
        assertFalse(AntiVanishModule.anonymousPlacementReady(
            "minecraft:air", "minecraft:stone", true, true));
        assertFalse(AntiVanishModule.anonymousPlacementReady(
            "minecraft:air", "minecraft:stone", false, false));
    }

    @Test
    void superVanishBreakUsesMissingEffectAsEvidence() {
        assertTrue(AntiVanishModule.anonymousBreakReady(
            "minecraft:stone", "minecraft:air", false, true));
        assertFalse(AntiVanishModule.anonymousBreakReady(
            "minecraft:stone", "minecraft:air", true, true));
        assertFalse(AntiVanishModule.anonymousBreakReady(
            "minecraft:stone", "minecraft:air", false, false));
    }

    @Test
    void selfMultiBlockFootprintsCoverPairedHalves() {
        Map<Long, Long> targets = new HashMap<>();
        BlockPos origin = new BlockPos(10, 64, 10);
        AntiVanishModule.markSelfMultiBlockFootprint(origin, "oak_door", targets, 100L, false);
        assertTrue(targets.containsKey(origin.asLong()));
        assertTrue(targets.containsKey(origin.above().asLong()));
        assertTrue(targets.containsKey(origin.below().asLong()));

        targets.clear();
        AntiVanishModule.markSelfMultiBlockFootprint(origin, "red_bed", targets, 100L, false);
        assertTrue(targets.containsKey(origin.offset(1, 0, 0).asLong()));
        assertTrue(targets.containsKey(origin.offset(0, 0, -1).asLong()));
    }

    @Test
    void silentTabRemovalStillProducesVisibleEvidence() {
        assertEquals(70, AntiVanishModule.silentTabRemovalScore(false, false));
        assertEquals(100, AntiVanishModule.silentTabRemovalScore(true, false));
        assertEquals(100, AntiVanishModule.silentTabRemovalScore(false, true));
        assertTrue(AntiVanishModule.tabDepartureReason("Vanish Event: silent TAB disappearance"));
        assertTrue(AntiVanishModule.tabDepartureReason("Vanish Event: staff left TAB silently"));
        assertFalse(AntiVanishModule.tabDepartureReason("Rank Detection: admin"));
    }
}
