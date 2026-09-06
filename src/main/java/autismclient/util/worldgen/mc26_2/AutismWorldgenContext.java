package autismclient.util.worldgen.mc26_2;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.IdMap;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

public final class AutismWorldgenContext {

    private static final Object LOOKUP_LOCK = new Object();
    private static volatile VanillaBootstrap cachedBootstrap;

    private final long seed;
    private final ResourceKey<Level> dimension;
    private final HolderLookup.Provider lookup;
    private final RegistryAccess registryAccess;
    private final ChunkGenerator generator;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState structureState;
    private final StructureTemplateManager structureTemplates;
    private final DimensionType dimensionType;
    private final LevelHeightAccessor heightAccessor;

    private AutismWorldgenContext(long seed, ResourceKey<Level> dimension, VanillaBootstrap bootstrap,
                                  ChunkGenerator generator, RandomState randomState, DimensionType dimensionType) {
        this.seed = seed;
        this.dimension = dimension;
        this.lookup = bootstrap.registries();
        this.registryAccess = bootstrap.registries();
        this.generator = generator;
        this.randomState = randomState;
        this.structureState = generator.createState(
            lookup.lookupOrThrow(Registries.STRUCTURE_SET), randomState, seed);
        this.structureState.ensureStructuresGenerated();
        this.structureTemplates = bootstrap.structureTemplates();
        this.dimensionType = dimensionType;
        this.heightAccessor = LevelHeightAccessor.create(dimensionType.minY(), dimensionType.height());
    }

    public static AutismWorldgenContext create(long seed, ResourceKey<Level> dimension) {
        VanillaBootstrap bootstrap = sharedBootstrap();
        HolderLookup.Provider lookup = bootstrap.registries();
        ResourceKey<LevelStem> stemKey = stemKeyFor(dimension);
        WorldPreset preset = lookup.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value();
        LevelStem stem = preset.createWorldDimensions().dimensions().get(stemKey);
        if (stem == null) throw new IllegalStateException("No level stem for " + dimension);

        ChunkGenerator generator = stem.generator();
        DimensionType dimensionType = stem.type().value();
        RandomState randomState = randomStateFor(generator, lookup, seed);
        return new AutismWorldgenContext(seed, dimension, bootstrap, generator, randomState, dimensionType);
    }

    private static RandomState randomStateFor(ChunkGenerator generator, HolderLookup.Provider lookup, long seed) {
        if (generator instanceof NoiseBasedChunkGenerator noise) {
            NoiseGeneratorSettings settings = noise.generatorSettings().value();
            return RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed);
        }

