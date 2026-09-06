package autismclient.util.worldgen.mc26_2;

import autismclient.util.oresim.AutismOreSimOre;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutismRegionGeneratorTest {

    private static final long SEED = 1234567890123456789L;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        net.minecraft.core.registries.BuiltInRegistries.ITEM
            .get(Identifier.withDefaultNamespace("trial_key"))
            .orElseThrow()
            .bindComponents(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS);
        bindVanillaBlockTags();
    }

    private static void bindVanillaBlockTags() {
        net.minecraft.server.packs.VanillaPackResources vanilla =
            net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource();
        net.minecraft.server.packs.resources.ResourceManager resources =
            new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.SERVER_DATA, java.util.List.of(vanilla));
        net.minecraft.core.RegistryAccess builtIn =
            net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, builtIn)
            .forEach(net.minecraft.core.Registry.PendingTags::apply);
    }

    private static Map<String, Integer> countOres(Map<Long, ChunkAccess> chunks, int minChunkX, int minChunkZ, int size) {
        Map<String, Integer> counts = new TreeMap<>();
        for (int dx = AutismRegionGenerator.MARGIN; dx < size - AutismRegionGenerator.MARGIN; dx++) {
            for (int dz = AutismRegionGenerator.MARGIN; dz < size - AutismRegionGenerator.MARGIN; dz++) {
                ChunkAccess chunk = chunks.get(ChunkPos.pack(minChunkX + dx, minChunkZ + dz));
                AutismRegionGenerator.forEachBlock(chunk,
                    state -> {
                        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                        return id != null && id.getPath().contains("_ore");
                    },
                    (x, y, z, state) -> {
                        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                        counts.merge(id.toString(), 1, Integer::sum);
                    });
            }
        }
        return counts;
    }

    @Test
    void oreReplaceableTagsAreBound() {
        boolean stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
            .is(net.minecraft.tags.BlockTags.STONE_ORE_REPLACEABLES);
        boolean deepslate = net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState()
            .is(net.minecraft.tags.BlockTags.DEEPSLATE_ORE_REPLACEABLES);
        System.out.println("stone in STONE_ORE_REPLACEABLES = " + stone);
        System.out.println("deepslate in DEEPSLATE_ORE_REPLACEABLES = " + deepslate);
        assertTrue(stone && deepslate, "ore replacement tags are not bound - no ore can ever generate");
    }

    @Test
    void overworldGenerationProducesOre() {
        AutismWorldgenContext context = AutismWorldgenContext.create(SEED, Level.OVERWORLD);
        assertTrue(context.vanillaBlockTagsVerified(), "worldgen block tags do not match vanilla 26.2");
        AutismRegionGenerator generator = new AutismRegionGenerator(context);

        int size = 2 * AutismRegionGenerator.MARGIN + 2;
        Map<Long, ChunkAccess> chunks = generator.generate(0, 0, size);
        Map<String, Integer> counts = countOres(chunks, 0, 0, size);

        Map<String, Integer> allBlocks = new TreeMap<>();
        for (int dx = AutismRegionGenerator.MARGIN; dx < size - AutismRegionGenerator.MARGIN; dx++) {
            for (int dz = AutismRegionGenerator.MARGIN; dz < size - AutismRegionGenerator.MARGIN; dz++) {
                ChunkAccess chunk = chunks.get(ChunkPos.pack(dx, dz));
                AutismRegionGenerator.forEachBlock(chunk, state -> !state.isAir(), (x, y, z, state) -> {
                    Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    allBlocks.merge(id.toString(), 1, Integer::sum);
                });
            }
        }
        System.out.println("Terrain block types: " + allBlocks.size() + ", total non-air: "
            + allBlocks.values().stream().mapToInt(Integer::intValue).sum());
        allBlocks.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(12)
            .forEach(e -> System.out.println("  " + e.getKey() + " = " + e.getValue()));

        System.out.println("Ore generated in " + (size - 2 * AutismRegionGenerator.MARGIN) + "^2 chunks:");
        counts.forEach((id, n) -> System.out.println("  " + id + " = " + n));

        assertFalse(counts.isEmpty(), "generation produced no ore at all");

        for (String required : new String[]{"minecraft:coal_ore", "minecraft:iron_ore", "minecraft:copper_ore"}) {
            assertTrue(counts.containsKey(required), "no " + required + " generated");
        }
    }

    @Test
    void generationIsDeterministicForASeed() {
        int size = 2 * AutismRegionGenerator.MARGIN + 1;
        Map<String, Integer> first = run(size);
        Map<String, Integer> second = run(size);

        assertTrue(first.equals(second), "two runs of the same seed disagreed:\n" + first + "\n" + second);
    }

    @Test
    void matchesVanillaWorldOreCoordinates() throws Exception {
        long fixtureSeed = 4905467586244360515L;
        AutismWorldgenContext context = AutismWorldgenContext.create(fixtureSeed, Level.OVERWORLD);
        Map<Long, ChunkAccess> generated = new AutismRegionGenerator(context).generate(-3, -3, 7);
        Map<Long, OreFixture> expected = Map.of(
            ChunkPos.pack(-1, 0), new OreFixture(394,
                "28de1c8b50ca44bdc91b33f353f986066c8dd5af29d369d19bbefecf690a4565"),
            ChunkPos.pack(0, -1), new OreFixture(337,
                "ed6bc6feb396a8ae95187ca56873c179640dc61e923936b4734d842d98edbe6e"),
            ChunkPos.pack(0, 0), new OreFixture(396,
                "495ccb1e0564acbc30b0044deb6bb80bf2d015e03e6a22c80382cc1e23f93667"),
            ChunkPos.pack(0, 1), new OreFixture(326,
                "d98e6e8587929417d6ea1cb005588df2d7bd3c8b855f6de0aa31851bad0dcf01"),
            ChunkPos.pack(1, 0), new OreFixture(352,
                "3e5276e8fffedc7dd55e3d83ff9b7e8960784b4f90c5e9593a7c3765ea667047"));

        for (Map.Entry<Long, OreFixture> fixture : expected.entrySet()) {
            ChunkAccess chunk = generated.get(fixture.getKey());
            OreFixture actual = oreFixture(chunk);
            assertEquals(fixture.getValue().count(), actual.count(),
                "vanilla fixture ore count changed in " + chunk.getPos());
            assertEquals(fixture.getValue().hash(), actual.hash(),
                "generated ore coordinates differ from the vanilla 26.2 save fixture in " + chunk.getPos());
        }
    }

    private static OreFixture oreFixture(ChunkAccess chunk) throws Exception {
        ArrayList<String> entries = new ArrayList<>();
        AutismRegionGenerator.forEachBlock(chunk, state -> AutismOreSimOre.isOreSimBlock(state.getBlock()),
            (x, y, z, state) -> entries.add(x + "," + y + "," + z + ","
                + BuiltInRegistries.BLOCK.getKey(state.getBlock())));
        entries.sort(String::compareTo);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest((String.join("\n", entries) + "\n").getBytes(StandardCharsets.UTF_8)));
        return new OreFixture(entries.size(), hash);
    }

    private record OreFixture(int count, String hash) {
    }

    @Test
    void matchesDefaultNetherOreCoordinates() throws Exception {
        long fixtureSeed = 4905467586244360515L;
        AutismWorldgenContext context = AutismWorldgenContext.create(fixtureSeed, Level.NETHER);
        Map<Long, ChunkAccess> generated = new AutismRegionGenerator(context).generate(-3, -3, 7);
        Map<Long, OreFixture> expected = Map.of(
            ChunkPos.pack(-1, 0), new OreFixture(84,
                "9c2fd4a1a2bfc8970a226df7e79cd8c6db820baf1ed0776dcb78bbbd8f8497c3"),
            ChunkPos.pack(0, -1), new OreFixture(84,
                "07d30f203104211ebd897cfdb8cdbbc2eea117e38f34e48bb1b90a97a811f750"),
            ChunkPos.pack(0, 0), new OreFixture(87,
                "178678452c3b0be9f1f5f5b7431a34d8ebe545645621a952a4562c9bd603d8a7"),
            ChunkPos.pack(0, 1), new OreFixture(185,
                "3b3a58332b7d146a0be7d7c3280b97ed77392a8a924141b080f59884ed1f1802"),
            ChunkPos.pack(1, 0), new OreFixture(121,
                "e89efb28cc182dbb6c2c3b27189aaf8362f9e6997a8d4617529cd2dc16f5d52b"));

        for (Map.Entry<Long, OreFixture> fixture : expected.entrySet()) {
            ChunkAccess chunk = generated.get(fixture.getKey());
            OreFixture actual = oreFixture(chunk);
            assertEquals(fixture.getValue().count(), actual.count(),
                "saved-world fixture ore count changed in " + chunk.getPos());
            assertEquals(fixture.getValue().hash(), actual.hash(),
                "Nether ore coordinates differ from the Minecraft 26.2 fixture in " + chunk.getPos());
        }
    }

    @Test
    void reportedMushroomLightTileHasPreFeatureLighting() {
        AutismWorldgenContext context = AutismWorldgenContext.create(4905467586244360515L, Level.OVERWORLD);

        BlockPos featurePos = new BlockPos((-20 << 4) + 8, 64, (-52 << 4) + 8);
        AutismSyntheticLevel level = syntheticLevelWithDirtFloor(context, featurePos.below());
        assertEquals(15, level.getRawBrightness(featurePos, 0));
        assertFalse(Blocks.BROWN_MUSHROOM.defaultBlockState().canSurvive(level, featurePos));
    }

    @Test
    void syntheticFeatureLightMatchesVanillaPreLightSemantics() {
        BlockPos mushroomPos = new BlockPos(0, 64, 0);

        AutismSyntheticLevel overworld = syntheticLevelWithDirtFloor(
            AutismWorldgenContext.create(SEED, Level.OVERWORLD), mushroomPos.below());
        assertEquals(15, overworld.getRawBrightness(mushroomPos, 0),
            "an uninitialized overworld sky-light engine must expose full sky light");
        assertFalse(Blocks.BROWN_MUSHROOM.defaultBlockState().canSurvive(overworld, mushroomPos),
            "ordinary dirt in full pre-light sky brightness must reject a mushroom");

        AutismSyntheticLevel nether = syntheticLevelWithDirtFloor(
            AutismWorldgenContext.create(SEED, Level.NETHER), mushroomPos.below());
        assertEquals(0, nether.getRawBrightness(mushroomPos, 0),
            "a dimension without skylight must retain zero pre-light brightness");
        assertTrue(Blocks.BROWN_MUSHROOM.defaultBlockState().canSurvive(nether, mushroomPos),
            "the same solid floor must allow a mushroom in a no-skylight dimension");
    }

    private static AutismSyntheticLevel syntheticLevelWithDirtFloor(AutismWorldgenContext context,
                                                                     BlockPos floorPos) {
        ChunkPos chunkPos = new ChunkPos(floorPos.getX() >> 4, floorPos.getZ() >> 4);
        ProtoChunk chunk = new ProtoChunk(chunkPos, UpgradeData.EMPTY, context.heightAccessor(),
            context.palettedContainerFactory(), null);
        chunk.setBlockState(floorPos, Blocks.DIRT.defaultBlockState(), 0);
        Map<Long, ChunkAccess> chunks = new HashMap<>();
        chunks.put(chunkPos.pack(), chunk);
        return new AutismSyntheticLevel(context, chunks);
    }

    private static Map<String, Integer> run(int size) {
        AutismWorldgenContext context = AutismWorldgenContext.create(SEED, Level.OVERWORLD);
        Map<Long, ChunkAccess> chunks = new AutismRegionGenerator(context).generate(-3, 5, size);
        return new HashMap<>(countOres(chunks, -3, 5, size));
    }
}
