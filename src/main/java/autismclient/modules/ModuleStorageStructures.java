package autismclient.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.List;
import java.util.Set;

public final class ModuleStorageStructures {

    public record Structure(String settingId, String label, Set<Block> markers) {
    }

    private static Structure of(String settingId, String label, Block... markers) {
        return new Structure(settingId, label, Set.of(markers));
    }

    public static final List<Structure> ALL = List.of(

        of("hide-trial-chambers", "Hide Trial Chambers",
            Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.CHISELED_TUFF, Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF),
        of("hide-ancient-cities", "Hide Ancient Cities",
            Blocks.REINFORCED_DEEPSLATE, Blocks.SCULK_SHRIEKER, Blocks.SCULK_CATALYST,
            Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS),
        of("hide-monuments", "Hide Ocean Monuments",
            Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN),
        of("hide-fortresses", "Hide Nether Fortresses",
            Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_FENCE),
        of("hide-bastions", "Hide Bastions",
            Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE),
        of("hide-end-cities", "Hide End Cities",
            Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR, Blocks.END_STONE_BRICKS),
        of("hide-strongholds", "Hide Strongholds",
            Blocks.END_PORTAL_FRAME, Blocks.INFESTED_STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS),
        of("hide-mineshafts", "Hide Mineshafts",
            Blocks.COBWEB, Blocks.RAIL, Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.OAK_FENCE),
        of("hide-dungeons", "Hide Dungeons",
            Blocks.SPAWNER, Blocks.MOSSY_COBBLESTONE),
        of("hide-desert-temples", "Hide Desert Temples",
            Blocks.CHISELED_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.TNT),
        of("hide-jungle-temples", "Hide Jungle Temples",
            Blocks.TRIPWIRE_HOOK, Blocks.CHISELED_STONE_BRICKS),
        of("hide-igloos", "Hide Igloos",
            Blocks.SNOW_BLOCK, Blocks.PACKED_ICE),
        of("hide-mansions", "Hide Woodland Mansions",
            Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_FENCE),
        of("hide-villages", "Hide Villages",
            Blocks.BELL, Blocks.COMPOSTER, Blocks.LECTERN, Blocks.FLETCHING_TABLE,
            Blocks.CARTOGRAPHY_TABLE, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.LOOM, Blocks.HAY_BLOCK),
        of("hide-swamp-huts", "Hide Swamp Huts",
            Blocks.CAULDRON, Blocks.SPRUCE_PLANKS, Blocks.CRAFTING_TABLE));

    private ModuleStorageStructures() {
    }

    public static long enabledMask(Module module) {
        if (module == null) return 0L;
        long mask = 0L;
        for (int i = 0; i < ALL.size(); i++) {
            if (module.value(ALL.get(i).settingId()).equals("true")) mask |= 1L << i;
        }
        return mask;
    }

    public static boolean hidden(LevelChunk chunk, BlockPos pos, long mask,
                                 java.util.Map<Integer, Long> sectionCache) {
        if (mask == 0L || chunk == null || pos == null) return false;
        int index = chunk.getSectionIndex(pos.getY());
        Long cached = sectionCache.get(index);
        long found = cached != null ? cached : matchSections(chunk, index, mask);
        if (cached == null) sectionCache.put(index, found);
        return (found & mask) != 0L;
    }

    private static long matchSections(LevelChunk chunk, int index, long mask) {
        long found = 0L;
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = index - 1; i <= index + 1; i++) {
            if (i < 0 || i >= sections.length) continue;
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;
            found |= match(section, mask);
        }
        return found;
    }

    private static long match(LevelChunkSection section, long mask) {
        long found = 0L;
        for (int i = 0; i < ALL.size(); i++) {
            if ((mask & (1L << i)) == 0L) continue;
            Set<Block> markers = ALL.get(i).markers();
            if (section.maybeHas(state -> containsBlock(markers, state))) found |= 1L << i;
        }
        return found;
    }

    private static boolean containsBlock(Set<Block> markers, BlockState state) {
        return state != null && markers.contains(state.getBlock());
    }
}
