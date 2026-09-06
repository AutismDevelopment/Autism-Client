package autismclient.util.oresim;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AutismOreSimOre {

    public enum Kind {
        COAL("coal", "Coal", 0xFF4E4E58, "coal"),
        IRON("iron", "Iron", 0xFFD8A57A, "iron"),
        GOLD("gold", "Gold", 0xFFFCE04B, "gold"),
        REDSTONE("redstone", "Redstone", 0xFFFF3B3B, "redstone"),
        DIAMOND("diamond", "Diamond", 0xFF4AE3D9, "diamond"),
        LAPIS("lapis", "Lapis", 0xFF3A62E0, "lapis"),
        COPPER("copper", "Copper", 0xFFE07B4A, "copper"),
        EMERALD("emerald", "Emerald", 0xFF31D96B, "emerald"),
        QUARTZ("quartz", "Quartz", 0xFFEDE2DA, "quartz"),
        DEBRIS("debris", "Ancient Debris", 0xFFB86FD9, "ancient_debris");

        public final String id;
        public final String label;
        public final int defaultColor;

        public final String match;

        Kind(String id, String label, int defaultColor, String match) {
            this.id = id;
            this.label = label;
            this.defaultColor = defaultColor;
            this.match = match;
        }

        public String colorId() {
            return "ore-color-" + id;
        }
    }

    public static final List<String> ORE_SIM_BLOCK_IDS = List.of(
        "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
        "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
        "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
        "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
        "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
        "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
        "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
        "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
        "minecraft:raw_iron_block", "minecraft:raw_copper_block",
        "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore",
        "minecraft:ancient_debris");

    private static volatile Set<Block> oreSimBlocks;

    private AutismOreSimOre() {
    }

    public static boolean isOreSimBlock(Block block) {
        if (block == null) return false;
        Set<Block> known = oreSimBlocks;
        if (known == null) {
            Set<Block> built = Collections.newSetFromMap(new IdentityHashMap<>());
            for (String id : ORE_SIM_BLOCK_IDS) {
                Identifier parsed = Identifier.tryParse(id);
                if (parsed != null) BuiltInRegistries.BLOCK.getOptional(parsed).ifPresent(built::add);
            }
            known = built;
            oreSimBlocks = known;
        }
        return known.contains(block);
    }

    public static final class OreStates {
        private static final Map<BlockState, Integer> IDS = new IdentityHashMap<>();
        private static volatile BlockState[] states = new BlockState[0];
        private static volatile Kind[] kinds = new Kind[0];

        private OreStates() {
        }

        private static synchronized int intern(BlockState state, Kind kind) {
            Integer existing = IDS.get(state);
            if (existing != null) return existing;
            int id = states.length;
            BlockState[] nextStates = Arrays.copyOf(states, id + 1);
            Kind[] nextKinds = Arrays.copyOf(kinds, id + 1);
            nextStates[id] = state;
            nextKinds[id] = kind;
            IDS.put(state, id);
            kinds = nextKinds;
            states = nextStates;
            return id;
        }

        public static BlockState state(int id) {
            BlockState[] snapshot = states;
            return id >= 0 && id < snapshot.length ? snapshot[id] : null;
        }

        public static Kind kind(int id) {
            Kind[] snapshot = kinds;
            return id >= 0 && id < snapshot.length ? snapshot[id] : null;
        }

        public static int count() {
            return states.length;
        }

        public static int internGenerated(BlockState state) {
            if (state == null || !AutismOreSimOre.isOreSimBlock(state.getBlock())) return -1;
            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            Kind kind = id == null ? null : AutismOreSimOre.familyOf(id.toString());
            return kind == null ? -1 : intern(state, kind);
        }
    }

    public static Kind familyOf(String blockId) {
        if (blockId == null) return null;
        String path = blockId.strip().toLowerCase(Locale.ROOT);
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        for (Kind kind : Kind.values()) {
            if (path.contains(kind.match)) return kind;
        }
        return null;
    }
}