        throw new IllegalStateException("Unsupported generator: " + generator.getClass().getName());
    }

    private static ResourceKey<LevelStem> stemKeyFor(ResourceKey<Level> dimension) {
        if (Level.OVERWORLD.equals(dimension)) return LevelStem.OVERWORLD;
        if (Level.NETHER.equals(dimension)) return LevelStem.NETHER;
        if (Level.END.equals(dimension)) return LevelStem.END;
        throw new IllegalArgumentException("Unsupported vanilla dimension: " + dimension.identifier());
    }

    private static VanillaBootstrap sharedBootstrap() {
        VanillaBootstrap bootstrap = cachedBootstrap;
        if (bootstrap != null) return bootstrap;
        synchronized (LOOKUP_LOCK) {
            if (cachedBootstrap == null) cachedBootstrap = loadVanillaBootstrap();
            return cachedBootstrap;
        }
    }

    private static VanillaBootstrap loadVanillaBootstrap() {
        VanillaPackResources vanilla = ServerPacksSource.createVanillaPackSource();
        CloseableResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla));
        Executor direct = Runnable::run;

        LayeredRegistryAccess<RegistryLayer> initial = RegistryLayer.createRegistryAccess();
        List<Registry.PendingTags<?>> staticTags = TagLoader.loadTagsForExistingRegistries(
            resources, initial.getLayer(RegistryLayer.STATIC));
        Map<Identifier, List<Identifier>> vanillaBlockTags = ensureVanillaBlockTags(staticTags);

        List<HolderLookup.RegistryLookup<?>> worldgenContext = TagLoader.buildUpdatedLookups(
            initial.getAccessForLoading(RegistryLayer.WORLDGEN), staticTags);
        RegistryAccess.Frozen worldgen = RegistryDataLoader.load(
            resources, worldgenContext, RegistryDataLoader.WORLDGEN_REGISTRIES, direct).join();
        List<HolderLookup.RegistryLookup<?>> dimensionContext = Stream.concat(
            worldgenContext.stream(), worldgen.listRegistries()).toList();
        RegistryAccess.Frozen dimensions = RegistryDataLoader.load(
            resources, dimensionContext, RegistryDataLoader.DIMENSION_REGISTRIES, direct).join();
        RegistryAccess.Frozen registries = initial
            .replaceFrom(RegistryLayer.WORLDGEN, worldgen, dimensions)
            .compositeAccess();

        try {
            Path scratch = Files.createTempDirectory("autismclient-oresim-26_2-");
            scratch.toFile().deleteOnExit();
            LevelStorageSource storageSource = LevelStorageSource.createDefault(scratch);
            LevelStorageSource.LevelStorageAccess storage = storageSource.createAccess("templates");
            storage.getLevelPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile().deleteOnExit();
            StructureTemplateManager templates = new StructureTemplateManager(
                resources,
                storage,
                DataFixers.getDataFixer(),
                registries.lookupOrThrow(Registries.BLOCK).filterFeatures(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
            return new VanillaBootstrap(registries, resources, storage, templates, vanillaBlockTags);
        } catch (IOException error) {
            resources.close();
            throw new IllegalStateException("Could not create OreSim's temporary structure-template context", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Identifier, List<Identifier>> ensureVanillaBlockTags(
        List<Registry.PendingTags<?>> pendingTags
    ) {
        Registry.PendingTags<Block> vanillaBlocks = (Registry.PendingTags<Block>) pendingTags.stream()
            .filter(tags -> tags.key().equals(Registries.BLOCK))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Vanilla datapack supplied no block tags"));
        Map<Identifier, List<Identifier>> expected = blockTagContents(vanillaBlocks.lookup());
        Map<Identifier, List<Identifier>> current = blockTagContents(
            net.minecraft.core.registries.BuiltInRegistries.BLOCK);
        if (current.isEmpty()) {
            pendingTags.forEach(Registry.PendingTags::apply);
            current = blockTagContents(net.minecraft.core.registries.BuiltInRegistries.BLOCK);
        }
        return Map.copyOf(expected);
    }

    private static Map<Identifier, List<Identifier>> blockTagContents(HolderLookup.RegistryLookup<Block> lookup) {
        Map<Identifier, List<Identifier>> result = new TreeMap<>();
        try {
            lookup.listTags().forEach(tag -> {
                if (!"minecraft".equals(tag.key().location().getNamespace())) return;
                List<Identifier> blocks = tag.stream()
                    .map(holder -> holder.unwrapKey().orElseThrow().identifier())
                    .sorted()
                    .toList();
                result.put(tag.key().location(), blocks);
            });
        } catch (IllegalStateException ignored) {

        }
        return result;
    }

    private record VanillaBootstrap(RegistryAccess.Frozen registries,
                                    CloseableResourceManager resources,
                                    LevelStorageSource.LevelStorageAccess storage,
                                    StructureTemplateManager structureTemplates,
                                    Map<Identifier, List<Identifier>> vanillaBlockTags) {
    }

    public long seed() {
        return seed;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public HolderLookup.Provider lookup() {
        return lookup;
    }

    public RegistryAccess registryAccess() {
        return registryAccess;
    }

    public ChunkGenerator generator() {
        return generator;
    }

    public NoiseBasedChunkGenerator noiseGenerator() {
        return (NoiseBasedChunkGenerator) generator;
    }

    public BiomeSource biomeSource() {
        return generator.getBiomeSource();
    }

    public RandomState randomState() {
        return randomState;
    }

    public ChunkGeneratorStructureState structureState() {
        return structureState;
    }

    public StructureTemplateManager structureTemplates() {
        return structureTemplates;
    }

    public boolean vanillaBlockTagsVerified() {
        Map<Identifier, List<Identifier>> current = blockTagContents(
            net.minecraft.core.registries.BuiltInRegistries.BLOCK);
        for (Map.Entry<Identifier, List<Identifier>> entry : cachedBootstrap.vanillaBlockTags().entrySet()) {
            if (!entry.getValue().equals(current.get(entry.getKey()))) return false;
        }
        return true;
    }

    public DimensionType dimensionType() {
        return dimensionType;
    }

    public LevelHeightAccessor heightAccessor() {
        return heightAccessor;
    }

    public PalettedContainerFactory palettedContainerFactory() {
        List<Holder.Reference<Biome>> biomes = lookup.lookupOrThrow(Registries.BIOME).listElements().toList();
        Map<Holder<Biome>, Integer> ids = new IdentityHashMap<>();
        for (int i = 0; i < biomes.size(); i++) ids.put(biomes.get(i), i);
        IdMap<Holder<Biome>> biomeIds = new IdMap<>() {
            @Override
            public int getId(Holder<Biome> value) {
                Integer id = ids.get(value);
                return id == null ? -1 : id;
            }

            @Override
            public Holder<Biome> byId(int id) {
                return id >= 0 && id < biomes.size() ? biomes.get(id) : null;
            }

            @Override
            public int size() {
                return biomes.size();
            }

            @Override
            public Iterator<Holder<Biome>> iterator() {
                return biomes.stream().map(reference -> (Holder<Biome>) reference).iterator();
            }
        };

        Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        BlockState defaultBlock = Blocks.AIR.defaultBlockState();
        Holder<Biome> defaultBiome = lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        return new PalettedContainerFactory(
            blockStrategy, defaultBlock, null,
            Strategy.createForBiomes(biomeIds), defaultBiome, null);
    }

}
