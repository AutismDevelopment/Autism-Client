package autismclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AntiVanishHeuristicsTest {
    @Test
    void ignoresExplosionAndNaturalBlockChanges() {
        assertFalse(AntiVanishHeuristics.potentialInteractiveBlock("minecraft:air"));
        assertFalse(AntiVanishHeuristics.potentialInteractiveBlock("minecraft:dirt"));
        assertFalse(AntiVanishHeuristics.potentialInteractiveBlock("minecraft:farmland"));
        assertFalse(AntiVanishHeuristics.potentialInteractiveBlock("minecraft:wheat"));
        assertFalse(AntiVanishHeuristics.potentialInteractiveBlock("minecraft:grass_block"));
    }

    @Test
    void ignoresGrassDirtSpreadButNotRealPlacements() {

        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:dirt"));
        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:grass_block"));

        assertFalse(AntiVanishHeuristics.naturalBlockNoise("minecraft:stone"));
        assertFalse(AntiVanishHeuristics.naturalBlockNoise("minecraft:cobblestone"));
        assertFalse(AntiVanishHeuristics.naturalBlockNoise("minecraft:coarse_dirt"));
        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:dirt_path"));
    }

    @Test
    void rejectsNaturalAndResyncedPlacementTransitions() {
        assertFalse(AntiVanishHeuristics.crediblePlacementTransition(
            "minecraft:dirt", "minecraft:grass_block", false));
        assertFalse(AntiVanishHeuristics.crediblePlacementTransition(
            "minecraft:netherrack", "minecraft:crimson_nylium", false));
        assertFalse(AntiVanishHeuristics.crediblePlacementTransition(
            "minecraft:nether_wart", "minecraft:nether_wart", true));
        assertFalse(AntiVanishHeuristics.crediblePlacementTransition(
            "minecraft:air", "minecraft:water", true));
        assertTrue(AntiVanishHeuristics.crediblePlacementTransition(
            "minecraft:air", "minecraft:cobblestone", true));
    }

    @Test
    void anonymousPlacementRequiresAirToStableBlock() {
        assertTrue(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:cobblestone"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:cave_air", "minecraft:netherrack"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:grass_block"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:dirt"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:crimson_nylium"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:red_sandstone"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:mossy_cobblestone"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:redstone_block"));

        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:water", "minecraft:cobblestone"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:lava", "minecraft:obsidian"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:short_grass", "minecraft:stone"));
    }

    @Test
    void anonymousPlacementRejectsGrowthFallingAndMechanismStates() {

        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:dirt", "minecraft:grass_block"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:netherrack", "minecraft:crimson_nylium"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:nether_wart"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:sand"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:concrete_powder"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:moving_piston"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:water"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:anvil"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousPlacementTransition(
            "minecraft:air", "minecraft:scaffolding"));
    }

    @Test
    void placementSoundMustMatchExactly() {
        assertTrue(AntiVanishHeuristics.matchingPlaceSound(
            "minecraft:block.stone.place", "minecraft:block.stone.place"));
        assertFalse(AntiVanishHeuristics.matchingPlaceSound(
            "minecraft:block.stone.place", "minecraft:block.grass.place"));
        assertFalse(AntiVanishHeuristics.matchingPlaceSound(
            "minecraft:block.stone.place", "minecraft:block.stone.fall"));
    }

    @Test
    void evidenceAcceptsEitherPacketOrder() {
        assertTrue(AntiVanishHeuristics.sameEvidenceWindow(1_000L, 1_600L, 700L));
        assertTrue(AntiVanishHeuristics.sameEvidenceWindow(1_600L, 1_000L, 700L));
        assertTrue(AntiVanishHeuristics.matchingBreakEvidence(
            "minecraft:stone", 2_000L, "minecraft:stone", 2_800L, 900L));
        assertTrue(AntiVanishHeuristics.matchingBreakEvidence(
            "minecraft:stone", 2_800L, "minecraft:stone", 2_000L, 900L));
        assertFalse(AntiVanishHeuristics.matchingBreakEvidence(
            "minecraft:stone", 2_000L, "minecraft:dirt", 2_100L, 900L));

        assertTrue(AntiVanishHeuristics.matchingBreakEffect(
            "minecraft:grass_block", 2_000L, "minecraft:grass_block", 2_100L, 900L));
        assertFalse(AntiVanishHeuristics.matchingBreakEvidence(
            "minecraft:grass_block", 2_000L, "minecraft:grass_block", 2_100L, 900L));
    }

    @Test
    void breakFilterRejectsEnvironmentalBlocks() {
        assertFalse(AntiVanishHeuristics.credibleBreakBlock("minecraft:grass_block"));
        assertFalse(AntiVanishHeuristics.credibleBreakBlock("minecraft:farmland"));
        assertFalse(AntiVanishHeuristics.credibleBreakBlock("minecraft:crimson_nylium"));
        assertFalse(AntiVanishHeuristics.credibleBreakBlock("minecraft:sand"));
        assertTrue(AntiVanishHeuristics.credibleBreakBlock("minecraft:oak_door"));
        assertTrue(AntiVanishHeuristics.credibleBreakBlock("minecraft:stone"));
    }

    @Test
    void anonymousBreakRequiresStableBlockToAir() {
        assertTrue(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:stone", "minecraft:air"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:netherrack", "minecraft:cave_air"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:grass_block", "minecraft:air"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:dirt", "minecraft:air"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:warped_nylium", "minecraft:air"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:sandstone", "minecraft:air"));
        assertTrue(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:soul_sand", "minecraft:air"));

        assertFalse(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:stone", "minecraft:water"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:stone", "minecraft:lava"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:air", "minecraft:air"));
    }

    @Test
    void anonymousBreakRejectsGrowthFallingAndMechanismStates() {
        assertFalse(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:nether_wart", "minecraft:air"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:sand", "minecraft:air"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:gravel", "minecraft:air"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:piston_head", "minecraft:air"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:water", "minecraft:air"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:anvil", "minecraft:air"));
        assertFalse(AntiVanishHeuristics.credibleAnonymousBreakTransition(
            "minecraft:dragon_egg", "minecraft:air"));
    }

    @Test
    void rejectsNetherGrowthAndWorldMechanics() {
        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:crimson_fungus"));
        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:warped_roots"));
        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:nether_sprouts"));
        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:crimson_nylium"));
        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:nether_wart"));
        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:resin_clump"));
        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:moving_piston"));
        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:nether_portal"));
    }

    @Test
    void keepsActualInteractionBlocks() {
        assertTrue(AntiVanishHeuristics.blockEventInteraction("minecraft:chest"));
        assertTrue(AntiVanishHeuristics.blockEventInteraction("minecraft:shulker_box"));
        assertTrue(AntiVanishHeuristics.blockStateInteraction("minecraft:oak_door"));
        assertTrue(AntiVanishHeuristics.blockStateInteraction("minecraft:lever"));
    }

    @Test
    void ignoresFootstepsMobAndAmbientSounds() {

        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:block.stone.step"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:block.grass.step"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:entity.cow.step"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:entity.cow.ambient"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:entity.zombie.ambient"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:entity.generic.explode"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:block.crop.break"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:weather.rain"));
    }

    @Test
    void flagsOnlyPlayerInteractionSounds() {
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.chest.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.chest.close"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.barrel.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.ender_chest.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.shulker_box.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.wooden_door.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.wooden_trapdoor.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.fence_gate.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.stone_button.click_on"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.lever.click"));
    }

    @Test
    void excludesGenericDeathParticles() {
        assertFalse(AntiVanishHeuristics.suspiciousParticle("minecraft:poof"));
        assertTrue(AntiVanishHeuristics.suspiciousParticle("minecraft:crit"));
        assertTrue(AntiVanishHeuristics.suspiciousParticle("minecraft:smoke"));
    }
}
