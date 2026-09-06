package autismclient.util.worldgen.mc26_2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.function.Supplier;

public final class AutismSyntheticLevel implements WorldGenLevel {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final FluidState EMPTY_FLUID = Blocks.AIR.defaultBlockState().getFluidState();

    private final AutismWorldgenContext context;
    private final Map<Long, ChunkAccess> chunks;
    private final BiomeManager biomeManager;
    private final LevelLightEngine lightEngine;
    private RandomSource random;
    private long subTickCount;
    private int centerChunkX;
    private int centerChunkZ;
    private int writeRadius;

    private final net.minecraft.world.ticks.WorldGenTickAccess<net.minecraft.world.level.block.Block> blockTicks =
        new net.minecraft.world.ticks.WorldGenTickAccess<>(pos -> chunkFor(pos).getBlockTicks());
    private final net.minecraft.world.ticks.WorldGenTickAccess<net.minecraft.world.level.material.Fluid> fluidTicks =
        new net.minecraft.world.ticks.WorldGenTickAccess<>(pos -> chunkFor(pos).getFluidTicks());

    public AutismSyntheticLevel(AutismWorldgenContext context, Map<Long, ChunkAccess> chunks) {
        this.context = context;
        this.chunks = chunks;

        this.biomeManager = new BiomeManager(this, BiomeManager.obfuscateSeed(context.seed()));

        this.lightEngine = new LevelLightEngine(new LightChunkGetter() {
            @Override
            public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
                return chunkAt(chunkX, chunkZ);
            }

            @Override
            public BlockGetter getLevel() {
                return AutismSyntheticLevel.this;
            }
        }, true, context.dimensionType().hasSkyLight());
        beginGeneration(new ChunkPos(0, 0), 0);
    }

    public void beginGeneration(ChunkPos center, int writeRadius) {
        this.random = context.randomState()
            .getOrCreateRandomFactory(Identifier.withDefaultNamespace("worldgen_region_random"))
            .at(center.getWorldPosition());
        this.subTickCount = 0L;
        this.centerChunkX = center.x();
        this.centerChunkZ = center.z();
        this.writeRadius = Math.max(0, writeRadius);
    }

    private ChunkAccess chunkAt(int chunkX, int chunkZ) {
        return chunks.get(ChunkPos.pack(chunkX, chunkZ));
    }

    private ChunkAccess chunkFor(BlockPos pos) {
        ChunkAccess chunk = chunkAt(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) throw new IllegalStateException("Generation touched unprepared chunk at " + pos);
        return chunk;
    }

    StructureTemplateManager structureTemplates() {
        return context.structureTemplates();
    }

    net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator() {
        return context.generator();
    }

    @Override
    public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks() {
        return blockTicks;
    }

    @Override
    public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks() {
        return fluidTicks;
    }

    @Override
    public long getSeed() {
        return context.seed();
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean require) {
        ChunkAccess chunk = chunkAt(chunkX, chunkZ);
        if (chunk == null && require) {
            throw new IllegalStateException("Generation asked for unprepared chunk " + chunkX + ", " + chunkZ);
        }
        return chunk;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasChunk(int chunkX, int chunkZ) {
        return chunkAt(chunkX, chunkZ) != null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (isOutsideBuildHeight(pos)) return AIR;
        ChunkAccess chunk = chunkAt(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? AIR : chunk.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (isOutsideBuildHeight(pos)) return EMPTY_FLUID;
        ChunkAccess chunk = chunkAt(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? EMPTY_FLUID : chunk.getFluidState(pos);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        if (isOutsideBuildHeight(pos) || !ensureCanWrite(pos)) return false;
        ChunkAccess chunk = chunkAt(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        BlockState oldState = chunk.setBlockState(pos, state, flags);
        if (state.hasBlockEntity()) {
            if (chunk.getPersistedStatus().getChunkType() == ChunkType.LEVELCHUNK) {
                BlockEntity blockEntity = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
                if (blockEntity != null) chunk.setBlockEntity(blockEntity);
                else chunk.removeBlockEntity(pos);
            } else {
                CompoundTag tag = new CompoundTag();
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
                tag.putString("id", "DUMMY");
                chunk.setBlockEntityNbt(tag);
            }
        } else if (oldState != null && oldState.hasBlockEntity()) {
            chunk.removeBlockEntity(pos);
        }
        if ((flags & 16) == 0) {
            BlockPos postProcess = state.getPostProcessPos(this, pos);
            if (postProcess != null) chunkFor(postProcess).markPosForPostProcessing(postProcess);
        }
        return true;
    }

    @Override
    public boolean ensureCanWrite(BlockPos pos) {
        int chunkX = net.minecraft.core.SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = net.minecraft.core.SectionPos.blockToSectionCoord(pos.getZ());
        return Math.abs(centerChunkX - chunkX) <= writeRadius
            && Math.abs(centerChunkZ - chunkZ) <= writeRadius;
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        ChunkAccess chunk = chunkAt(x >> 4, z >> 4);

        return chunk == null ? getMinY() : chunk.getHeight(type, x & 15, z & 15) + 1;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
        return context.biomeSource().getNoiseBiome(quartX, quartY, quartZ, context.randomState().sampler());
    }

    @Override
    public BiomeManager getBiomeManager() {
        return biomeManager;
    }

    @Override
    public RegistryAccess registryAccess() {
        return context.registryAccess();
    }

    @Override
    public DimensionType dimensionType() {
        return context.dimensionType();
    }

    @Override
    public int getMinY() {
        return context.heightAccessor().getMinY();
    }

    @Override
    public int getHeight() {
        return context.heightAccessor().getHeight();
    }

    @Override
    public int getSeaLevel() {
        return context.generator().getSeaLevel();
    }

    @Override
    public void setCurrentlyGenerating(Supplier<String> currentlyGenerating) {

    }

    @Override
    public long getGameTime() {
        return 0L;
    }

    @Override
    public net.minecraft.server.level.ServerLevel getLevel() {
        throw new UnsupportedOperationException("Synthetic worldgen level has no server level");
    }

    @Override
    public MinecraftServer getServer() {
        return null;
    }

    @Override
    public net.minecraft.world.DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        throw new UnsupportedOperationException("Synthetic worldgen level has no difficulty");
    }

    @Override
    public net.minecraft.world.level.border.WorldBorder getWorldBorder() {

        return new net.minecraft.world.level.border.WorldBorder();
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, java.util.function.Predicate<FluidState> predicate) {
        return predicate.test(getFluidState(pos));
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, java.util.function.Predicate<BlockState> predicate) {
        return predicate.test(getBlockState(pos));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> java.util.Optional<T> getBlockEntity(BlockPos pos,
                                                                        net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        BlockEntity blockEntity = getBlockEntity(pos);
        return blockEntity != null && blockEntity.getType() == type
            ? java.util.Optional.of((T) blockEntity)
            : java.util.Optional.empty();
    }

    @Override
    public BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) {
        return new BlockPos(pos.getX(), getHeight(type, pos.getX(), pos.getZ()), pos.getZ());
    }

    @Override
    public java.util.List<net.minecraft.world.entity.player.Player> players() {
        return java.util.List.of();
    }

    @Override
    public java.util.List<Entity> getEntities(Entity except, net.minecraft.world.phys.AABB area,
                                              java.util.function.Predicate<? super Entity> filter) {
        return java.util.List.of();
    }

    @Override
    public <T extends Entity> java.util.List<T> getEntities(net.minecraft.world.level.entity.EntityTypeTest<Entity, T> test,
                                                            net.minecraft.world.phys.AABB area,
                                                            java.util.function.Predicate<? super T> filter) {
        return java.util.List.of();
    }

    @Override
    public net.minecraft.world.level.BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return chunkAt(chunkX, chunkZ);
    }

    @Override
    public java.util.List<net.minecraft.world.phys.shapes.VoxelShape> getEntityCollisions(Entity entity,
                                                                                          net.minecraft.world.phys.AABB area) {
        return java.util.List.of();
    }

    @Override
    public ChunkSource getChunkSource() {
        throw new UnsupportedOperationException("Synthetic worldgen level has no chunk source");
    }

    @Override
    public LevelData getLevelData() {
        throw new UnsupportedOperationException("Synthetic worldgen level has no level data");
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return lightEngine;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        ChunkAccess chunk = chunkFor(pos);
        BlockEntity blockEntity = chunk.getBlockEntity(pos);
        if (blockEntity != null) return blockEntity;

        CompoundTag tag = chunk.getBlockEntityNbt(pos);
        BlockState state = chunk.getBlockState(pos);
        if (tag == null) return null;
        if ("DUMMY".equals(tag.getStringOr("id", ""))) {
            if (!state.hasBlockEntity()) return null;
            blockEntity = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
        } else {
            blockEntity = BlockEntity.loadStatic(pos, state, tag, registryAccess());
        }
        if (blockEntity != null) chunk.setBlockEntity(blockEntity);
        return blockEntity;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean move) {
        return setBlock(pos, AIR, 3, 512);
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean drop, Entity breaker, int recursionLeft) {
        return setBlock(pos, AIR, 3, recursionLeft);
    }

    @Override
    public RandomSource getRandom() {
        return random;
    }

    @Override
    public long nextSubTickCount() {
        return subTickCount++;
    }

    @Override
    public int getSkyDarken() {
        return 0;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return FeatureFlags.DEFAULT_FLAGS;
    }

    @Override
    public EnvironmentAttributeReader environmentAttributes() {
        throw new UnsupportedOperationException("Synthetic worldgen level has no environment attributes");
    }

    @Override
    public void playSound(Entity source, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {
    }

    @Override
    public void addParticle(ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
    }

    @Override
    public void levelEvent(Entity source, int type, BlockPos pos, int data) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
    }
}
