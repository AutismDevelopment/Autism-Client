package autismclient.util.oresim;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutismOreSimOreTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void everyPickerBlockResolvesAndMapsToAFamily() {
        Set<String> seen = new LinkedHashSet<>();
        for (String id : AutismOreSimOre.ORE_SIM_BLOCK_IDS) {
            assertTrue(seen.add(id), "duplicate entry " + id);
            Identifier parsed = Identifier.tryParse(id);
            assertNotNull(parsed, "unparseable id " + id);
            Block block = BuiltInRegistries.BLOCK.getOptional(parsed).orElse(null);
            assertNotNull(block, "unknown block " + id);
            assertTrue(AutismOreSimOre.isOreSimBlock(block), id + " rejected by the picker filter");
            assertNotNull(AutismOreSimOre.familyOf(id), id + " maps to no ore family");
        }
    }

    @Test
    void everyOreFamilyIsReachableFromThePickerList() {
        for (AutismOreSimOre.Kind kind : AutismOreSimOre.Kind.values()) {
            boolean reachable = false;
            for (String id : AutismOreSimOre.ORE_SIM_BLOCK_IDS) {
                if (AutismOreSimOre.familyOf(id) == kind) {
                    reachable = true;
                    break;
                }
            }
            assertTrue(reachable, kind.id + " has no block in the picker list");
        }
    }

    @Test
    void rawOreBlocksArePickerTargetsWithTheCorrectFamilies() {
        assertPickerFamily("minecraft:raw_iron_block", AutismOreSimOre.Kind.IRON);
        assertPickerFamily("minecraft:raw_copper_block", AutismOreSimOre.Kind.COPPER);
    }

    @Test
    void blocksOutsideTheListAreRejectedByThePicker() {

        for (String id : new String[]{"minecraft:chest", "minecraft:stone", "minecraft:copper_block",
            "minecraft:diamond_block", "minecraft:spawner"}) {
            Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(id)).orElse(null);
            assertNotNull(block, "unknown block " + id);
            assertTrue(!AutismOreSimOre.isOreSimBlock(block), id + " should not be offered in OreSim");
        }
    }

    private static void assertPickerFamily(String id, AutismOreSimOre.Kind expected) {
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(id)).orElse(null);
        assertNotNull(block, "unknown block " + id);
        assertTrue(AutismOreSimOre.isOreSimBlock(block), id + " rejected by the picker filter");
        assertEquals(expected, AutismOreSimOre.familyOf(id), id + " maps to the wrong ore family");
    }
}
