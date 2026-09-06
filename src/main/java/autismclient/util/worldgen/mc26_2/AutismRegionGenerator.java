package autismclient.util.worldgen.mc26_2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class AutismRegionGenerator {

    public static final int MARGIN = 2;

    private static final int CARVER_RADIUS = 8;
    private static final EnumSet<Heightmap.Types> FINAL_HEIGHTMAPS = EnumSet.of(
        Heightmap.Types.MOTION_BLOCKING,
        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
        Heightmap.Types.OCEAN_FLOOR,
        Heightmap.Types.WORLD_SURFACE);

    private final AutismWorldgenContext context;
    private final PalettedContainerFactory containers;

    public AutismRegionGenerator(AutismWorldgenContext context) {
        this.context = context;
        this.containers = context.palettedContainerFactory();
    }

    public Map<Long, ChunkAccess> generate(int minChunkX, int minChunkZ, int size) {
        return generate(minChunkX, minChunkZ, size, () -> false);
    }

    public Map<Long, ChunkAccess> generate(int minChunkX, int minChunkZ, int size,
                                           BooleanSupplier cancelled) {
        if (size < MARGIN * 2 + 1) {
            throw new IllegalArgumentException("Region size must leave at least one reportable chunk");
        }
        if (cancelled == null) throw new IllegalArgumentException("Cancellation supplier must not be null");

        return AutismOreSimGenerationScope.call(() -> generateInScope(minChunkX, minChunkZ, size, cancelled));
    }

    private Map<Long, ChunkAccess> generateInScope(int minChunkX, int minChunkZ, int size,
                                                    BooleanSupplier cancelled) {

        Map<Long, ChunkAccess> chunks = new HashMap<>();
        for (int dx = -CARVER_RADIUS; dx < size + CARVER_RADIUS; dx++) {
            for (int dz = -CARVER_RADIUS; dz < size + CARVER_RADIUS; dz++) {
                checkCancelled(cancelled);
                ChunkPos pos = new ChunkPos(minChunkX + dx, minChunkZ + dz);
                chunks.put(pos.pack(), new ProtoChunk(pos, UpgradeData.EMPTY, context.heightAccessor(),
                    containers, null));
            }
        }

        AutismSyntheticLevel level = new AutismSyntheticLevel(context, chunks);
        StructureManager structures = new StructureManager(level, new WorldOptions(context.seed(), true, false), null);

        for (int dx = -CARVER_RADIUS; dx < size + CARVER_RADIUS; dx++) {
            for (int dz = -CARVER_RADIUS; dz < size + CARVER_RADIUS; dz++) {
                checkCancelled(cancelled);
                ChunkAccess chunk = chunks.get(ChunkPos.pack(minChunkX + dx, minChunkZ + dz));
                level.beginGeneration(chunk.getPos(), 0);
                context.generator().createStructures(
                    context.registryAccess(),
                    context.structureState(),
                    structures,
                    chunk,
                    context.structureTemplates(),
                    context.dimension());
                setStatus(chunk, ChunkStatus.STRUCTURE_STARTS);
            }
        }
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                checkCancelled(cancelled);
                ChunkAccess chunk = chunks.get(ChunkPos.pack(minChunkX + dx, minChunkZ + dz));
                level.beginGeneration(chunk.getPos(), 0);
                context.generator().createReferences(level, structures, chunk);
                setStatus(chunk, ChunkStatus.STRUCTURE_REFERENCES);
            }
        }

        for (int dx = -1; dx <= size; dx++) {
            for (int dz = -1; dz <= size; dz++) {
                checkCancelled(cancelled);
                ChunkAccess chunk = chunks.get(ChunkPos.pack(minChunkX + dx, minChunkZ + dz));
                level.beginGeneration(chunk.getPos(), 0);
                context.generator().createBiomes(context.randomState(), Blender.empty(), structures, chunk).join();
                setStatus(chunk, ChunkStatus.BIOMES);
            }
        }
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                checkCancelled(cancelled);
                ChunkAccess chunk = chunks.get(ChunkPos.pack(minChunkX + dx, minChunkZ + dz));
                level.beginGeneration(chunk.getPos(), 0);
                context.generator().fillFromNoise(Blender.empty(), context.randomState(), structures, chunk).join();
                setStatus(chunk, ChunkStatus.NOISE);
            }
        }
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                checkCancelled(cancelled);
                ChunkAccess chunk = chunks.get(ChunkPos.pack(minChunkX + dx, minChunkZ + dz));
                level.beginGeneration(chunk.getPos(), 0);
                buildSurface(level, chunks, structures, chunk);
                setStatus(chunk, ChunkStatus.SURFACE);
            }
        }
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                checkCancelled(cancelled);
                ChunkAccess chunk = chunks.get(ChunkPos.pack(minChunkX + dx, minChunkZ + dz));
                level.beginGeneration(chunk.getPos(), 0);
                applyCarvers(level, chunks, structures, chunk, cancelled);
                setStatus(chunk, ChunkStatus.CARVERS);
            }
        }

        for (int dx = 1; dx < size - 1; dx++) {
            for (int dz = 1; dz < size - 1; dz++) {
                checkCancelled(cancelled);
                ChunkAccess chunk = chunks.get(ChunkPos.pack(minChunkX + dx, minChunkZ + dz));
                Heightmap.primeHeightmaps(chunk, FINAL_HEIGHTMAPS);
                level.beginGeneration(chunk.getPos(), 1);
                context.generator().applyBiomeDecoration(level, chunk, structures);
                setStatus(chunk, ChunkStatus.FEATURES);
            }
        }

        Map<Long, ChunkAccess> result = new HashMap<>(size * size);
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                checkCancelled(cancelled);
                long key = ChunkPos.pack(minChunkX + dx, minChunkZ + dz);
                result.put(key, chunks.get(key));
            }
        }
        return result;
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("OreSim region generation superseded");
        }
    }

    private static void setStatus(ChunkAccess chunk, ChunkStatus status) {
        if (chunk instanceof ProtoChunk proto) proto.setPersistedStatus(status);
    }

    private void buildSurface(WorldGenLevel level, Map<Long, ChunkAccess> chunks,
                              StructureManager structures, ChunkAccess chunk) {
        WorldGenerationContext heightContext = new WorldGenerationContext(context.generator(), context.heightAccessor());

        Set<Holder<Biome>> biomes = new HashSet<>();
        ChunkPos pos = chunk.getPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkAccess neighbour = chunks.get(ChunkPos.pack(pos.x() + dx, pos.z() + dz));
                if (neighbour == null) continue;
                for (LevelChunkSection section : neighbour.getSections()) {
                    if (section != null) section.getBiomes().getAll(biomes::add);
                }
            }
        }
        context.noiseGenerator().buildSurface(chunk, heightContext, context.randomState(), structures,
            level.getBiomeManager(), Blender.empty(), biomes);
    }

    private void applyCarvers(AutismSyntheticLevel level, Map<Long, ChunkAccess> chunks,
                              StructureManager structures, ChunkAccess chunk, BooleanSupplier cancelled) {
        NoiseBasedChunkGenerator generator = context.noiseGenerator();
        BiomeManager biomeManager = level.getBiomeManager().withDifferentSource(
            (quartX, quartY, quartZ) -> context.biomeSource().getNoiseBiome(
                quartX, quartY, quartZ, context.randomState().sampler()));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        ChunkPos targetPos = chunk.getPos();
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(ignored -> {
            throw new IllegalStateException("Noise stage did not attach a NoiseChunk for " + targetPos);
        });
        Aquifer aquifer = noiseChunk.aquifer();
        CarvingContext carvingContext = new CarvingContext(
            generator,
            context.registryAccess(),
            chunk.getHeightAccessorForGeneration(),
            noiseChunk,
            context.randomState(),
            generator.generatorSettings().value().surfaceRule());
        CarvingMask mask = ((ProtoChunk) chunk).getOrCreateCarvingMask();

        for (int dx = -CARVER_RADIUS; dx <= CARVER_RADIUS; dx++) {
            for (int dz = -CARVER_RADIUS; dz <= CARVER_RADIUS; dz++) {
                checkCancelled(cancelled);
                ChunkPos sourcePos = new ChunkPos(targetPos.x() + dx, targetPos.z() + dz);
                ChunkAccess sourceChunk = chunks.get(sourcePos.pack());
                if (sourceChunk == null) {
                    throw new IllegalStateException("Missing carver source chunk " + sourcePos);
                }

                BiomeGenerationSettings settings = context.biomeSource().getNoiseBiome(
                    QuartPos.fromBlock(sourcePos.getMinBlockX()),
                    0,
                    QuartPos.fromBlock(sourcePos.getMinBlockZ()),
                    context.randomState().sampler()).value().getGenerationSettings();
                int index = 0;
                for (Holder<ConfiguredWorldCarver<?>> holder : settings.getCarvers()) {
                    checkCancelled(cancelled);
                    ConfiguredWorldCarver<?> carver = holder.value();
                    random.setLargeFeatureSeed(context.seed() + index, sourcePos.x(), sourcePos.z());
                    if (carver.isStartChunk(random)) {
                        carver.carve(carvingContext, chunk, biomeManager::getBiome, random,
                            aquifer, sourcePos, mask);
                    }
                    index++;
                }
            }
        }
    }

    public static void forEachBlock(ChunkAccess chunk, Predicate<BlockState> wanted, BlockSink sink) {
        forEachBlock(chunk, wanted, sink, () -> false);
    }

    public static void forEachBlock(ChunkAccess chunk, Predicate<BlockState> wanted, BlockSink sink,
                                    BooleanSupplier cancelled) {
        if (cancelled == null) throw new IllegalArgumentException("Cancellation supplier must not be null");
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int index = 0; index < sections.length; index++) {
            checkCancelled(cancelled);
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(wanted)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                checkCancelled(cancelled);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (wanted.test(state)) sink.accept(minX + x, baseY + y, minZ + z, state);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface BlockSink {
        void accept(int x, int y, int z, BlockState state);
    }

    public static long pack(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }
}
