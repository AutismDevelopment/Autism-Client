package autismclient.util;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class AutismFarmBlocks {

    private static final Set<Block> COLUMN_CROPS = identitySet(
        Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.BAMBOO);

    private static final Set<Block> HARVEST_ONLY = identitySet(
        Blocks.PUMPKIN, Blocks.MELON,
        Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.BAMBOO);

    private static final Map<Block, Item> SEED_BY_CROP = Map.ofEntries(
        Map.entry(Blocks.WHEAT, Items.WHEAT_SEEDS),
        Map.entry(Blocks.CARROTS, Items.CARROT),
        Map.entry(Blocks.POTATOES, Items.POTATO),
        Map.entry(Blocks.BEETROOTS, Items.BEETROOT_SEEDS),
        Map.entry(Blocks.NETHER_WART, Items.NETHER_WART),
        Map.entry(Blocks.COCOA, Items.COCOA_BEANS),
        Map.entry(Blocks.SWEET_BERRY_BUSH, Items.SWEET_BERRIES));

    private static final Set<Block> FARMABLE = identitySet(
        Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS,
        Blocks.NETHER_WART, Blocks.COCOA, Blocks.SWEET_BERRY_BUSH,
        Blocks.PUMPKIN, Blocks.MELON,
        Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.BAMBOO);

    private AutismFarmBlocks() {
    }

    public static boolean isFarmable(Block block) {
        return block != null && FARMABLE.contains(block);
    }

    public static boolean isColumnCrop(Block block) {
        return block != null && COLUMN_CROPS.contains(block);
    }

    public static boolean isHarvestOnly(Block block) {
        return block != null && HARVEST_ONLY.contains(block);
    }

    public static Set<Block> all() {
        return FARMABLE;
    }

    public static boolean baseMatches(Block crop, BlockState below) {
        if (crop == null || below == null) return false;
        if (crop == Blocks.NETHER_WART) return below.is(BlockTags.SUPPORTS_NETHER_WART);
        if (crop == Blocks.COCOA) return below.is(BlockTags.SUPPORTS_COCOA);
        if (crop == Blocks.SWEET_BERRY_BUSH) return below.is(BlockTags.SUPPORTS_VEGETATION);
        if (SEED_BY_CROP.containsKey(crop)) return below.is(BlockTags.SUPPORTS_CROPS);
        return false;
    }

    public static Item seedFor(Block crop) {
        return crop == null ? null : SEED_BY_CROP.get(crop);
    }

    private static Set<Block> identitySet(Block... blocks) {
        Set<Block> set = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(set, blocks);
        return Collections.unmodifiableSet(set);
    }
}
